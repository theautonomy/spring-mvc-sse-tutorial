package com.example.sse.step3;

import com.example.sse.FragmentRenderer;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 3: a long-running job that reports progress. A POST starts the job and returns HTML that opens an SSE
 * stream for that job. The nutrition planner's {@code POST /plan} works the same way.
 */
@Controller
class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    static final int STEPS = 10;
    private static final Duration STEP_DURATION = Duration.ofMillis(500);

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final FragmentRenderer fragments;

    ProgressController(FragmentRenderer fragments) {
        this.fragments = fragments;
    }

    @GetMapping("/step3")
    String page() {
        return "step3";
    }

    @PostMapping("/step3/jobs")
    String start(@RequestParam String task, Model model) {
        var jobId = UUID.randomUUID().toString();

        // The emitter is created now, but the browser only connects to it after it gets this response.
        // Events sent in between are buffered by the emitter, so the job can start right away.
        var emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));

        Thread.startVirtualThread(() -> run(jobId, task, emitter));

        model.addAttribute("jobId", jobId);
        model.addAttribute("task", task);
        return "fragments/step3 :: job";
    }

    @GetMapping(path = "/step3/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String jobId) {
        var emitter = emitters.get(jobId);
        if (emitter == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or finished job");
        }
        return emitter;
    }

    private void run(String jobId, String task, SseEmitter emitter) {
        try {
            for (int step = 1; step <= STEPS; step++) {
                Thread.sleep(STEP_DURATION);
                if (step == 6 && task.contains("fail")) {
                    throw new JobFailedException("Step 6 failed because the task name contains \"fail\"");
                }
                emitter.send(SseEmitter.event().name("progress").data(fragments.render("fragments/step3", "progress",
                        Map.of("percent", step * 100 / STEPS, "message", "Step %d of %d".formatted(step, STEPS)))));
            }
            // Every way the job can end sends a "done" event. The page calls es.close() when it arrives, instead of
            // letting the browser reconnect to a job that no longer exists.
            emitter.send(SseEmitter.event().name("done").data(fragments.render("fragments/step3", "result",
                    Map.of("task", task, "finishedAt", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)))));
        } catch (IOException | IllegalStateException e) {
            log.info("Step 3: job {} stopped, the browser went away: {}", jobId, e.toString());
        } catch (JobFailedException | InterruptedException e) {
            log.warn("Step 3: job {} failed: {}", jobId, e.getMessage());
            sendQuietly(emitter, SseEmitter.event().name("done").data(fragments.render("fragments/step3", "error",
                    Map.of("message", e.getMessage()))));
        } finally {
            emitter.complete();
        }
    }

    private static void sendQuietly(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.info("Step 3: could not send the error, the browser went away");
        }
    }

    private static class JobFailedException extends RuntimeException {
        JobFailedException(String message) {
            super(message);
        }
    }
}
