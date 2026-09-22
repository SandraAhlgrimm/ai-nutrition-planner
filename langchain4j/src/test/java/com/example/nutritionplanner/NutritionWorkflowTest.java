package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionWorkflowTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMonitor monitor = new AgentMonitor();

    @AfterEach
    void close() {
        executor.close();
        registry.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void auditsEveryCandidateAndStopsOnInitialRevisionOrFinalPermittedPass(int passingAudit) {
        var model = new ScriptedChatModel(passingAudit);
        var planner = planner(model);

        assertThat(invoke(planner, "alice")).isEqualTo(plan(passingAudit));
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isEqualTo(passingAudit - 1);
        assertThat(model.audits.get()).isEqualTo(passingAudit);
        assertThat(model.seasonal.get()).isEqualTo(1);
        assertThat(model.verifiedToolResults.get()).isEqualTo(passingAudit * 3);
        assertThat(monitor.successfulExecutions()).hasSize(1);
        assertThat(monitor.failedExecutions()).isEmpty();
        assertIdle();
        assertThat(registry.get("agent_duration").tag("agent", "createNutritionPlan").timer().count()).isEqualTo(1);
        assertThat(registry.get("agent_duration").tag("agent", "createWeeklyPlan").timer().count()).isEqualTo(1);
        assertThat(registry.get("agent_duration").tag("agent", "getUserProfile").timer().count()).isEqualTo(1);

        assertThat(model.generationPrompts).allSatisfy(prompt -> assertThat(prompt)
                .contains(ALICE.toString(), REQUEST.toString(), REQUEST.additionalInstructions(), "carrot"));
        assertThat(model.generationPrompts.getFirst()).doesNotContain("# Current response", "# Feedback");
        for (int revision = 1; revision < passingAudit; revision++) {
            assertThat(model.generationPrompts.get(revision))
                    .contains("Feedback for candidate-" + revision, "candidate-" + revision);
        }
        assertThat(model.auditPrompts).allSatisfy(prompt -> assertThat(prompt)
                .contains(ALICE.toString(), REQUEST.toString(), REQUEST.additionalInstructions()));
    }

    @Test
    void emptyOptionalInstructionsRemainValidWorkflowInput() {
        var model = new ScriptedChatModel(1);
        var request = new WeeklyPlanRequest(REQUEST.days(), "DE", "");

        assertThat(planner(model).createNutritionPlan("alice", request, "SEPTEMBER", "Germany", ""))
                .isEqualTo(plan(1));
        assertThat(model.generationPrompts.getFirst()).contains(request.toString());
        assertIdle();
    }

    @Test
    void exhaustionRejectsFinalAuditedCandidateAfterExactlyThreeRevisions() {
        var model = new ScriptedChatModel(0);
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice"))
                .isInstanceOfSatisfying(PlanValidationException.class, exception ->
                        assertThat(exception.audit()).isEqualTo(failed(4)));
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isEqualTo(3);
        assertThat(model.audits.get()).isEqualTo(4);
        assertThat(model.verifiedToolResults.get()).isEqualTo(12);
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).hasSize(1);
        assertIdle();
    }

    @ParameterizedTest
    @MethodSource("com.example.nutritionplanner.RequestShapeAuditTest#invalidPlans")
    void mistakenPassingModelAuditCannotAcceptBadShapeAndExhaustsAtFourAudits(
            WeeklyPlan invalidPlan, String expectedFeedback) {
        var model = new ScriptedChatModel(1);
        model.planCandidates = candidate -> invalidPlan;
        model.useTools = false;
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice"))
                .isInstanceOfSatisfying(PlanValidationException.class, exception -> {
                    assertThat(exception.audit().allPassed()).isFalse();
                    assertThat(exception.audit().consolidatedFeedback()).contains(expectedFeedback);
                });
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isEqualTo(3);
        assertThat(model.audits.get()).isEqualTo(4);
        assertThat(model.generationPrompts.subList(1, 4)).allSatisfy(prompt ->
                assertThat(prompt).contains(expectedFeedback, ALICE.toString(), REQUEST.toString()));
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertIdle();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void shapeRepairMustBeReauditedBeforeItCanPass(int repairedCandidate) {
        var model = new ScriptedChatModel(1);
        model.planCandidates = candidate -> candidate < repairedCandidate
                ? new WeeklyPlan(List.of(plan(candidate).days().getFirst())) : plan(candidate);
        model.useTools = false;

        assertThat(invoke(planner(model), "alice")).isEqualTo(plan(repairedCandidate));
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isEqualTo(repairedCandidate - 1);
        assertThat(model.audits.get()).isEqualTo(repairedCandidate);
        assertThat(model.generationPrompts.subList(1, repairedCandidate)).allSatisfy(prompt ->
                assertThat(prompt).contains("Missing requested day: THURSDAY", ALICE.toString(), REQUEST.toString()));
        assertThat(monitor.successfulExecutions()).hasSize(1);
        assertIdle();
    }

    @Test
    void profileAndSeasonalPreparationActuallyRunInParallelOnVirtualThreads() {
        var bothStarted = new CountDownLatch(2);
        Set<Long> threads = ConcurrentHashMap.newKeySet();
        var probe = new AgentListener() {
            @Override
            public boolean inheritedBySubagents() {
                return true;
            }

            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                if (Set.of("getUserProfile", "fetchSeasonalIngredients").contains(request.agentName())) {
                    assertThat(Thread.currentThread().isVirtual()).isTrue();
                    threads.add(Thread.currentThread().threadId());
                    bothStarted.countDown();
                    try {
                        assertThat(bothStarted.await(5, TimeUnit.SECONDS))
                                .as("Both branches must start before either can finish").isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }
            }
        };

        assertThat(invoke(planner(new ScriptedChatModel(1), probe), "alice")).isEqualTo(plan(1));
        assertThat(threads).hasSize(2);
        assertIdle();
    }

    @Test
    void oneComposedBeanIsolatesOverlappingRequestsProfilesAndCurrentPlanTools() throws Exception {
        var model = new ScriptedChatModel(1);
        var planner = planner(model);
        var alice = executor.submit(() -> invoke(planner, "alice"));
        var bob = executor.submit(() -> invoke(planner, "bob"));

        assertThat(List.of(alice.get(5, TimeUnit.SECONDS), bob.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(plan(1), plan(2));
        assertThat(model.generationPrompts).hasSize(2)
                .anySatisfy(prompt -> assertThat(prompt).contains(ALICE.toString()).doesNotContain(BOB.toString()))
                .anySatisfy(prompt -> assertThat(prompt).contains(BOB.toString()).doesNotContain(ALICE.toString()));
        assertThat(model.initials.get()).isEqualTo(2);
        assertThat(model.revisions.get()).isZero();
        assertThat(model.audits.get()).isEqualTo(2);
        assertThat(model.verifiedToolResults.get()).isEqualTo(6);
        assertThat(monitor.successfulExecutions()).hasSize(2);
        assertThat(monitor.allMemoryIds()).hasSize(2);
        assertThat(registry.get("agent_duration").tag("agent", "createNutritionPlan").timer().count()).isEqualTo(2);
        assertIdle();
    }

    @Test
    void modelFailurePropagatesWithoutInventingAPlanAndFinalizesFailedParallelExecution() {
        var model = new ScriptedChatModel(1);
        model.failure = new IllegalStateException("scripted model unavailable");
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice"))
                .isInstanceOf(AgentInvocationException.class).hasRootCauseMessage("scripted model unavailable");
        assertThat(model.initials.get()).isZero();
        assertThat(model.audits.get()).isZero();
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).hasSize(1);
        assertThat(registry.get("agent_errors_total").tag("agent", "fetchSeasonalIngredients").counter().count())
                .isEqualTo(1);
        assertIdle();
    }

    @Test
    void malformedPlanFailsInsteadOfCreatingADefaultOrEnteringRevisionLoop() {
        var model = new ScriptedChatModel(1);
        model.planResponseOverride = "not valid JSON";
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice"))
                .isInstanceOf(AgentInvocationException.class).hasStackTraceContaining("OutputParsingException");
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isZero();
        assertThat(model.audits.get()).isZero();
        assertIdle();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "null", "{}", "{\"violations\":[],\"consolidatedFeedback\":\"\"}",
            "{\"allPassed\":null,\"violations\":[],\"consolidatedFeedback\":\"\"}",
            "{\"allPassed\":true,\"violations\":[{\"recipeName\":\"bad\"}],\"consolidatedFeedback\":\"\"}"})
    void malformedOrContradictoryAuditNeverBecomesSuccess(String auditResponse) {
        var model = new ScriptedChatModel(1);
        model.auditResponseOverride = auditResponse;
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice")).isInstanceOf(AgentInvocationException.class);
        assertThat(model.initials.get()).isEqualTo(1);
        assertThat(model.revisions.get()).isZero();
        assertThat(model.audits.get()).isEqualTo(1);
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertIdle();
    }

    @Test
    void malformedToolArgumentsFailExplicitlyRatherThanFeedingAFalseSuccessToTheAuditor() {
        var model = new ScriptedChatModel(1);
        model.invalidToolRequest = true;
        var planner = planner(model);

        assertThatThrownBy(() -> invoke(planner, "alice"))
                .isInstanceOf(AgentInvocationException.class).hasStackTraceContaining("Invalid nutrition tool arguments");
        assertThat(model.revisions.get()).isZero();
        assertThat(model.verifiedToolResults.get()).isZero();
        assertIdle();
    }

    @Test
    void missingAuthenticatedProfileFailsInsteadOfUsingSomeoneElsesPreferences() {
        var planner = planner(new ScriptedChatModel(1));

        assertThatThrownBy(() -> invoke(planner, "unknown"))
                .isInstanceOf(AgentInvocationException.class).hasRootCauseMessage("No profile found for user: unknown");
        assertThat(monitor.successfulExecutions()).isEmpty();
        assertIdle();
    }

    private Agents.NutritionPlanner planner(ChatModel model, AgentListener... extraListeners) {
        var listener = new ComposedAgentListener(new AgentListeners.MicrometerAgentListener(registry), monitor);
        for (var extra : extraListeners) {
            listener.addListener(extra);
        }
        return new AgentConfiguration().nutritionPlanner(model, PROFILES, executor, listener);
    }

    private WeeklyPlan invoke(Agents.NutritionPlanner planner, String username) {
        return planner.createNutritionPlan(username, REQUEST, "SEPTEMBER", "Germany", REQUEST.additionalInstructions());
    }

    private void assertIdle() {
        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(registry.get("agent_active").gauge().value()).isZero();
    }

    private static class ScriptedChatModel implements ChatModel {
        private static final JsonMapper JSON = JsonMapper.builder().build();
        private static final Pattern CANDIDATE = Pattern.compile("candidate-(\\d+)");
        private final int passingAudit;
        private final AtomicInteger seasonal = new AtomicInteger();
        private final AtomicInteger initials = new AtomicInteger();
        private final AtomicInteger revisions = new AtomicInteger();
        private final AtomicInteger candidates = new AtomicInteger();
        private final AtomicInteger audits = new AtomicInteger();
        private final AtomicInteger verifiedToolResults = new AtomicInteger();
        private final List<String> generationPrompts = new CopyOnWriteArrayList<>();
        private final List<String> auditPrompts = new CopyOnWriteArrayList<>();
        private RuntimeException failure;
        private String planResponseOverride;
        private String auditResponseOverride;
        private boolean invalidToolRequest;
        private boolean useTools = true;
        private IntFunction<WeeklyPlan> planCandidates = PlanFixtures::plan;

        ScriptedChatModel(int passingAudit) {
            this.passingAudit = passingAudit;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("scripted-model").build();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (failure != null) {
                throw failure;
            }
            var prompt = request.messages().stream().filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast).reduce((first, second) -> second).orElseThrow().singleText();
            if (prompt.contains("Return a list of ingredients")) {
                seasonal.incrementAndGet();
                assertThat(prompt).contains("SEPTEMBER", "Germany");
                return response("{\"items\":[{\"name\":\"carrot\",\"quantity\":\"200\",\"unit\":\"g\"}]}");
            }
            if (prompt.contains("Create a weekly meal plan") || prompt.contains("Revise the current weekly meal plan")) {
                generationPrompts.add(prompt);
                if (prompt.contains("Revise the current")) {
                    revisions.incrementAndGet();
                } else {
                    initials.incrementAndGet();
                }
                int candidate = candidates.incrementAndGet();
                return response(planResponseOverride != null ? planResponseOverride
                        : JSON.writeValueAsString(planCandidates.apply(candidate)));
            }
            assertThat(prompt).contains("Audit this candidate");
            if (!(request.messages().getLast() instanceof ToolExecutionResultMessage)) {
                audits.incrementAndGet();
                auditPrompts.add(prompt);
                assertThat(request.toolSpecifications()).extracting(spec -> spec.name())
                        .containsExactlyInAnyOrder("dailyNutritionTotals", "nutritionTotalsForDay", "totalMealCount");
                if (!useTools) {
                    boolean passed = passingAudit > 0 && audits.get() >= passingAudit;
                    return response(JSON.writeValueAsString(passed ? PASSED : failed(audits.get())));
                }
                if (invalidToolRequest) {
                    return response(AiMessage.from(tool("nutritionTotalsForDay", "{\"day\":\"NOT_A_DAY\"}")));
                }
                return response(AiMessage.from(tool("dailyNutritionTotals", "{}"),
                        tool("nutritionTotalsForDay", "{\"day\":\"MONDAY\"}"), tool("totalMealCount", "{}")));
            }
            var matcher = CANDIDATE.matcher(prompt);
            assertThat(matcher.find()).isTrue();
            int candidate = Integer.parseInt(matcher.group(1));
            request.messages().stream().filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast).forEach(result -> {
                        var json = JSON.readTree(result.text());
                        switch (result.toolName()) {
                            case "dailyNutritionTotals" -> assertThat(json.get("MONDAY").get("calories").asInt())
                                    .isEqualTo(candidate * 100);
                            case "nutritionTotalsForDay" -> assertThat(json.get("calories").asInt())
                                    .isEqualTo(candidate * 100);
                            case "totalMealCount" -> assertThat(json.asInt()).isEqualTo(2);
                            default -> throw new AssertionError("Unexpected tool: " + result.toolName());
                        }
                        verifiedToolResults.incrementAndGet();
                    });
            boolean passed = passingAudit > 0 && audits.get() >= passingAudit;
            return response(auditResponseOverride != null ? auditResponseOverride
                    : JSON.writeValueAsString(passed ? PASSED : failed(candidate)));
        }

        private static ToolExecutionRequest tool(String name, String arguments) {
            return ToolExecutionRequest.builder().id(name).name(name).arguments(arguments).build();
        }

        private static ChatResponse response(String text) {
            return response(AiMessage.from(text));
        }

        private static ChatResponse response(AiMessage message) {
            return ChatResponse.builder().aiMessage(message).modelName("scripted-model")
                    .tokenUsage(new TokenUsage(10, 5)).build();
        }
    }
}
