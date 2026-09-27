package com.example.sse.step7;

import com.example.sse.FragmentRenderer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;

/**
 * Step 7: one request, two actions that must run in order. Action 1 inserts records into the database, in several
 * batches on their own threads. Action 2 processes the records, and may only start once every batch is in.
 * {@link CompletableFuture} expresses that order; SSE reports the progress of both actions.
 */
@Controller
class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    static final int BATCHES = 4;
    static final int ROWS_PER_BATCH = 50;
    static final int TOTAL_ROWS = BATCHES * ROWS_PER_BATCH;
    private static final int INSERT_CHUNK = 10;
    private static final int PROCESS_CHUNK = 20;
    private static final int FAILING_BATCH = 2; // "batch 3" on the page

    record Options(boolean dontWait, boolean failBatch) {}

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // One virtual thread per task: fine for work that mostly waits on the database
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final RecordRepository records;
    private final FragmentRenderer fragments;

    PipelineController(RecordRepository records, FragmentRenderer fragments) {
        this.records = records;
        this.fragments = fragments;
    }

    @GetMapping("/step7")
    String page() {
        return "step7";
    }

    @PostMapping("/step7/jobs")
    String start(@RequestParam(defaultValue = "false") boolean dontWait,
                 @RequestParam(defaultValue = "false") boolean failBatch, Model model) {
        var jobId = UUID.randomUUID().toString();
        var emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));

        // Only wires the actions together and returns: the request thread doesn't wait for any of them
        run(jobId, new Options(dontWait, failBatch), emitter);

        model.addAttribute("jobId", jobId);
        model.addAttribute("options", new Options(dontWait, failBatch));
        return "fragments/step7 :: job";
    }

    @GetMapping(path = "/step7/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String jobId) {
        var emitter = emitters.get(jobId);
        if (emitter == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or finished job");
        }
        return emitter;
    }

    private void run(String jobId, Options options, SseEmitter emitter) {
        var inserted = new AtomicIntegerArray(BATCHES); // rows inserted so far, per batch

        // Action 1: insert. Each batch runs on its own thread, so they progress side by side.
        var batches = new CompletableFuture<?>[BATCHES];
        for (int batch = 0; batch < BATCHES; batch++) {
            int b = batch;
            batches[b] = CompletableFuture.runAsync(() -> insertBatch(jobId, b, options, inserted, emitter), executor);
        }
        // Completes when every batch has finished, or exceptionally if any of them failed
        CompletableFuture<Void> allInserted = CompletableFuture.allOf(batches);

        // Action 2: process
        CompletableFuture<Void> pipeline;
        if (options.dontWait()) {
            // Wrong on purpose: processing starts right away, next to the inserts, and only sees the rows that
            // happen to be there already. allOf here just makes the summary wait for everything.
            pipeline = CompletableFuture.allOf(CompletableFuture.runAsync(() -> process(jobId, emitter), executor), allInserted);
        } else {
            // Right: thenRunAsync starts processing only after allInserted completed normally. If a batch failed,
            // processing is skipped and the failure travels on to whenComplete.
            pipeline = allInserted.thenRunAsync(() -> process(jobId, emitter), executor);
        }

        pipeline.whenComplete((ignored, error) -> finish(jobId, options, error, emitter));
    }

    private void insertBatch(String jobId, int batch, Options options, AtomicIntegerArray inserted, SseEmitter emitter) {
        var random = ThreadLocalRandom.current();
        for (int rows = 0; rows < ROWS_PER_BATCH; rows += INSERT_CHUNK) {
            pause(random.nextInt(150, 600)); // pretend the database is slow, and not equally slow for every batch
            if (options.failBatch() && batch == FAILING_BATCH && rows == 20) {
                throw new IllegalStateException("Batch %d failed after %d rows (simulated)".formatted(batch + 1, rows));
            }
            records.insert(jobId, batch, random.ints(INSERT_CHUNK, 1, 100).boxed().toList());
            inserted.addAndGet(batch, INSERT_CHUNK);
            send(emitter, "insert", () -> fragments.render("fragments/step7", "batches",
                    Map.of("inserted", snapshot(inserted), "rowsPerBatch", ROWS_PER_BATCH)));
        }
        log.info("Step 7: job {} batch {} inserted", jobId, batch + 1);
    }

    private void process(String jobId, SseEmitter emitter) {
        int processed = 0;
        List<Long> ids;
        // Works through whatever NEW rows exist, until there are none left
        while (!(ids = records.findNew(jobId, PROCESS_CHUNK)).isEmpty()) {
            pause(150);
            records.process(ids);
            processed += ids.size();
            int done = processed;
            send(emitter, "process", () -> fragments.render("fragments/step7", "process",
                    Map.of("processed", done, "total", TOTAL_ROWS)));
        }
        if (processed == 0) {
            // Found nothing at all (the "don't wait" case): still say so, instead of leaving "Looking…" on screen
            send(emitter, "process", () -> fragments.render("fragments/step7", "process",
                    Map.of("processed", 0, "total", TOTAL_ROWS)));
        }
        log.info("Step 7: job {} processed {} rows", jobId, processed);
    }

    private void finish(String jobId, Options options, Throwable error, SseEmitter emitter) {
        try {
            var summary = records.summary(jobId);
            if (error == null) {
                send(emitter, "done", () -> fragments.render("fragments/step7", "result",
                        Map.of("summary", summary, "total", TOTAL_ROWS, "options", options)));
            } else {
                var cause = error instanceof CompletionException ? error.getCause() : error;
                log.warn("Step 7: job {} failed: {}", jobId, cause.getMessage());
                send(emitter, "done", () -> fragments.render("fragments/step7", "error",
                        Map.of("message", cause.getMessage(), "summary", summary, "total", TOTAL_ROWS)));
            }
        } finally {
            emitter.complete(); // even if the summary query fails, don't leave the stream open
        }
    }

    /**
     * Several batch threads send on the same emitter. A single send is thread-safe, but "render the current state,
     * then send it" is two steps: without the lock, an older snapshot could be sent after a newer one.
     */
    private void send(SseEmitter emitter, String name, Supplier<String> html) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name(name).data(html.get()));
            } catch (IOException | IllegalStateException e) {
                // The browser left. The database work carries on anyway: it doesn't depend on anyone watching.
                log.debug("Step 7: could not send {}: {}", name, e.toString());
            }
        }
    }

    private static List<Integer> snapshot(AtomicIntegerArray values) {
        var list = new ArrayList<Integer>(values.length());
        for (int i = 0; i < values.length(); i++) {
            list.add(values.get(i));
        }
        return list;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
