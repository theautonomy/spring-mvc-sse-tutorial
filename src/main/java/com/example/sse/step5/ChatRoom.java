package com.example.sse.step5;

import com.example.sse.FragmentRenderer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Step 5: the registry behind the chat. It holds one emitter per open browser tab, sends every message to all of
 * them, and keeps a short history so a tab that reconnects can catch up.
 */
@Component
class ChatRoom {

    private static final Logger log = LoggerFactory.getLogger(ChatRoom.class);

    static final int HISTORY_SIZE = 50;

    record Message(long id, String user, String text, LocalTime time) {}

    // Iterated on every broadcast and changed only on join and leave, so a copy-on-write list fits well
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<Message> history = new ArrayDeque<>(); // guarded by "this"
    private long lastId; // guarded by "this"
    private final FragmentRenderer fragments;

    ChatRoom(FragmentRenderer fragments) {
        this.fragments = fragments;
    }

    /**
     * Adds a tab to the room. With no {@code lastEventId} (a fresh page load) the tab gets the whole history.
     * With one (the browser reconnected and sent the Last-Event-ID header) it only gets what it missed.
     */
    void join(SseEmitter emitter, @Nullable Long lastEventId) {
        emitter.onCompletion(() -> leave(emitter));
        emitter.onError(_ -> leave(emitter));
        emitters.add(emitter);
        var missed = messagesAfter(lastEventId == null ? 0 : lastEventId);
        log.info("Step 5: tab joined after id {}, replaying {} messages ({} online)", lastEventId, missed.size(), emitters.size());
        missed.forEach(message -> send(emitter, () -> messageEvent(message)));
        broadcast(this::presenceEvent);
    }

    Message post(String user, String text) {
        Message message;
        synchronized (this) {
            message = new Message(++lastId, user, text, LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
            history.addLast(message);
            if (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        broadcast(() -> messageEvent(message));
        return message;
    }

    int online() {
        return emitters.size();
    }

    /**
     * A comment line (": ping") is ignored by the browser, but writing it tells us whether the connection is
     * still alive. Without it, a tab that vanished is only noticed at the next chat message. It also keeps
     * proxies from closing a connection that looks idle.
     */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    void heartbeat() {
        broadcast(() -> SseEmitter.event().comment("ping"));
    }

    private synchronized List<Message> messagesAfter(long id) {
        return history.stream().filter(m -> m.id() > id).toList();
    }

    // Takes a Supplier because an SseEventBuilder is single use: build a new event for each emitter
    private void broadcast(Supplier<SseEventBuilder> event) {
        for (var emitter : emitters) {
            send(emitter, event);
        }
    }

    private void send(SseEmitter emitter, Supplier<SseEventBuilder> event) {
        try {
            emitter.send(event.get());
        } catch (IOException | IllegalStateException e) {
            log.info("Step 5: dropping a dead tab: {}", e.toString());
            leave(emitter);
        }
    }

    private void leave(SseEmitter emitter) {
        if (emitters.remove(emitter)) {
            broadcast(this::presenceEvent);
        }
    }

    private SseEventBuilder messageEvent(Message message) {
        // The id lets the browser tell us, after a reconnect, which message it saw last
        return SseEmitter.event().id(String.valueOf(message.id())).name("message")
                .data(fragments.render("step5/message", Map.of(
                        "id", message.id(), "user", message.user(), "text", message.text(), "time", message.time())));
    }

    private SseEventBuilder presenceEvent() {
        return SseEmitter.event().name("presence").data(emitters.size() + " online");
    }
}
