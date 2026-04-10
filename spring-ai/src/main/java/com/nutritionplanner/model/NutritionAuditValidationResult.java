package com.nutritionplanner.model;

import java.time.DayOfWeek;
import java.util.List;

public record NutritionAuditValidationResult(boolean allPassed,
                                              List<NutritionAuditRecipeViolation> violations,
                                              String summary) {

    public record NutritionAuditRecipeViolation(DayOfWeek day, String recipeName,
                                                 String violation, String suggestion) {}
}
