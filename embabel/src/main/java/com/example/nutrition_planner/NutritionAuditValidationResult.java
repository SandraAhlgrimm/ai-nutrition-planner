package com.example.nutrition_planner;

import java.time.DayOfWeek;
import java.util.List;

record NutritionAuditValidationResult(boolean allPassed, List<NutritionAuditRecipeViolation> violations,
                                      String consolidatedFeedback) {

    record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix) {}
}