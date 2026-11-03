package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record NutritionAuditValidationResult(boolean allPassed, List<NutritionAuditRecipeViolation> violations,
                                      String consolidatedFeedback) {

    NutritionAuditValidationResult {
        violations = List.copyOf(violations);
        Objects.requireNonNull(consolidatedFeedback, "Audit feedback is required");
        if (allPassed && !violations.isEmpty()) {
            throw new IllegalArgumentException("A passing audit cannot contain violations");
        }
    }

    NutritionAuditValidationResult withRequiredMealChecks(WeeklyPlan plan, WeeklyPlanRequest request) {
        var requiredMealViolations = plan.requiredMealViolations(request);
        if (requiredMealViolations.isEmpty()) {
            return this;
        }
        var combined = new ArrayList<>(violations);
        combined.addAll(requiredMealViolations);
        return new NutritionAuditValidationResult(false, combined,
                consolidatedFeedback + "\nRequired meal checks: " + requiredMealViolations);
    }

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}
}