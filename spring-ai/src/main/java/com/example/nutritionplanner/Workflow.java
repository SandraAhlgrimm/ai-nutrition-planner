package com.example.nutritionplanner;

import io.micrometer.context.ContextSnapshotFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

final class Workflow {

    private Workflow() {}

    // Application orchestration, not a Spring AI workflow API.
    static <A, B> ParallelResult<A, B> parallel(Supplier<A> first, Supplier<B> second) {
        var context = ContextSnapshotFactory.builder().build().captureAll();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(context.wrap(first::get));
            var secondResult = executor.submit(context.wrap(second::get));
            try {
                return new ParallelResult<>(firstResult.get(), secondResult.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Nutrition planning was interrupted", e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                if (e.getCause() instanceof Error cause) {
                    throw cause;
                }
                throw new IllegalStateException("Nutrition planning failed", e.getCause());
            } finally {
                firstResult.cancel(true);
                secondResult.cancel(true);
            }
        }
    }

    record ParallelResult<A, B>(A first, B second) {}
}
