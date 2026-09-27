package com.example.sse.step6;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingQuestionTests {

    @Test
    void answerFromAnotherThreadWakesTheWaiter() throws Exception {
        var question = new PendingQuestion<Boolean>();
        Thread.startVirtualThread(() -> {
            sleep(Duration.ofMillis(100));
            question.answer(true);
        });

        assertThat(question.await(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void onlyTheFirstAnswerCounts() throws Exception {
        var question = new PendingQuestion<Boolean>();

        assertThat(question.answer(false)).isTrue();
        assertThat(question.answer(true)).isFalse();
        assertThat(question.await(Duration.ofSeconds(1))).isFalse();
    }

    @Test
    void timesOutAndRefusesLateAnswers() {
        var question = new PendingQuestion<Boolean>();

        assertThatThrownBy(() -> question.await(Duration.ofMillis(50))).isInstanceOf(TimeoutException.class);
        assertThat(question.answer(true)).isFalse();
    }

    @Test
    void expiredQuestionRefusesAnswers() {
        var question = new PendingQuestion<Boolean>();
        question.expire();

        assertThat(question.answer(true)).isFalse();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
