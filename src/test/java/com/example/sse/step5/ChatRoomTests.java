package com.example.sse.step5;

import com.example.sse.FragmentRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomTests {

    // Renders a message as its text only, so the tests don't need Thymeleaf
    private final FragmentRenderer renderer = new FragmentRenderer(null) {
        @Override
        public String render(String template, String fragment, Map<String, Object> variables) {
            return ((ChatRoom.Message) variables.get("message")).text();
        }
    };

    private final ChatRoom chatRoom = new ChatRoom(renderer);

    @Test
    void broadcastsMessagesToEveryTab() {
        var tab1 = new RecordingEmitter();
        var tab2 = new RecordingEmitter();
        chatRoom.join(tab1, null);
        chatRoom.join(tab2, null);

        chatRoom.post("alice", "hello");

        assertThat(tab1.messages()).containsExactly("hello");
        assertThat(tab2.messages()).containsExactly("hello");
        assertThat(tab1.events()).anyMatch(e -> e.contains("id:1\nevent:message\n"));
    }

    @Test
    void dropsTabsWhoseSendFails() {
        var alive = new RecordingEmitter();
        var dead = new RecordingEmitter();
        chatRoom.join(alive, null);
        chatRoom.join(dead, null);
        assertThat(chatRoom.online()).isEqualTo(2);

        dead.disconnect();
        chatRoom.post("alice", "anyone there?");

        assertThat(chatRoom.online()).isEqualTo(1);
        assertThat(alive.events()).last().satisfies(e -> assertThat(e).contains("event:presence\ndata:1 online"));
    }

    @Test
    void heartbeatFindsDeadTabs() {
        var dead = new RecordingEmitter();
        chatRoom.join(dead, null);
        dead.disconnect();

        chatRoom.heartbeat();

        assertThat(chatRoom.online()).isZero();
    }

    @Test
    void replaysOnlyMissedMessagesAfterReconnect() {
        chatRoom.post("alice", "one");
        chatRoom.post("alice", "two");
        chatRoom.post("alice", "three");

        var freshTab = new RecordingEmitter();
        chatRoom.join(freshTab, null);
        var reconnectedTab = new RecordingEmitter();
        chatRoom.join(reconnectedTab, 1L);

        assertThat(freshTab.messages()).containsExactly("one", "two", "three");
        assertThat(reconnectedTab.messages()).containsExactly("two", "three");
    }

    @Test
    void keepsALimitedHistory() {
        for (int i = 1; i <= ChatRoom.HISTORY_SIZE + 5; i++) {
            chatRoom.post("alice", "message " + i);
        }

        var tab = new RecordingEmitter();
        chatRoom.join(tab, null);

        assertThat(tab.messages()).hasSize(ChatRoom.HISTORY_SIZE).first().isEqualTo("message 6");
    }

    /** Captures what would be written to the browser, and can pretend the browser went away. */
    static class RecordingEmitter extends SseEmitter {

        private final List<String> events = new ArrayList<>();
        private boolean disconnected;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (disconnected) {
                throw new IOException("Connection reset by peer");
            }
            events.add(builder.build().stream().map(d -> String.valueOf(d.getData())).collect(Collectors.joining()));
        }

        void disconnect() {
            disconnected = true;
        }

        List<String> events() {
            return events;
        }

        /** The data of each "message" event. */
        List<String> messages() {
            return events.stream().filter(e -> e.contains("event:message\n"))
                    .map(e -> e.substring(e.indexOf("data:") + 5).strip())
                    .toList();
        }
    }
}
