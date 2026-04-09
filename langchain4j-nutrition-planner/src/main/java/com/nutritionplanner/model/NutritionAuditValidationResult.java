package com.nutritionplanner.model;

import java.time.DayOfWeek;
import java.util.List;

public record NutritionAuditValidationResult(boolean allPassed,
                                              List<NutritionAuditRecipeViolation> violations,
                                              String consolidatedFeedback) {

    public record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName,
                                                 String explanation, String suggestedFix) {}
}
