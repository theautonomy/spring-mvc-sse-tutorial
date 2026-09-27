package com.example.sse.step6;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Step 6: lets a background job wait for an answer that arrives later, in a separate HTTP request.
 * The job thread calls {@link #await}; the controller that receives the answer calls {@link #answer}.
 */
final class PendingQuestion<T> {

    private final CompletableFuture<T> answer = new CompletableFuture<>();

    /**
     * Blocks until someone answers, or throws {@link TimeoutException} when nobody does in time.
     * After a timeout the question is expired, so a late {@link #answer} returns {@code false}.
     */
    T await(Duration timeout) throws InterruptedException, TimeoutException {
        try {
            return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (answer.cancel(false)) {
                throw e;
            }
            return answer.join(); // an answer slipped in between the timeout and the cancel
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Returns {@code false} if the question was already answered or has expired. */
    boolean answer(T value) {
        return answer.complete(value);
    }

    /** Makes later answers fail, for example when the job stopped because the browser went away. */
    void expire() {
        answer.cancel(false);
    }
}
