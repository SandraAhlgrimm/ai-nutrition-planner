package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DailyCalorieAcceptanceTests {
    private static final UserProfile PROFILE = new UserProfile("alice", List.of(), List.of(), 2500, List.of(), List.of());

    @Test
    void acceptsInclusiveDailyCalorieBoundaries() {
        var result = validate(Map.of(DayOfWeek.MONDAY, 2250, DayOfWeek.TUESDAY, 2750));

        assertThat(result.allPassed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void rejectsDailyTotalsBelowOrAboveTheInclusiveRange() {
        var below = validate(Map.of(DayOfWeek.MONDAY, 2249));
        var above = validate(Map.of(DayOfWeek.MONDAY, 2751));

        assertThat(below.allPassed()).isFalse();
        assertThat(below.feedback()).contains("2249 kcal", "2250-2750 kcal");
        assertThat(above.allPassed()).isFalse();
        assertThat(above.feedback()).contains("2751 kcal", "2250-2750 kcal");
    }

    @Test
    void rejectsAnObviousUnderTargetDay() {
        var result = validate(Map.of(DayOfWeek.MONDAY, 190));

        assertThat(result.allPassed()).isFalse();
        assertThat(result.feedback()).contains("190 kcal", "MONDAY");
    }

    @Test
    void doesNotAllowOneDaysCaloriesToOffsetAnotherDaysTotal() {
        var result = validate(Map.of(DayOfWeek.MONDAY, 2000, DayOfWeek.TUESDAY, 3000));

        assertThat(result.allPassed()).isFalse();
        assertThat(result.violations()).extracting(
                NutritionAuditValidationResult.NutritionAuditRecipeViolation::dayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(result.feedback()).contains("Do not offset with another day's total");
    }

    private static NutritionAuditValidationResult validate(Map<DayOfWeek, Integer> calories) {
        var days = new ArrayList<WeeklyPlan.DailyPlan>();
        var requested = new EnumMap<DayOfWeek, Set<WeeklyPlanRequest.MealType>>(DayOfWeek.class);
        calories.forEach((day, total) -> {
            days.add(new WeeklyPlan.DailyPlan(day, null, recipe(total), null));
            requested.put(day, Set.of(WeeklyPlanRequest.MealType.LUNCH));
        });
        var plan = new WeeklyPlan(days);
        var request = new WeeklyPlanRequest(requested, "DE", "");
        var modelAudit = new NutritionAuditValidationResult(true, List.of(), "");

        return modelAudit.withDeterministicChecks(plan, request, PROFILE);
    }

    private static Recipe recipe(int calories) {
        return new Recipe("Test meal", List.of(), new NutritionInfo(calories, 30.0, 70.0, 15.0, 500),
                "Cook and serve.", 20);
    }
}
