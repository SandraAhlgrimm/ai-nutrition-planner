package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(5)
class WorkflowTests {

    @Test
    void differentTypesRunConcurrentlyOnVirtualThreads() {
        var started = new CountDownLatch(2);
        var result = Workflow.parallel(() -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            started.countDown();
            await(started);
            return "profile";
        }, () -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            started.countDown();
            await(started);
            return 42;
        });
        assertThat(result.first()).isEqualTo("profile");
        assertThat(result.second()).isEqualTo(42);
    }

    @Test
    void failuresPropagateWithoutNullOrEmptyFallback() {
        assertThatThrownBy(() -> Workflow.parallel(() -> {
            throw new IllegalStateException("Profile missing");
        }, () -> "ingredients")).hasMessage("Profile missing");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
