package com.example.nutritionplanner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;

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

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}
}