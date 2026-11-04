package com.example.nutritionplanner;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.decision.PropositionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nutritionplanner.LocalModelFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class PatternExamplesIntegrationTests {

    private static final PatternRequest REQUEST = new PatternRequest("Use lentils and carrots in a 20-minute dinner.");

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openai", "azure"})
    void nativeDecisionServicesKeepSelectedUnmatchedInconclusiveAndFailureDistinct(String profile) throws IOException {
        try (var fixture = new LocalModelFixture(profile, "fixture-decisions")) {
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                assertEquals(0, fixture.requests.size());
                var routing = context.getBean(TypedRoutingExample.class);

                for (String route : List.of("pantry", "technique")) {
                    fixture.respondWith(request -> fixture.requests.size() == 1
                            ? fixture.text(Map.of("verdict", "SELECTED", "categoryId", route))
                            : fixture.raw("{\"answers\":[{\"question\":\"q1\",\"verdict\":\"TRUE\"}]}"));
                    var result = routing.route(REQUEST);
                    assertEquals(route, result.route());
                    var selected = assertInstanceOf(ClassificationResult.Selected.class, result.classification());
                    assertEquals(fixture.model, selected.getProvenance().getModelName());
                    assertNull(selected.getConfidence(), "Prompted decisions must not invent numerical confidence");
                    assertNotNull(result.preferences());
                    assertTrue(assertInstanceOf(PropositionResult.Answered.class,
                            result.preferences().answer(TypedRoutingExample.QUICK)).getAnswer());
                    assertEquals(2, fixture.requests.size());
                    fixture.assertWireFormat();
                    String userInput = fixture.requests.getFirst().path("messages").valueStream()
                            .filter(message -> message.path("role").asText().equals("user"))
                            .findFirst().orElseThrow().path("content").asText();
                    assertEquals(REQUEST.prompt(), fixture.mapper.readTree(userInput).path("input").asText());
                }

                for (String verdict : List.of("NO_MATCH", "INCONCLUSIVE")) {
                    fixture.respondWith(request -> fixture.text(Map.of("verdict", verdict)));
                    var result = routing.route(REQUEST);
                    assertNull(result.route());
                    assertNull(result.preferences());
                    assertEquals(verdict.equals("NO_MATCH") ? ClassificationResult.NoMatch.class
                            : ClassificationResult.Inconclusive.class, result.classification().getClass());
                    assertEquals(1, fixture.requests.size(), "Do not run a fallback route or ask more questions");
                }

                for (String invalid : List.of("not-json", "{\"verdict\":\"SELECTED\",\"categoryId\":\"medical\"}",
                        "{\"verdict\":\"NO_MATCH\",\"categoryId\":\"pantry\"}")) {
                    fixture.respondWith(request -> fixture.raw(invalid));
                    var failure = assertThrows(PatternFailure.class, () -> routing.route(REQUEST));
                    assertEquals(PatternFailure.Reason.INVALID_RESPONSE, failure.reason());
                    assertEquals(1, fixture.requests.size(), "Decision retry ceiling is one attempt");
                }
                fixture.respondWith(request -> fixture.error());
                assertEquals(PatternFailure.Reason.MODEL_UNAVAILABLE,
                        assertThrows(PatternFailure.class, () -> routing.route(REQUEST)).reason());
                assertEquals(1, fixture.requests.size());

                for (String verdict : List.of("FALSE", "INCONCLUSIVE")) {
                    fixture.respondWith(request -> fixture.requests.size() == 1
                            ? fixture.text(Map.of("verdict", "SELECTED", "categoryId", "pantry"))
                            : fixture.raw("{\"answers\":[{\"question\":\"q1\",\"verdict\":\"" + verdict + "\"}]}"));
                    var result = routing.route(REQUEST);
                    assertNotNull(result.preferences());
                    if (verdict.equals("FALSE")) {
                        assertFalse(assertInstanceOf(PropositionResult.Answered.class,
                                result.preferences().answer(TypedRoutingExample.QUICK)).getAnswer());
                    } else {
                        assertInstanceOf(PropositionResult.Inconclusive.class,
                                result.preferences().answer(TypedRoutingExample.QUICK));
                    }
                    assertEquals(2, fixture.requests.size());
                }
                fixture.respondWith(request -> fixture.requests.size() == 1
                        ? fixture.text(Map.of("verdict", "SELECTED", "categoryId", "pantry"))
                        : fixture.raw("{\"answers\":[]}"));
                assertEquals(PatternFailure.Reason.INVALID_RESPONSE,
                        assertThrows(PatternFailure.class, () -> routing.route(REQUEST)).reason());
                assertEquals(2, fixture.requests.size());
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openai", "azure"})
    void realSupervisorComposesSpecialistsAndRealHybridTerminatesAtItsGoal(String profile) throws IOException {
        try (var fixture = new LocalModelFixture(profile, "fixture-native-planners")) {
            var supervisorCalls = new AtomicInteger();
            fixture.respondWith(request -> supervisorReply(fixture, request, supervisorCalls, "lentils"));
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                var platform = context.getBean(AgentPlatform.class);
                assertEquals(1, platform.agents().size(), "Opt-in examples must not deploy additional global agents");
                assertFalse(platform.getActions().stream().anyMatch(action -> action.getName().contains("CulinaryWorkers")));
                assertFalse(platform.getActions().stream().anyMatch(action -> action.getName().contains("KitchenResearch")));

                var supervisor = context.getBean(SupervisorExample.class).run(REQUEST);
                assertEquals(List.of("pantryIdeas", "preparationSteps", "composeAdvice"), supervisor.actions());
                assertEquals(6, supervisor.modelCalls(), "Four supervisor turns plus two worker model calls");
                assertEquals(6, fixture.requests.size());
                assertEquals("lentils bowl", supervisor.advice().pantry().ideas().getFirst());
                fixture.assertWireFormat();

                fixture.respondWith(request -> fixture.text(Map.of("suggestion", "Make a lentil bowl.")));
                var hybrid = context.getBean(HybridExample.class).run(REQUEST);
                assertEquals(List.of("pantryEvidence", "techniqueEvidence", "synthesize"), hybrid.actions());
                assertEquals(1, hybrid.modelCalls());
                assertEquals(1, fixture.requests.size());
                assertEquals("Make a lentil bowl.", hybrid.advice().suggestion().suggestion());
                assertTrue(messages(fixture.requests.getFirst()).contains("Keep wet sauces separate"));
                assertTrue(messages(fixture.requests.getFirst()).contains("Start the longest cooking step first"));
                fixture.assertWireFormat();
            });
        }
    }

    @Test
    void noProgressSupervisorStopsBeforeAnExtraModelCallAndHybridBudgetCannotReturnPartialAdvice() throws IOException {
        try (var fixture = new LocalModelFixture("openai", "fixture-budgets")) {
            fixture.respondWith(request -> fixture.raw("I have not performed any actions."));
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                var failure = assertThrows(PatternFailure.class,
                        () -> context.getBean(SupervisorExample.class).run(REQUEST));
                assertEquals(PatternFailure.Reason.LIMIT_EXCEEDED, failure.reason());
                assertEquals(SupervisorExample.MAX_MODEL_CALLS, fixture.requests.size());

                fixture.respondWith(request -> fixture.text(Map.of("suggestion", "Must not be requested.")));
                assertEquals(PatternFailure.Reason.GOAL_NOT_REACHED,
                        assertThrows(PatternFailure.class, () -> context.getBean(HybridExample.class).run(REQUEST, 2)).reason());
                assertEquals(0, fixture.requests.size(), "The research budget must stop before synthesis");
            });
        }
    }

    @Test
    void supervisorRejectsExcessFanoutAndPrematureCompositionBeforeWorkerSideEffects() throws IOException {
        try (var fixture = new LocalModelFixture("openai", "fixture-fanout")) {
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                var supervisor = context.getBean(SupervisorExample.class);
                fixture.respondWith(request -> {
                    String pantry = functionName(request, "pantryIdeas");
                    return fixture.toolCalls(List.of(fixture.tool(pantry, Map.of()),
                            fixture.tool(pantry, Map.of()), fixture.tool(pantry, Map.of())));
                });
                assertEquals(PatternFailure.Reason.LIMIT_EXCEEDED,
                        assertThrows(PatternFailure.class, () -> supervisor.run(REQUEST)).reason());
                assertEquals(1, fixture.requests.size());

                fixture.respondWith(request -> fixture.toolCall(functionName(request, "composeAdvice"), Map.of()));
                assertEquals(PatternFailure.Reason.INVALID_RESPONSE,
                        assertThrows(PatternFailure.class, () -> supervisor.run(REQUEST)).reason());
                assertEquals(1, fixture.requests.size(), "Do not ask the model to invent missing worker results");
            });
        }
    }

    @Test
    void repeatedWorkersAndMalformedModelOutputsNeverBecomeSuccessfulAdvice() throws IOException {
        try (var fixture = new LocalModelFixture("openai", "fixture-invalid-output")) {
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                var supervisor = context.getBean(SupervisorExample.class);
                fixture.respondWith(request -> request.path("tools").isEmpty()
                        ? fixture.text(Map.of("ideas", List.of("Lentil bowl", "Lentil soup")))
                        : fixture.toolCall(functionName(request, "pantryIdeas"), Map.of()));
                assertEquals(PatternFailure.Reason.LIMIT_EXCEEDED,
                        assertThrows(PatternFailure.class, () -> supervisor.run(REQUEST)).reason());
                assertEquals(3, fixture.requests.size(), "A repeated worker must not make a second worker model call");

                fixture.respondWith(request -> request.path("tools").isEmpty()
                        ? fixture.raw("{\"ideas\":null}")
                        : fixture.toolCall(functionName(request, "pantryIdeas"), Map.of()));
                assertThrows(PatternFailure.class, () -> supervisor.run(REQUEST));
                assertTrue(fixture.requests.size() >= 2 && fixture.requests.size() <= SupervisorExample.MAX_MODEL_CALLS);

                fixture.respondWith(request -> fixture.raw("not-json"));
                assertThrows(PatternFailure.class, () -> context.getBean(HybridExample.class).run(REQUEST));
                assertEquals(2, fixture.requests.size(), "Binding retries must share the same small model-call budget");

                fixture.respondWith(request -> fixture.error());
                assertEquals(PatternFailure.Reason.MODEL_UNAVAILABLE,
                        assertThrows(PatternFailure.class, () -> context.getBean(HybridExample.class).run(REQUEST)).reason());
                assertEquals(1, fixture.requests.size(), "A failed provider call must not become budget exhaustion");
            });
        }
    }

    static LocalModelFixture.Reply supervisorReply(LocalModelFixture fixture, JsonNode request,
                                                   AtomicInteger supervisorCalls, String ingredient) {
        if (!request.path("tools").isEmpty()) {
            return switch (supervisorCalls.getAndIncrement()) {
                case 0 -> fixture.toolCall(functionName(request, "pantryIdeas"), Map.of());
                case 1 -> fixture.toolCall(functionName(request, "preparationSteps"), Map.of());
                case 2 -> fixture.toolCall(functionName(request, "composeAdvice"), Map.of());
                default -> fixture.raw("The actual worker results have been combined.");
            };
        }
        if (messages(request).contains("PANTRY SPECIALIST")) {
            return fixture.text(Map.of("ideas", List.of(ingredient + " bowl", ingredient + " soup")));
        }
        assertTrue(messages(request).contains("PREPARATION SPECIALIST"));
        return fixture.text(Map.of("steps", List.of("Prepare " + ingredient, "Cook " + ingredient)));
    }
}
