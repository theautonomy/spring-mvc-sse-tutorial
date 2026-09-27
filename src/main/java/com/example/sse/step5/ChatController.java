package com.example.sse.step5;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Step 5: a chat room. Every open tab has its own stream; a message posted in one tab is broadcast to all of them.
 */
@Controller
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // How long the browser waits before it reconnects. Long enough to post a message from another tab meanwhile.
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

    private final ChatRoom chatRoom;
    // Only used by the "simulate a dropped connection" button
    private final Map<String, SseEmitter> emittersByTab = new ConcurrentHashMap<>();

    ChatController(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    @GetMapping("/step5")
    String page(Model model) {
        model.addAttribute("user", "guest-" + ThreadLocalRandom.current().nextInt(1000, 10000));
        model.addAttribute("tab", UUID.randomUUID().toString());
        return "step5";
    }

    @GetMapping(path = "/step5/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@RequestParam String tab,
                      @RequestHeader(name = "Last-Event-ID", required = false) @Nullable Long lastEventId) throws IOException {
        var emitter = new SseEmitter(0L); // 0 = no timeout: the heartbeat finds dead tabs instead
        emittersByTab.put(tab, emitter);
        emitter.onCompletion(() -> emittersByTab.remove(tab, emitter));

        // "retry:" tells the browser how long to wait before it reconnects (the default is about 3 s)
        emitter.send(SseEmitter.event().reconnectTime(RECONNECT_DELAY.toMillis()));
        chatRoom.join(emitter, lastEventId);
        return emitter;
    }

    @PostMapping("/step5/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void post(@RequestParam String user, @RequestParam String text) {
        if (!text.isBlank()) {
            chatRoom.post(user.isBlank() ? "anonymous" : user.strip(), text.strip());
        }
    }

    /** Ends this tab's stream from the server side, so you can watch the browser reconnect and catch up. */
    @PostMapping("/step5/tabs/{tab}/drop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void drop(@PathVariable String tab) {
        var emitter = emittersByTab.get(tab);
        if (emitter != null) {
            log.info("Step 5: dropping the stream of tab {}", tab);
            emitter.complete();
        }
    }
}
