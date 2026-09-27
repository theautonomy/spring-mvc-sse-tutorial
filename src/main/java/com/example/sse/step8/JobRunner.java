package com.example.sse.step8;

import com.example.sse.step8.JobStore.Status;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Step 8: step 7's pipeline (insert in parallel batches, then process), made fit for real use. The job's state is
 * in the database, batches are transactional and retried, the database gets a limited number of batches at a time,
 * and a job can be cancelled or time out. Nothing here knows about SSE: the stream just reads the job's state.
 */
@Service
class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    static final int BATCHES = 6;
    static final int ROWS_PER_BATCH = 100;
    private static final int INSERT_CHUNK = 25;
    private static final int PROCESS_CHUNK = 50;
    static final int MAX_ATTEMPTS = 2;
    private static final int FAILING_BATCH = 2; // "batch 3" on the page

    enum FailureMode {
        NONE, FAIL_ONCE, FAIL_ALWAYS, SLOW
    }

    record Started(String jobId, CompletableFuture<Void> finished) {}

    /** Thrown inside a job's threads when someone asked the job to stop: cancel, timeout or a failed batch. */
    static class JobStopped extends RuntimeException {
        JobStopped(String reason) {
            super("Stopped: " + reason);
        }
    }

    private final JobStore store;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private final Duration timeout;
    /** Across all jobs. Each running batch holds a database connection, and the pool has only 10. */
    private final int maxParallelBatches;
    private final Semaphore databaseSlots;
    // Virtual threads with names, so the log shows which thread did what
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("step8-", 0).factory());

    JobRunner(JobStore store, PlatformTransactionManager transactionManager,
              @Value("${tutorial.step8.job-timeout:20s}") Duration timeout,
              @Value("${tutorial.step8.max-parallel-batches:3}") int maxParallelBatches) {
        this.store = store;
        this.tx = new TransactionTemplate(transactionManager);
        // For progress updates made while a batch's own transaction is still open: they must commit right away,
        // or nobody else could see them
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.timeout = timeout;
        this.maxParallelBatches = maxParallelBatches;
        this.databaseSlots = new Semaphore(maxParallelBatches, true);
    }

    int maxParallelBatches() {
        return maxParallelBatches;
    }

    Started start(String owner, FailureMode mode) {
        var jobId = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> store.create(jobId, owner, mode.name(), BATCHES, ROWS_PER_BATCH));
        var deadline = Instant.now().plus(timeout);

        var batches = IntStream.range(0, BATCHES)
                .mapToObj(batch -> CompletableFuture.runAsync(() -> insertBatch(jobId, batch, mode, deadline), executor))
                .toArray(CompletableFuture[]::new);

        // Same shape as step 7. handle() runs finish() for every outcome and completes normally afterwards.
        CompletableFuture<Void> finished = CompletableFuture.allOf(batches)
                .thenRunAsync(() -> process(jobId, deadline), executor)
                .handle((ignored, error) -> {
                    finish(jobId, error);
                    return null;
                });
        log.info("Step 8: job {} started by {} ({})", jobId, owner, mode);
        return new Started(jobId, finished);
    }

    /** @return {@code false} if the job doesn't exist for this user or has already finished */
    boolean cancel(String jobId, String owner) {
        var job = store.find(jobId, owner);
        return job.isPresent() && !job.get().status().finished()
                && store.requestStop(jobId, "CANCEL", "Cancelled by " + owner + ".");
    }

    // --- action 1: insert -----------------------------------------------------------------------------------------

    private void insertBatch(String jobId, int batch, FailureMode mode, Instant deadline) {
        try {
            acquire(databaseSlots); // wait here while too many batches (of any job) are running
            try {
                for (int attempt = 1; ; attempt++) {
                    checkStop(jobId, deadline);
                    store.startAttempt(jobId, batch);
                    try {
                        int a = attempt;
                        // One transaction per batch: it's inserted completely or not at all, so a retry can simply
                        // start over, and a failed attempt leaves no half batch behind
                        tx.executeWithoutResult(status -> insertRows(jobId, batch, a, mode, deadline));
                        store.setBatch(jobId, batch, "DONE");
                        return;
                    } catch (JobStopped e) {
                        throw e;
                    } catch (RuntimeException e) {
                        if (attempt == MAX_ATTEMPTS) {
                            store.setBatch(jobId, batch, "FAILED");
                            store.requestStop(jobId, "FAILED", "Batch %d failed %d times: %s."
                                    .formatted(batch + 1, attempt, e.getMessage()));
                            throw e;
                        }
                        log.warn("Step 8: job {} batch {} attempt {} failed, retrying: {}", jobId, batch + 1, attempt, e.getMessage());
                        store.setBatch(jobId, batch, "RETRYING");
                        pause(500);
                    }
                }
            } finally {
                databaseSlots.release();
            }
        } catch (JobStopped e) {
            store.setBatch(jobId, batch, "STOPPED");
            throw e;
        }
    }

    /** Runs inside the batch's transaction. */
    private void insertRows(String jobId, int batch, int attempt, FailureMode mode, Instant deadline) {
        var random = ThreadLocalRandom.current();
        for (int rows = 0; rows < ROWS_PER_BATCH; rows += INSERT_CHUNK) {
            checkStop(jobId, deadline); // throwing here rolls back this batch's rows
            pause(mode == FailureMode.SLOW ? random.nextInt(2500, 3500) : random.nextInt(150, 450));
            boolean fails = mode == FailureMode.FAIL_ALWAYS || (mode == FailureMode.FAIL_ONCE && attempt == 1);
            if (fails && batch == FAILING_BATCH && rows == 50) {
                throw new IllegalStateException("lost the database connection (simulated)");
            }
            store.insertRecords(jobId, batch, random.ints(INSERT_CHUNK, 1, 100).boxed().toList());
            int inserted = rows + INSERT_CHUNK;
            newTx.executeWithoutResult(status -> store.setInserted(jobId, batch, inserted));
        }
    }

    // --- action 2: process ----------------------------------------------------------------------------------------

    private void process(String jobId, Instant deadline) {
        store.setStatus(jobId, Status.PROCESSING);
        boolean more = true;
        while (more) {
            checkStop(jobId, deadline);
            pause(100);
            // Each chunk is a transaction: the records and the job's "processed" counter change together
            more = Boolean.TRUE.equals(tx.execute(status -> {
                var ids = store.findNew(jobId, PROCESS_CHUNK);
                if (ids.isEmpty()) {
                    return false;
                }
                store.process(jobId, ids);
                return true;
            }));
        }
    }

    // --- the end ------------------------------------------------------------------------------------------------

    /** Runs once, after every thread of the job has stopped. Only then is it safe to clean up. */
    private void finish(String jobId, Throwable error) {
        try {
            if (error == null) {
                store.finish(jobId, Status.DONE, null);
                log.info("Step 8: job {} done", jobId);
                return;
            }
            var cause = error instanceof CompletionException ? error.getCause() : error;
            var stop = store.stop(jobId);
            // All or nothing: a job that didn't finish leaves no records behind
            int deleted = store.deleteRecords(jobId);
            var cleanup = " Its %d inserted records were deleted.".formatted(deleted);
            if (stop.isPresent() && stop.get().reason().equals("CANCEL")) {
                store.finish(jobId, Status.CANCELLED, stop.get().message() + cleanup);
            } else if (stop.isPresent()) {
                store.finish(jobId, Status.FAILED, stop.get().message() + cleanup);
            } else {
                store.finish(jobId, Status.FAILED, cause.getMessage() + "." + cleanup);
            }
            log.warn("Step 8: job {} ended: {}", jobId, cause.getMessage());
        } catch (RuntimeException e) {
            log.error("Step 8: could not finish job {}", jobId, e);
        }
    }

    /**
     * Stopping is cooperative: every thread checks between chunks. CompletableFuture.cancel() or orTimeout() would
     * only mark the future as done, while the threads would keep inserting.
     */
    private void checkStop(String jobId, Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            // In its own transaction: this often runs inside a batch's transaction, which is about to roll back
            // because of the stop. Inside it, the stop request would be rolled back too.
            newTx.executeWithoutResult(status -> store.requestStop(jobId, "TIMEOUT",
                    "Timed out after %d s.".formatted(timeout.toSeconds())));
        }
        store.stop(jobId).ifPresent(stop -> {
            throw new JobStopped(stop.reason());
        });
    }

    /**
     * With a database that survives restarts, a job that was running when the server went down would stay
     * "running" forever. Nobody is working on it any more, so mark it failed.
     */
    @EventListener(ApplicationReadyEvent.class)
    void failJobsLeftByAPreviousRun() {
        int jobs = store.failUnfinished("The server restarted while this job was running. Its records were deleted.");
        if (jobs > 0) {
            log.warn("Step 8: marked {} unfinished jobs from a previous run as failed", jobs);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }
}
