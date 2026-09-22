package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.List;

final class PlanFixtures {

    static final UserProfile ALICE = new UserProfile("alice", List.of("vegetarian"),
            List.of("weight-loss", "improve-energy"), 1800, List.of("nuts"), List.of("cilantro", "olives"));
    static final UserProfile BOB = new UserProfile("bob", List.of("vegan"),
            List.of("maintain-energy"), 2100, List.of("soy"), List.of("mushrooms"));
    static final UserProfileProperties PROFILES = new UserProfileProperties(List.of(ALICE, BOB));
    static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(List.of(
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, List.of(WeeklyPlanRequest.MealType.DINNER)),
            new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.THURSDAY, List.of(WeeklyPlanRequest.MealType.LUNCH))),
            "DE", "Keep all recipes under 30 minutes.");
    static final NutritionAuditValidationResult PASSED = new NutritionAuditValidationResult(true, List.of(), "");

    static WeeklyPlan plan(int candidate) {
        var recipe = new Recipe("candidate-" + candidate, List.of(new Recipe.Ingredient("carrot", "200", "g")),
                new NutritionInfo(100 * candidate, 10, 20, 5, 30), "Roast the carrots.", 20);
        return new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, null, null, recipe),
                new WeeklyPlan.DailyPlan(DayOfWeek.THURSDAY, null, recipe, null)));
    }

    static NutritionAuditValidationResult failed(int candidate) {
        return new NutritionAuditValidationResult(false, List.of(
                new NutritionAuditValidationResult.NutritionAuditRecipeViolation(DayOfWeek.MONDAY,
                        "candidate-" + candidate, "A dietary requirement is not met.", "Revise the recipe.")),
                "Feedback for candidate-" + candidate);
    }

    private PlanFixtures() {}
}
