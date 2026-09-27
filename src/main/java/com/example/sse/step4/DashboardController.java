package com.example.sse.step4;

import com.example.sse.FragmentRenderer;
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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Step 4: one stream, many named events. Each event name updates a different part of the dashboard.
 * The numbers are simulated, per connection.
 */
@Controller
class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final FragmentRenderer fragments;

    DashboardController(FragmentRenderer fragments) {
        this.fragments = fragments;
    }

    @GetMapping("/step4")
    String page() {
        return "step4";
    }

    @GetMapping(path = "/step4/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        var emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        var running = new AtomicBoolean(true);
        emitter.onCompletion(() -> running.set(false));

        Thread.startVirtualThread(() -> {
            var random = ThreadLocalRandom.current();
            int cpu = 30, memory = 55, orders = 0;
            try {
                for (long tick = 1; running.get(); tick++) {
                    cpu = clamp(cpu + random.nextInt(-12, 13));
                    memory = clamp(memory + random.nextInt(-3, 4));
                    emitter.send(SseEmitter.event().name("cpu").data(gauge("CPU", cpu)));
                    emitter.send(SseEmitter.event().name("memory").data(gauge("Memory", memory)));

                    if (random.nextInt(10) < 4) {
                        orders++;
                        emitter.send(SseEmitter.event().name("orders").data(String.valueOf(orders)));
                        emitter.send(SseEmitter.event().name("log").data(logLine("Order #%d received".formatted(1000 + orders))));
                    }
                    if (tick % 5 == 0) {
                        emitter.send(SseEmitter.event().name("log").data(logLine("Health check OK")));
                    }
                    Thread.sleep(1000);
                }
            } catch (IOException | IllegalStateException e) {
                log.info("Step 4: dashboard stream stopped: {}", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return emitter;
    }

    private String gauge(String label, int percent) {
        return fragments.render("fragments/step4", "gauge", Map.of("label", label, "percent", percent));
    }

    private String logLine(String text) {
        return fragments.render("fragments/step4", "log-line",
                Map.of("time", LocalTime.now().truncatedTo(ChronoUnit.SECONDS), "text", text));
    }

    private static int clamp(int value) {
        return Math.clamp(value, 1, 99);
    }
}
