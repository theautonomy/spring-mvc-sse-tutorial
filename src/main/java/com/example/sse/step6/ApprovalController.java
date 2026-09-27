package com.example.sse.step6;

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
import java.util.concurrent.TimeoutException;

/**
 * Step 6: two-way interaction. A fake deployment runs a few steps, then pauses and asks for approval over SSE.
 * The answer comes back as a normal POST, and the job continues. This is the nutrition planner's
 * human-in-the-loop flow without the AI.
 */
@Controller
class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    static final Duration APPROVAL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STEP_DURATION = Duration.ofMillis(800);

    record Job(SseEmitter emitter, PendingQuestion<Boolean> approval) {}

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final FragmentRenderer fragments;

    ApprovalController(FragmentRenderer fragments) {
        this.fragments = fragments;
    }

    @GetMapping("/step6")
    String page() {
        return "step6";
    }

    @PostMapping("/step6/jobs")
    String start(@RequestParam String version, Model model) {
        var jobId = UUID.randomUUID().toString();
        var job = new Job(new SseEmitter(Duration.ofMinutes(5).toMillis()), new PendingQuestion<>());
        jobs.put(jobId, job);
        job.emitter().onCompletion(() -> jobs.remove(jobId));

        Thread.startVirtualThread(() -> run(jobId, version, job));

        model.addAttribute("jobId", jobId);
        model.addAttribute("version", version);
        return "fragments/step6 :: job";
    }

    @GetMapping(path = "/step6/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String jobId) {
        var job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or finished job");
        }
        return job.emitter();
    }

    /** The answer arrives here, on a request thread, while the job thread is blocked in {@code await}. */
    @PostMapping("/step6/jobs/{jobId}/answer")
    String answer(@PathVariable String jobId, @RequestParam boolean approved, Model model) {
        var job = jobs.get(jobId);
        var accepted = job != null && job.approval().answer(approved);
        model.addAttribute("accepted", accepted);
        model.addAttribute("approved", approved);
        return "fragments/step6 :: answered";
    }

    private void run(String jobId, String version, Job job) {
        var emitter = job.emitter();
        try {
            step(emitter, "Build " + version);
            step(emitter, "Run tests");
            step(emitter, "Deploy to staging");

            emitter.send(SseEmitter.event().name("question").data(fragments.render("fragments/step6", "question",
                    Map.of("jobId", jobId, "version", version, "timeoutSeconds", APPROVAL_TIMEOUT.toSeconds()))));

            boolean approved;
            try {
                approved = job.approval().await(APPROVAL_TIMEOUT);
            } catch (TimeoutException e) {
                emitter.send(SseEmitter.event().name("question").data(fragments.render("fragments/step6", "expired", Map.of())));
                done(emitter, false, "Nobody approved within %d s, so production was not touched.".formatted(APPROVAL_TIMEOUT.toSeconds()));
                return;
            }

            if (approved) {
                step(emitter, "Deploy " + version + " to production");
                done(emitter, true, "Version %s is live.".formatted(version));
            } else {
                done(emitter, false, "Rejected. Version %s stays on staging.".formatted(version));
            }
        } catch (IOException | IllegalStateException e) {
            log.info("Step 6: job {} stopped, the browser went away: {}", jobId, e.toString());
            job.approval().expire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            emitter.complete();
        }
    }

    private void step(SseEmitter emitter, String name) throws IOException, InterruptedException {
        Thread.sleep(STEP_DURATION);
        emitter.send(SseEmitter.event().name("step").data(fragments.render("fragments/step6", "step",
                Map.of("name", name, "time", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)))));
    }

    private void done(SseEmitter emitter, boolean success, String message) throws IOException {
        emitter.send(SseEmitter.event().name("done").data(fragments.render("fragments/step6", "done",
                Map.of("success", success, "message", message))));
    }
}
