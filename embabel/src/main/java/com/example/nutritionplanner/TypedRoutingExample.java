package com.example.nutritionplanner;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.spi.decision.LlmDecisionServiceFactory;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.decision.DecisionResponse;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.DecisionSpec;
import com.embabel.common.ai.decision.PropositionQuestionSpec;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.Questions;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("patterns")
class TypedRoutingExample {

    static final ClassificationSpec ROUTES = ClassificationSpec.builder()
            .asking("Which culinary help fits the request? Do not classify medical or dietary-safety questions.")
            .category("pantry", "Ideas for using existing ingredients and reducing kitchen waste.")
            .category("technique", "Cooking methods, preparation order, and kitchen technique.")
            .build();
    static final PropositionQuestionSpec QUICK = Questions.named("quick_preparation")
            .proposition("The request explicitly asks for a preparation time of at most 30 minutes.").build();
    static final DecisionSpec PREFERENCES = DecisionSpec.of(QUICK);

    private final Ai ai;
    private final DecisionService decisions;

    TypedRoutingExample(Ai ai, LlmDecisionServiceFactory factory, SpringAiLlmService llm) {
        this.ai = ai;
        this.decisions = factory.decisionService(llm);
    }

    RoutingResult route(PatternRequest request) {
        var classification = ai.classifications().using(decisions).classify(request.prompt(), ROUTES);
        return switch (classification) {
            case ClassificationResult.Selected selected -> selected(request, selected);
            case ClassificationResult.NoMatch noMatch -> new RoutingResult(noMatch, null, null,
                    "No culinary route matches this request. No worker was invoked.");
            case ClassificationResult.Inconclusive inconclusive -> new RoutingResult(inconclusive, null, null,
                    "There is not enough information to choose a route. No worker was invoked.");
            case ClassificationResult.Failure failure -> throw PatternFailure.decision(failure.getReason());
        };
    }

    private RoutingResult selected(PatternRequest request, ClassificationResult.Selected selected) {
        var preferences = ai.decisions().using(decisions).ask(request.prompt(), PREFERENCES);
        preferences.requireMatches(PREFERENCES);
        String timing = switch (preferences.answer(QUICK)) {
            case PropositionResult.Answered answered -> answered.getAnswer()
                    ? "The request explicitly asks for quick preparation."
                    : "The request does not ask for preparation within 30 minutes.";
            case PropositionResult.Inconclusive ignored -> "Preparation-time preference is inconclusive.";
            case PropositionResult.Failure failure -> throw PatternFailure.decision(failure.getReason());
        };
        String nextStep = switch (selected.getCategoryId()) {
            case "pantry" -> "Route to pantry-idea assistance. ";
            case "technique" -> "Route to cooking-technique assistance. ";
            default -> throw new PatternFailure(PatternFailure.Reason.INVALID_RESPONSE, "Unknown culinary route.");
        };
        return new RoutingResult(selected, preferences, selected.getCategoryId(), nextStep + timing);
    }

    record RoutingResult(ClassificationResult classification, @Nullable DecisionResponse preferences,
                         @Nullable String route, String nextStep) {}
}
