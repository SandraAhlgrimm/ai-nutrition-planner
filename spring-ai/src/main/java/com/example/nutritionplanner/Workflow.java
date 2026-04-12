package com.example.nutritionplanner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Workflow {

    @SafeVarargs
    static <T> List<T> parallel(Supplier<T>... tasks) {
        return parallel(2, tasks);
    }

    @SafeVarargs
    static <T> List<T> parallel(int maxWorkers, Supplier<T>... tasks) {
        var executor = Executors.newFixedThreadPool(maxWorkers);
        try {
            var futures = Arrays.stream(tasks)
                    .map(t -> CompletableFuture.supplyAsync(t, executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }
}
