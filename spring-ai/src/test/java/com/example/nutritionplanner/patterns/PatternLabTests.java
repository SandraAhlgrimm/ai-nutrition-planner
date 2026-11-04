package com.example.nutritionplanner.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Timeout(10)
class PatternLabTests {
    private final PatternTestModel model = new PatternTestModel();
    private final PatternLabService lab = new PatternLabService(model.models());
    private static final PatternRequest REQUEST = new PatternRequest("Suggest quick lentil cooking ideas");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(value = PatternLabService.Specialist.class, names = {"QUICK_MEALS", "BUDGET", "PLANT_BASED"})
    void typedRouterCallsOnlyTheSelectedSpecialist(PatternLabService.Specialist specialist) {
        model.text("{\"specialist\":\"" + specialist + "\",\"rationale\":\"Matches the cooking request\"}")
                .text("{\"advice\":\"Cook lentils ahead of time\"}");
        var result = lab.route(REQUEST);
        assertThat(result.specialist()).isEqualTo(specialist);
        assertThat(result.modelCalls()).isEqualTo(2);
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.getLast().getSystemMessage().getText()).contains(specialist + " specialist");
        assertThat(model.prompts.getLast().getUserMessage().getText()).contains(REQUEST.prompt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void orchestratorRunsItsActualDynamicTasksConcurrentlyThenSynthesizesInTaskOrder(int count) {
        var started = new CountDownLatch(count);
        var specialists = List.of(PatternLabService.Specialist.QUICK_MEALS, PatternLabService.Specialist.BUDGET,
                PatternLabService.Specialist.PLANT_BASED);
        var tasks = IntStream.range(0, count).mapToObj(index ->
                new PatternLabService.Subtask(specialists.get(index), "Task number " + index)).toList();
        model.respond(prompt -> {
            var system = prompt.getSystemMessage().getText();
            if (system.contains("Decompose")) {
                return PatternTestModel.answer(JSON.writeValueAsString(new PatternLabService.TaskPlan(tasks)));
            }
            if (system.contains("Synthesize")) {
                assertThat(prompt.getUserMessage().getText()).contains("Original request", "Worker findings");
                return PatternTestModel.answer("{\"advice\":\"Combined culinary suggestions\"}");
            }
            started.countDown();
            try {
                if (!started.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not run concurrently");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return PatternTestModel.answer("{\"advice\":\"A worker suggestion\"}");
        });

        var result = lab.orchestrate(REQUEST);

        assertThat(result.workers()).extracting(PatternLabService.WorkerResult::task).containsExactlyElementsOf(tasks);
        assertThat(result.modelCalls()).isEqualTo(count + 2);
        assertThat(model.prompts).hasSize(count + 2);
        assertThat(model.prompts.getLast().getSystemMessage().getText()).contains("Synthesize");
    }

    @Test
    void unsupportedRequestsNeverReachASpecialist() {
        model.text("{\"specialist\":\"UNSUPPORTED\",\"rationale\":\"Not a cooking request\"}");
        assertThatThrownBy(() -> lab.route(REQUEST)).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void unknownRoutingEnumFailsWithoutAFallback() {
        model.text("{\"specialist\":\"DOCTOR\",\"rationale\":\"invalid\"}");
        assertThatThrownBy(() -> lab.route(REQUEST)).isInstanceOf(RuntimeException.class);
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void invalidTaskPlansAreRejectedBeforeAnyWorkerRuns() {
        for (var tasks : List.of("null", "[]",
                "[{\"specialist\":\"BUDGET\",\"task\":\"Same\"},{\"specialist\":\"BUDGET\",\"task\":\"Same\"}]",
                "[{\"specialist\":\"BUDGET\",\"task\":\"\"}]",
                "[{\"specialist\":\"BUDGET\",\"task\":\"one\"},{\"specialist\":\"BUDGET\",\"task\":\"two\"},"
                        + "{\"specialist\":\"BUDGET\",\"task\":\"three\"},{\"specialist\":\"BUDGET\",\"task\":\"four\"}]")) {
            model.clear();
            model.text("{\"tasks\":" + tasks + "}");
            assertThatThrownBy(() -> lab.orchestrate(REQUEST)).isInstanceOf(RuntimeException.class);
            assertThat(model.prompts).hasSize(1);
        }
    }

    @Test
    void failedWorkerCancelsItsPeerAndSkipsAggregation() {
        var peerStarted = new CountDownLatch(1);
        var peerCancelled = new AtomicBoolean();
        model.respond(prompt -> {
            var system = prompt.getSystemMessage().getText();
            if (system.contains("Decompose")) {
                return PatternTestModel.answer("""
                        {"tasks":[{"specialist":"BUDGET","task":"Fail this task"},
                                  {"specialist":"QUICK_MEALS","task":"Wait for cancellation"}]}
                        """);
            }
            if (system.contains("QUICK_MEALS specialist")) {
                peerStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError("The waiting worker must be interrupted");
                } catch (InterruptedException exception) {
                    peerCancelled.set(true);
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Worker interrupted", exception);
                }
            }
            try {
                if (!peerStarted.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Peer did not start");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            throw new IllegalStateException("Scripted worker failure");
        });

        assertThatThrownBy(() -> lab.orchestrate(REQUEST)).isInstanceOf(IllegalStateException.class);
        await().atMost(Duration.ofSeconds(2)).untilTrue(peerCancelled);
        assertThat(model.prompts).hasSize(3).noneSatisfy(prompt ->
                assertThat(prompt.getSystemMessage().getText()).contains("Synthesize"));
    }

    @Test
    void invalidPromptsAndCancelledRequestsNeverCallAModel() {
        for (var value : List.of("", " ", "x".repeat(1001))) {
            assertThatThrownBy(() -> new PatternRequest(value)).isInstanceOf(PatternFailure.class);
        }
        assertThatThrownBy(() -> new PatternRequest(null)).isInstanceOf(PatternFailure.class);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> lab.route(REQUEST)).isInstanceOf(PatternFailure.class);
        } finally {
            Thread.interrupted();
        }
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void eightInFlightCallsAreAHardLimitNotAnUnboundedQueue() throws Exception {
        var started = new CountDownLatch(8);
        var release = new CountDownLatch(1);
        var models = model.models();
        var client = models.client(List.of());
        model.respond(_ -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return PatternTestModel.answer("Done");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            try {
                for (int i = 0; i < 8; i++) {
                    futures.add(executor.submit(() -> client.prompt().user("cook")
                            .advisors(models.budget(1)).call().content()));
                }
                assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> client.prompt().user("cook").advisors(models.budget(1)).call().content())
                        .isInstanceOfSatisfying(PatternFailure.class,
                                error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
                assertThat(model.prompts).hasSize(8);
            } finally {
                release.countDown();
            }
            for (var future : futures) assertThat(future.get()).isEqualTo("Done");
        }
    }
}
