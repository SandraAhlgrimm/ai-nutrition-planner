package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

final class NutritionTestData {

    static final UserProfile ALICE = new UserProfile("alice", List.of("vegetarian"),
            List.of("weight-loss", "improve-energy"), 1800, List.of("nuts"), List.of("cilantro", "olives"));
    static final SeasonalIngredients SEASONAL = new SeasonalIngredients(List.of(new Recipe.Ingredient("spinach", "200", "g")));
    static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(List.of(
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                    List.of(WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER)),
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.WEDNESDAY,
                    List.of(WeeklyPlanRequest.MealType.BREAKFAST))), "DE", "Under 30 minutes; no soy.");
    static final NutritionAuditValidationResult PASS =
            new NutritionAuditValidationResult(true, List.of(), "All checks passed");
    static final NutritionAuditValidationResult FAIL = new NutritionAuditValidationResult(false, List.of(
            new NutritionAuditValidationResult.NutritionAuditRecipeViolation(DayOfWeek.MONDAY, "Lentil bowl",
                    "Contains an allergen", "Replace nuts with seeds")), "Remove nuts from every recipe");

    static WeeklyPlan plan(String name) {
        var recipe = new Recipe(name, List.of(new Recipe.Ingredient("lentils", "100", "g")),
                new NutritionInfo(500, 30, 60, 15, 300), "Cook lentils and serve.", 20);
        return new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, Optional.empty(), Optional.of(recipe), Optional.of(recipe)),
                new WeeklyPlan.DailyPlan(DayOfWeek.WEDNESDAY, Optional.of(recipe), Optional.empty(), Optional.empty())));
    }

    private NutritionTestData() {}
}
