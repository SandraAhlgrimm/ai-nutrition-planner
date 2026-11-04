package com.example.nutritionplanner.patterns;

import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nutritionplanner.patterns.PatternModels.*;
import static com.example.nutritionplanner.patterns.PatternTestModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternWorkflowTest {

    @Test
    void nativeRouterInvokesOnlyTheSpecialistAtOrAboveItsThreshold() {
        var chat = new Chat(request -> "Use a wok and seasonal vegetables.");
        var decision = new Decision(request -> probabilities(Map.of(
                "mediterranean", 0.1, "eastAsian", 0.8, "baking", 0.2)));

        var result = lab(chat, decision).route(new PromptRequest("An East Asian stir-fry idea"));

        assertThat(result.advice()).containsOnlyKeys("eastAsian");
        assertThat(result.probabilities()).containsEntry("eastAsian", 0.8);
        assertThat(result.calls()).isEqualTo(new Calls(1, 1));
        assertThat(chat.requests).hasSize(1);
        assertThat(userText(chat.requests.getFirst())).contains("East Asian", "stir-fry");
        assertThat(decision.requests.getFirst().input())
                .isEqualTo(FieldsInput.from(Map.of("prompt", "An East Asian stir-fry idea")));
        assertThat(decision.requests.getFirst().questions()).containsOnlyKeys("mediterranean", "eastAsian", "baking");
        assertThat(result.advisoryOnly()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.7999})
    void unknownOrLowConfidenceRequestsReturnNoMatchRatherThanInventingARoute(double probability) {
        var chat = new Chat(request -> "must not run");
        var decision = new Decision(request -> probabilities(Map.of(
                "mediterranean", probability, "eastAsian", 0.0, "baking", 0.0)));

        assertThatThrownBy(() -> lab(chat, decision).route(new PromptRequest("Explain orbital mechanics")))
                .isInstanceOfSatisfying(PatternException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("no_matching_specialist");
                    assertThat(failure.status().value()).isEqualTo(422);
                    assertThat(failure.details().get("calls")).isEqualTo(new Calls(0, 1));
                });
        assertThat(chat.requests).isEmpty();
        assertThat(decision.requests).hasSize(1);
    }

    @Test
    void nativeRouterCanSelectSeveralSpecialistsAndRunThemConcurrently() {
        var both = new CountDownLatch(2);
        var chat = new Chat(request -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            both.countDown();
            await(both);
            return userText(request).startsWith("Suggest a Mediterranean approach")
                    ? "olive bread" : "baking technique";
        });
        var decision = new Decision(request -> probabilities(Map.of(
                "mediterranean", 0.95, "eastAsian", 0.1, "baking", 0.9)));

        var result = lab(chat, decision).route(new PromptRequest("Mediterranean bread baking"));

        assertThat(result.advice()).containsExactlyInAnyOrderEntriesOf(
                Map.of("mediterranean", "olive bread", "baking", "baking technique"));
        assertThat(result.calls()).isEqualTo(new Calls(2, 1));
    }

    @Test
    void refusedDecisionRemainsInconclusiveAndDoesNotActivateAnExpert() {
        var chat = new Chat(request -> "must not run");
        var decision = new Decision(request -> DecisionResponse.builder()
                .answer("mediterranean", RefusalAnswer.of())
                .answer("eastAsian", YesNoAnswer.from(0.9))
                .answer("baking", YesNoAnswer.from(0.0)).build());

        assertThatThrownBy(() -> lab(chat, decision).route(new PromptRequest("A refused request")))
                .isInstanceOfSatisfying(PatternException.class,
                        failure -> assertThat(failure.code()).isEqualTo("decision_inconclusive"));
        assertThat(chat.requests).isEmpty();
    }

    @Test
    void missingDecisionAnswersAreProviderErrorsNotNoMatchOrApproval() {
        var chat = new Chat(request -> "must not run");
        var decision = new Decision(request -> probabilities(Map.of("mediterranean", 0.9)));

        assertThatThrownBy(() -> lab(chat, decision).route(new PromptRequest("Mediterranean cooking")))
                .isInstanceOfSatisfying(PatternException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("pattern_execution_failed");
                    assertThat(failure.status().value()).isEqualTo(502);
                });
        assertThat(chat.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1})
    void invalidDecisionProbabilitiesAreProviderErrors(double probability) {
        var chat = new Chat(request -> "must not run");
        var decision = new Decision(request -> probabilities(Map.of(
                "mediterranean", probability, "eastAsian", 0.1, "baking", 0.1)));

        assertThatThrownBy(() -> lab(chat, decision).route(new PromptRequest("Mediterranean cooking")))
                .isInstanceOfSatisfying(PatternException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(502));
        assertThat(chat.requests).isEmpty();
        assertThat(decision.requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void cascadeStopsOnFirstMiddleOrFinalAcceptanceWithExactGenerationAndJudgmentCounts(int acceptedTier) {
        var generations = new AtomicInteger();
        var judgments = new AtomicInteger();
        var chat = new Chat(request -> "candidate-" + generations.incrementAndGet());
        var decision = new Decision(request -> probabilities(Map.of("accepted",
                judgments.incrementAndGet() == acceptedTier ? 0.8 : 0.2)));

        var result = lab(chat, decision).cascade(new PromptRequest("How should I roast vegetables?"));

        assertThat(result.advice()).isEqualTo("candidate-" + acceptedTier);
        assertThat(result.tier()).isEqualTo(PatternLab.TIERS.get(acceptedTier - 1));
        assertThat(result.calls()).isEqualTo(new Calls(acceptedTier, acceptedTier));
        assertThat(chat.requests).hasSize(acceptedTier);
        assertThat(decision.requests).hasSize(acceptedTier);
        assertThat(result.assessments()).hasSize(acceptedTier);
        for (int i = 0; i < acceptedTier; i++) {
            assertThat(decision.requests.get(i).input()).isEqualTo(FieldsInput.from(Map.of(
                    "prompt", "How should I roast vegetables?", "candidateAnswer", "candidate-" + (i + 1))));
            var question = (YesNoQuestion) decision.requests.get(i).questions().get("accepted");
            assertThat(question.yesWhen()).isEqualTo(PatternLab.ACCEPTANCE_CRITERION);
            assertThat(result.assessments().get(i).applicationFinalCheck()).isEqualTo(i == 2);
        }
        assertThat(result.assessments().getLast().accepted()).isTrue();
        assertThat(result.advisoryOnly()).isTrue();
    }

    @Test
    void fullCascadeExhaustionGradesEvenAnIdenticalFinalAnswerAndNeverReturnsItAsSuccess() {
        var chat = new Chat(request -> "the same inadequate answer");
        var decision = new Decision(request -> probabilities(Map.of("accepted", 0.1)));

        assertThatThrownBy(() -> lab(chat, decision).cascade(new PromptRequest("Explain a cooking technique")))
                .isInstanceOfSatisfying(PatternException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("cascade_exhausted");
                    assertThat(failure.status().value()).isEqualTo(422);
                    assertThat(failure.details().get("calls")).isEqualTo(new Calls(3, 3));
                    assertThat(failure.details().get("assessments")).isEqualTo(List.of(
                            new Assessment("quick", 0.1, false, false),
                            new Assessment("practical", 0.1, false, false),
                            new Assessment("detailed", 0.1, false, true)));
                });
        assertThat(chat.requests).hasSize(3);
        assertThat(decision.requests).hasSize(3);
    }

    @Test
    void gradingFailureStopsTheCascadeWithoutFallbackOrAnotherGeneration() {
        var chat = new Chat(request -> "first candidate");
        var decision = new Decision(request -> {
            throw new IllegalStateException("scripted decision outage");
        });

        assertThatThrownBy(() -> lab(chat, decision).cascade(new PromptRequest("Explain a cooking technique")))
                .isInstanceOf(PatternException.class).hasRootCauseMessage("scripted decision outage");
        assertThat(chat.requests).hasSize(1);
        assertThat(decision.requests).hasSize(1);
    }

    @Test
    void nativeMapperOverlapsWorkersKeepsItemsIsolatedAndSynthesizesInInputOrder() {
        var all = new CountDownLatch(4);
        var laterItemsFinished = new CountDownLatch(3);
        var completionOrder = new CopyOnWriteArrayList<String>();
        var completed = new AtomicInteger();
        var briefs = List.of("brief-one", "brief-two", "brief-three", "brief-four");
        var chat = new Chat(request -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            String text = userText(request);
            if (text.contains("Your assigned meal brief:")) {
                String brief = briefs.stream().filter(text::contains).findFirst().orElseThrow();
                assertThat(briefs.stream().filter(text::contains)).containsExactly(brief);
                assertThat(text).contains("weekday cooking");
                all.countDown();
                await(all);
                if (brief.equals("brief-one")) {
                    await(laterItemsFinished);
                }
                completionOrder.add(brief);
                if (!brief.equals("brief-one")) {
                    laterItemsFinished.countDown();
                }
                completed.incrementAndGet();
                return "idea-" + brief;
            }
            assertThat(completed).hasValue(4);
            assertThat(text).contains("[idea-brief-one, idea-brief-two, idea-brief-three, idea-brief-four]");
            return "These four meals complement each other.";
        });

        var result = lab(chat, null).map(new MapperRequest("weekday cooking", briefs));

        assertThat(result.items()).containsExactly(
                new MealIdea(0, "brief-one", "idea-brief-one"), new MealIdea(1, "brief-two", "idea-brief-two"),
                new MealIdea(2, "brief-three", "idea-brief-three"), new MealIdea(3, "brief-four", "idea-brief-four"));
        assertThat(result.synthesis()).isEqualTo("These four meals complement each other.");
        assertThat(result.calls()).isEqualTo(new Calls(5, 0));
        assertThat(result.advisoryOnly()).isTrue();
        assertThat(completionOrder.getLast()).isEqualTo("brief-one");
        assertThat(chat.requests).hasSize(5).allSatisfy(request -> {
            assertThat(request.parameters().maxOutputTokens()).isEqualTo(700);
            assertThat(request.toolSpecifications()).isEmpty();
        });
    }

    @Test
    void duplicateBriefsRemainSeparateNativeItemInvocations() {
        var generated = new AtomicInteger();
        var chat = new Chat(request -> userText(request).contains("Your assigned meal brief:")
                ? "idea-" + generated.incrementAndGet() : "summary");

        var result = lab(chat, null).map(new MapperRequest("weekday", List.of("same brief", "same brief")));

        assertThat(result.items()).extracting(MealIdea::index).containsExactly(0, 1);
        assertThat(result.items()).extracting(MealIdea::item).containsExactly("same brief", "same brief");
        assertThat(result.items()).extracting(MealIdea::idea).containsExactlyInAnyOrder("idea-1", "idea-2");
        assertThat(result.calls()).isEqualTo(new Calls(3, 0));
    }

    @Test
    void aSingleItemStillRunsMappingBeforeSynthesis() {
        var chat = new Chat(request -> userText(request).contains("Your assigned meal brief:") ? "one idea" : "summary");

        var result = lab(chat, null).map(new MapperRequest("weekday", List.of("a soup")));

        assertThat(result.items()).containsExactly(new MealIdea(0, "a soup", "one idea"));
        assertThat(result.calls()).isEqualTo(new Calls(2, 0));
        assertThat(userText(chat.requests.getLast())).contains("[one idea]");
    }

    @Test
    void overlappingRequestsShareNoItemsResultsOrScopeState() throws Exception {
        var workers = new CountDownLatch(4);
        var chat = new Chat(request -> {
            String text = userText(request);
            String batch = text.contains("batch-A") ? "A" : "B";
            assertThat(text).doesNotContain("batch-" + (batch.equals("A") ? "B" : "A"));
            if (text.contains("Your assigned meal brief:")) {
                workers.countDown();
                await(workers);
                return batch + (text.contains("second") ? "-second" : "-first");
            }
            assertThat(text).contains("[" + batch + "-first, " + batch + "-second]");
            return "summary-" + batch;
        });
        var lab = lab(chat, null);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = callers.submit(() -> lab.map(new MapperRequest("batch-A", List.of("first", "second"))));
            var b = callers.submit(() -> lab.map(new MapperRequest("batch-B", List.of("first", "second"))));
            var first = a.get(10, TimeUnit.SECONDS);
            var second = b.get(10, TimeUnit.SECONDS);
            assertThat(first.synthesis()).isEqualTo("summary-A");
            assertThat(second.synthesis()).isEqualTo("summary-B");
            assertThat(first.items()).extracting(MealIdea::idea).containsExactly("A-first", "A-second");
            assertThat(second.items()).extracting(MealIdea::idea).containsExactly("B-first", "B-second");
            assertThat(first.calls()).isEqualTo(new Calls(3, 0));
            assertThat(second.calls()).isEqualTo(new Calls(3, 0));
        }
    }

    @Test
    void failedMapperItemPreventsSynthesisAndPartialSuccess() {
        var chat = new Chat(request -> {
            if (userText(request).contains("Your assigned meal brief:")) {
                throw new IllegalStateException("scripted worker failure");
            }
            throw new AssertionError("Synthesis must not run after an item fails");
        });

        assertThatThrownBy(() -> lab(chat, null).map(new MapperRequest("context", List.of("one", "two"))))
                .isInstanceOf(PatternException.class).hasRootCauseMessage("scripted worker failure");
        assertThat(chat.requests).allSatisfy(request ->
                assertThat(userText(request)).contains("Your assigned meal brief:"));
    }

    @Test
    void catalogDoesNotCallModelsAndDoesNotPretendAChatModelIsADecisionModel() {
        var chat = new Chat(request -> "must not run");
        var lab = new PatternLab(chat, null, "azure", "Azure decision models are not wired.");

        var catalog = lab.catalog();

        assertThat(catalog.patterns()).extracting(Pattern::available).containsExactly(false, false, true);
        assertThat(catalog.decisionModel()).isNull();
        assertThatThrownBy(() -> lab.route(new PromptRequest("Mediterranean cooking")))
                .isInstanceOfSatisfying(PatternException.class,
                        failure -> assertThat(failure.code()).isEqualTo("decision_model_unavailable"));
        assertThatThrownBy(() -> lab.cascade(new PromptRequest("Mediterranean cooking")))
                .isInstanceOf(PatternException.class);
        assertThat(chat.requests).isEmpty();
    }
}
