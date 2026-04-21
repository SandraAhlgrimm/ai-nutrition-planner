package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;

public record NutritionAuditValidationResult(boolean allPassed, List<NutritionAuditRecipeViolation> violations,
                                      String consolidatedFeedback) implements TypedKey<NutritionAuditValidationResult> {

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}

    public NutritionAuditValidationResult() {
        this(false, Collections.emptyList(), "");
    }

    // Required to make it an optional parameter for the Agents:createWeeklyPlan method
    @Override
    public NutritionAuditValidationResult defaultValue() {
        return new NutritionAuditValidationResult();
    }
}