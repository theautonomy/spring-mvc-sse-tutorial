package com.example.sse.step8;

import com.example.sse.step8.JobRunner.FailureMode;
import com.example.sse.step8.JobStore.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs real jobs against the in-memory database and checks what they leave behind. */
@SpringBootTest
class JobRunnerTests {

    private static final int TOTAL = JobRunner.BATCHES * JobRunner.ROWS_PER_BATCH;

    @Autowired
    JobRunner runner;

    @Autowired
    JobStore store;

    @Test
    void normalJobInsertsAndProcessesEverything() throws Exception {
        var job = finish(runner.start("alice", FailureMode.NONE));

        assertThat(job.status()).isEqualTo(Status.DONE);
        assertThat(job.processed()).isEqualTo(TOTAL);
        assertThat(job.batches()).allSatisfy(b -> assertThat(b.status()).isEqualTo("DONE"));
        var summary = store.summary(job.id());
        assertThat(summary.records()).isEqualTo(TOTAL);
        assertThat(summary.processed()).isEqualTo(TOTAL);
    }

    @Test
    void failedAttemptIsRolledBackAndRetried() throws Exception {
        var job = finish(runner.start("alice", FailureMode.FAIL_ONCE));

        assertThat(job.status()).isEqualTo(Status.DONE);
        assertThat(job.batches().get(2).attempts()).isEqualTo(2);
        // The rolled-back first attempt left nothing behind: exactly one set of rows
        assertThat(store.summary(job.id()).records()).isEqualTo(TOTAL);
    }

    @Test
    void failedBatchStopsTheJobAndDeletesItsRecords() throws Exception {
        var job = finish(runner.start("alice", FailureMode.FAIL_ALWAYS));

        assertThat(job.status()).isEqualTo(Status.FAILED);
        assertThat(job.message()).startsWith("Batch 3 failed 2 times").contains("records were deleted");
        assertThat(job.batches().get(2).status()).isEqualTo("FAILED");
        assertThat(job.processed()).isZero();
        assertThat(store.summary(job.id()).records()).isZero();
    }

    @Test
    void cancelStopsTheJobAndDeletesItsRecords() throws Exception {
        var started = runner.start("alice", FailureMode.NONE);
        // Wait for some progress. (The rows themselves stay invisible until a batch's transaction commits.)
        waitUntil(() -> store.find(started.jobId(), "alice").orElseThrow().batches().stream().anyMatch(b -> b.inserted() > 0));

        assertThat(runner.cancel(started.jobId(), "alice")).isTrue();
        var job = finish(started);

        assertThat(job.status()).isEqualTo(Status.CANCELLED);
        assertThat(job.message()).startsWith("Cancelled by alice");
        assertThat(store.summary(job.id()).records()).isZero();
        assertThat(runner.cancel(job.id(), "alice")).as("a finished job can't be cancelled").isFalse();
    }

    @Test
    void jobsBelongToTheirOwner() throws Exception {
        var started = runner.start("alice", FailureMode.NONE);

        assertThat(store.find(started.jobId(), "bob")).isEmpty();
        assertThat(runner.cancel(started.jobId(), "bob")).isFalse();
        assertThat(finish(started).status()).isEqualTo(Status.DONE);
    }

    @Test
    void neverMoreThanThreeBatchesAtOnceAcrossJobs() throws Exception {
        var first = runner.start("alice", FailureMode.NONE);
        var second = runner.start("bob", FailureMode.NONE);
        int maxRunning = 0;
        while (!(first.finished().isDone() && second.finished().isDone())) {
            int running = running(first.jobId(), "alice") + running(second.jobId(), "bob");
            maxRunning = Math.max(maxRunning, running);
            Thread.sleep(20);
        }
        assertThat(maxRunning).isBetween(1, runner.maxParallelBatches());
    }

    private int running(String jobId, String owner) {
        return (int) store.find(jobId, owner).orElseThrow().batches().stream()
                .filter(b -> b.status().equals("RUNNING") || b.status().equals("RETRYING"))
                .count();
    }

    private JobStore.Job finish(JobRunner.Started started) throws Exception {
        started.finished().get(60, TimeUnit.SECONDS);
        var owner = store.find(started.jobId(), "alice").isPresent() ? "alice" : "bob";
        return store.find(started.jobId(), owner).orElseThrow();
    }

    static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("condition reached in time").isTrue();
    }
}
