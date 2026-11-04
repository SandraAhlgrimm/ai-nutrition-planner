package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

final class NutritionTestData {

    static final UserProfile ALICE = new UserProfile("alice", List.of("vegetarian"),
            List.of("weight-loss", "improve-energy"), 2500, List.of("nuts"), List.of("cilantro", "olives"));
    static final SeasonalIngredients SEASONAL = new SeasonalIngredients(List.of(new Recipe.Ingredient("spinach", "200", "g")));
    static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(List.of(
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                    List.of(WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER)),
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.WEDNESDAY,
                    List.of(WeeklyPlanRequest.MealType.BREAKFAST))), "DE", "Under 30 minutes; no soy.");
    static final NutritionAuditValidationResult PASS =
            new NutritionAuditValidationResult(true, List.of(), "All checks passed");

    /** MONDAY requests lunch and dinner, so it covers 35% + 40% = 75% of ALICE's 2500 kcal target. */
    static final int MONDAY_BUDGET = 1875;
    /** WEDNESDAY requests breakfast only, so it covers 25% of ALICE's 2500 kcal target. */
    static final int WEDNESDAY_BUDGET = 625;
    static final NutritionAuditValidationResult FAIL = new NutritionAuditValidationResult(false, List.of(
            new NutritionAuditValidationResult.NutritionAuditRecipeViolation(DayOfWeek.MONDAY, "Lentil bowl",
                    "Contains an allergen", "Replace nuts with seeds")), "Remove nuts from every recipe");

    static WeeklyPlan plan(String name) {
        return plan(name, MONDAY_BUDGET, WEDNESDAY_BUDGET);
    }

    static WeeklyPlan plan(String name, int mondayCalories, int wednesdayCalories) {
        return new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, Optional.empty(),
                        Optional.of(recipe(name, mondayCalories / 2)),
                        Optional.of(recipe(name, mondayCalories - mondayCalories / 2))),
                new WeeklyPlan.DailyPlan(DayOfWeek.WEDNESDAY, Optional.of(recipe(name, wednesdayCalories)),
                        Optional.empty(), Optional.empty())));
    }

    private static Recipe recipe(String name, int calories) {
        return new Recipe(name, List.of(new Recipe.Ingredient("lentils", "100", "g")),
                new NutritionInfo(calories, 30, 60, 15, 300), "Cook lentils and serve.", 20);
    }

    private NutritionTestData() {}
}
