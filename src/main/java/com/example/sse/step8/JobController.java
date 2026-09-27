package com.example.sse.step8;

import com.example.sse.FragmentRenderer;
import com.example.sse.step8.JobRunner.FailureMode;
import com.example.sse.step8.JobStore.Job;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Step 8: the web side of the jobs. Unlike step 7, the stream doesn't get events from the worker threads. It reads
 * the job's state from the database and sends it whenever it changed. That makes the stream independent of the
 * job: after a reload, in a second tab, after a dropped connection, or from another server instance, it shows the
 * same state.
 */
@Controller
class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    // A stream may end before its job does. The browser reconnects and simply gets the current state again.
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(2);

    private final JobStore store;
    private final JobRunner runner;
    private final FragmentRenderer fragments;

    JobController(JobStore store, JobRunner runner, FragmentRenderer fragments) {
        this.store = store;
        this.runner = runner;
        this.fragments = fragments;
    }

    /** {@code ?job=…} reopens a job, for example after a reload. */
    @GetMapping("/step8")
    String page(@RequestParam(required = false) @Nullable String job, Principal principal, Model model) {
        model.addAttribute("recent", store.recent(principal.getName(), 5));
        if (job != null && store.find(job, principal.getName()).isPresent()) {
            model.addAttribute("jobId", job);
        }
        return "step8";
    }

    @PostMapping("/step8/jobs")
    String start(@RequestParam(defaultValue = "NONE") FailureMode mode, Principal principal, Model model) {
        var started = runner.start(principal.getName(), mode);
        model.addAttribute("jobId", started.jobId());
        return "fragments/step8 :: shell";
    }

    @PostMapping("/step8/jobs/{jobId}/cancel")
    ResponseEntity<Void> cancel(@PathVariable String jobId, Principal principal) {
        return runner.cancel(jobId, principal.getName())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping(path = "/step8/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String jobId, Principal principal) {
        var owner = principal.getName();
        // Someone else's job gets the same 404 as a job that doesn't exist: no hint that the id is valid
        if (store.find(jobId, owner).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job");
        }

        var emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        var open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));

        Thread.ofVirtual().name("step8-stream-", 0).start(() -> {
            String sent = null;
            try {
                while (open.get()) {
                    var job = store.find(jobId, owner).orElseThrow();
                    var html = render(job);
                    if (job.status().finished()) {
                        // The last state, as "done": the page shows it and closes the stream
                        emitter.send(SseEmitter.event().name("done").data(html));
                        emitter.complete();
                        return;
                    }
                    if (!html.equals(sent)) { // only send what changed
                        emitter.send(SseEmitter.event().name("state").data(html));
                        sent = html;
                    }
                    Thread.sleep(POLL_INTERVAL);
                }
            } catch (IOException | IllegalStateException e) {
                // The browser left. The job doesn't care: it keeps running and its state stays in the database.
                log.debug("Step 8: stream for job {} closed: {}", jobId, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // For example the database is down: end the stream instead of leaving it open until its timeout.
                // The browser reconnects after a few seconds and tries again.
                log.warn("Step 8: stream for job {} failed", jobId, e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private String render(Job job) {
        return fragments.render("fragments/step8", "state", Map.of(
                "job", job,
                "summary", store.summary(job.id()),
                "rowsPerBatch", JobRunner.ROWS_PER_BATCH,
                "maxParallel", runner.maxParallelBatches(),
                "maxAttempts", JobRunner.MAX_ATTEMPTS));
    }
}
