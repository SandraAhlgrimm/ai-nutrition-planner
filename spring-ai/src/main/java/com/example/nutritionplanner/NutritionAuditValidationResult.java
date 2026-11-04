package com.example.nutritionplanner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

record NutritionAuditValidationResult(@JsonProperty(required = true) boolean allPassed, List<NutritionAuditRecipeViolation> violations,
                                      String consolidatedFeedback) implements ValidationRetryAdvisor.ValidationResult {

    NutritionAuditValidationResult {
        violations = List.copyOf(violations);
        Objects.requireNonNull(consolidatedFeedback, "Audit feedback is required");
        if (allPassed && !violations.isEmpty()) {
            throw new IllegalArgumentException("A passing audit must not contain violations");
        }
        if (!allPassed && violations.isEmpty() && consolidatedFeedback.isBlank()) {
            throw new IllegalArgumentException("A failing audit must explain the failure");
        }
    }

    @Override
    public String feedback() {
        return this.toString();
    }

    NutritionAuditValidationResult withDeterministicChecks(
            WeeklyPlan plan, WeeklyPlanRequest request, UserProfile profile) {
        var shapeFeedback = request.shapeFeedback(plan);
        var calorieViolations = shapeFeedback.isEmpty()
                ? plan.dailyCalorieViolations(request, profile)
                : List.<NutritionAuditRecipeViolation>of();
        if (shapeFeedback.isEmpty() && calorieViolations.isEmpty()) {
            return this;
        }

        var combinedViolations = new ArrayList<>(violations);
        combinedViolations.addAll(calorieViolations);
        var feedback = new ArrayList<String>();
        if (!consolidatedFeedback.isBlank()) {
            feedback.add(consolidatedFeedback);
        }
        if (!shapeFeedback.isEmpty()) {
            feedback.add(shapeFeedback);
        }
        if (!calorieViolations.isEmpty()) {
            feedback.add(calorieViolations.stream()
                    .map(violation -> violation.explanation() + " " + violation.suggestedFix())
                    .collect(Collectors.joining("\n")));
        }
        return new NutritionAuditValidationResult(false, combinedViolations, String.join("\n", feedback));
    }

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}
}