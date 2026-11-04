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

    NutritionAuditValidationResult withDeterministicChecks(WeeklyPlan plan, WeeklyPlanRequest request,
                                                          UserProfile profile) {
        var requiredMealViolations = plan.requiredMealViolations(request);
        var deterministicViolations = requiredMealViolations.isEmpty()
                ? plan.dailyCalorieViolations(request, profile) : requiredMealViolations;
        if (deterministicViolations.isEmpty()) {
            return this;
        }
        var combined = new ArrayList<>(violations);
        combined.addAll(deterministicViolations);
        return new NutritionAuditValidationResult(false, combined,
                consolidatedFeedback + "\nDeterministic plan checks: " + deterministicViolations);
    }

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}
}