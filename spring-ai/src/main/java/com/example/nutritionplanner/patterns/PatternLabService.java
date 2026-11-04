package com.example.nutritionplanner.patterns;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@Profile("patterns")
public final class PatternLabService {
    static final int MAX_WORKERS = 3;
    private final PatternModels models;
    private final ChatClient client;

    PatternLabService(PatternModels models) {
        this.models = models;
        this.client = models.client(List.of());
    }

    public RoutingResult route(PatternRequest request) {
        var budget = models.budget(2);
        var route = client.prompt().system(PatternModels.ADVISORY + """
                        Route the request to exactly one culinary specialist:
                        QUICK_MEALS for time-saving cooking, BUDGET for economical ingredients,
                        PLANT_BASED for plant-based cooking, or UNSUPPORTED for non-culinary or medical requests.
                        Give a short rationale. Do not provide the specialist's answer yet.
                        """).user(request.prompt()).advisors(budget).call().entity(RouteDecision.class);
        if (route == null || route.specialist() == null) {
            throw PatternFailure.output("The router returned no specialist");
        }
        requireSupported(route.specialist());
        var rationale = PatternModels.requiredText(route.rationale(), 400, "routing rationale");
        var advice = answer(route.specialist().instructions(), request.prompt(), budget);
        return new RoutingResult(route.specialist(), rationale, advice, budget.calls());
    }

    public WorkersResult orchestrate(PatternRequest request) {
        var budget = models.budget(MAX_WORKERS + 2);
        var plan = client.prompt().system(PatternModels.ADVISORY + """
                        Decompose the cooking request into 1 to 3 small, independent culinary tasks.
                        Assign each task to QUICK_MEALS, BUDGET, or PLANT_BASED.
                        For a non-culinary or medical request return one UNSUPPORTED task.
                        Each task must be distinct and at most 300 characters.
                        """).user(request.prompt()).advisors(budget).call().entity(TaskPlan.class);
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty() || plan.tasks().size() > MAX_WORKERS) {
            throw PatternFailure.output("The orchestrator must return 1 to 3 tasks");
        }
        var tasks = plan.tasks();
        for (var task : tasks) {
            if (task == null || task.specialist() == null) throw PatternFailure.output("Invalid worker task");
            requireSupported(task.specialist());
            PatternModels.requiredText(task.task(), 300, "worker task");
        }
        if (tasks.stream().map(task -> task.task().strip()).distinct().count() != tasks.size()) {
            throw PatternFailure.output("The orchestrator returned duplicate tasks");
        }
        var results = runWorkers(request.prompt(), tasks, budget);
        var answer = answer("Synthesize the worker findings into one concise culinary suggestion.",
                "Original request:\n" + request.prompt() + "\nWorker findings:\n" + results, budget);
        return new WorkersResult(results, answer, budget.calls());
    }

    private List<WorkerResult> runWorkers(String prompt, List<Subtask> tasks, PatternModels.Budget budget) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var completions = new ExecutorCompletionService<IndexedResult>(executor);
        var futures = new ArrayList<Future<IndexedResult>>();
        var context = ContextSnapshotFactory.builder().build().captureAll();
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try {
            for (int i = 0; i < tasks.size(); i++) {
                int index = i;
                var task = tasks.get(i);
                futures.add(completions.submit(context.wrap(() -> new IndexedResult(index, new WorkerResult(task,
                        answer(task.specialist().instructions(), "Original request:\n" + prompt
                                + "\nYour task:\n" + task.task(), budget))))));
            }
            var results = new ArrayList<IndexedResult>();
            for (int i = 0; i < tasks.size(); i++) {
                var completed = completions.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (completed == null) {
                    throw new PatternFailure(HttpStatus.GATEWAY_TIMEOUT, "The workers exceeded their 30-second deadline");
                }
                results.add(completed.get());
            }
            return results.stream().sorted(Comparator.comparingInt(IndexedResult::index))
                    .map(IndexedResult::result).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PatternFailure(HttpStatus.SERVICE_UNAVAILABLE, "Worker execution was interrupted");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("Worker execution failed", exception.getCause());
        } finally {
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }
    }

    private String answer(String instructions, String prompt, PatternModels.Budget budget) {
        budget.checkActive();
        var advice = client.prompt().system(PatternModels.ADVISORY + instructions)
                .user(prompt).advisors(budget).call().entity(Advice.class);
        if (advice == null) throw PatternFailure.output("The model returned no culinary advice");
        return PatternModels.requiredText(advice.advice(), 1600, "advice");
    }

    private static void requireSupported(Specialist specialist) {
        if (specialist == Specialist.UNSUPPORTED) {
            throw new PatternFailure(HttpStatus.UNPROCESSABLE_CONTENT, "This lab supports culinary requests, not medical advice");
        }
    }

    public enum Specialist {
        QUICK_MEALS, BUDGET, PLANT_BASED, UNSUPPORTED;

        String instructions() {
            return switch (this) {
                case QUICK_MEALS -> "You are the QUICK_MEALS specialist. Suggest practical time-saving cooking techniques.";
                case BUDGET -> "You are the BUDGET specialist. Suggest economical ingredient choices without inventing prices.";
                case PLANT_BASED -> "You are the PLANT_BASED specialist. Suggest plant-based culinary substitutions.";
                case UNSUPPORTED -> throw new IllegalStateException("Unsupported specialist cannot run");
            };
        }
    }

    public record RouteDecision(@JsonProperty(required = true) Specialist specialist,
                                @JsonProperty(required = true) String rationale) {}
    public record Advice(@JsonProperty(required = true) String advice) {}
    public record Subtask(@JsonProperty(required = true) Specialist specialist,
                          @JsonProperty(required = true) String task) {}
    public record TaskPlan(@JsonProperty(required = true) List<Subtask> tasks) {}
    public record RoutingResult(Specialist specialist, String rationale, String advice, int modelCalls) {}
    public record WorkerResult(Subtask task, String advice) {}
    public record WorkersResult(List<WorkerResult> workers, String advice, int modelCalls) {}
    private record IndexedResult(int index, WorkerResult result) {}
}
