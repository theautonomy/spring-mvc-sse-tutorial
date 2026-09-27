package com.example.sse.step2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 2: a long-lived stream. A background thread pushes the time every second until the browser leaves or the
 * emitter times out.
 */
@Controller
class ClockController {

    private static final Logger log = LoggerFactory.getLogger(ClockController.class);

    // Without an explicit timeout Spring MVC uses the server's async timeout (30 s on Tomcat)
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final AtomicInteger openStreams = new AtomicInteger();

    @GetMapping("/step2")
    String page() {
        return "step2";
    }

    @GetMapping(path = "/step2/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        var emitter = new SseEmitter(TIMEOUT.toMillis());
        var running = new AtomicBoolean(true);
        log.info("Step 2: browser connected ({} open)", openStreams.incrementAndGet());

        // Lifecycle callbacks run on a server thread. onCompletion runs last in every case (done, timeout, error).
        emitter.onTimeout(() -> log.info("Step 2: timed out after {} s, the browser will reconnect", TIMEOUT.toSeconds()));
        emitter.onError(e -> log.info("Step 2: error: {}", e.toString()));
        emitter.onCompletion(() -> {
            running.set(false);
            log.info("Step 2: stream closed ({} open)", openStreams.decrementAndGet());
        });

        Thread.startVirtualThread(() -> {
            try {
                while (running.get()) {
                    var now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
                    emitter.send(SseEmitter.event().name("time").data(now.toString()));
                    Thread.sleep(1000);
                }
            } catch (IOException | IllegalStateException e) {
                // IOException: the browser went away. IllegalStateException: the emitter has already completed.
                log.info("Step 2: stopped sending: {}", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return emitter;
    }
}
