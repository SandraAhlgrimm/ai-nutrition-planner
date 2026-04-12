package com.nutritionplanner.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyPlanTest {

    static Recipe recipe(int calories, double protein, double carbs, double fat, int sodium) {
        return new Recipe("Test Recipe", List.of(), new NutritionInfo(calories, protein, carbs, fat, sodium),
                "Test instructions", 20);
    }

    @Test
    void dailyNutritionTotals_sumsAllThreeMeals() {
        var plan = new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                        recipe(400, 20, 50, 10, 300),
                        recipe(600, 30, 80, 15, 500),
                        recipe(700, 35, 70, 20, 600))
        ));

        var totals = plan.dailyNutritionTotals();
        var monday = totals.get(DayOfWeek.MONDAY);

        assertThat(monday.calories()).isEqualTo(1700);
        assertThat(monday.proteinGrams()).isEqualTo(85.0);
        assertThat(monday.carbGrams()).isEqualTo(200.0);
        assertThat(monday.fatGrams()).isEqualTo(45.0);
        assertThat(monday.sodiumMg()).isEqualTo(1400);
    }

    @Test
    void dailyNutritionTotals_skipsEmptyMeals() {
        var plan = new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY,
                        null,
                        recipe(600, 30, 80, 15, 500),
                        recipe(700, 35, 70, 20, 600))
        ));

        var totals = plan.dailyNutritionTotals();
        var tuesday = totals.get(DayOfWeek.TUESDAY);

        assertThat(tuesday.calories()).isEqualTo(1300);
    }

    @Test
    void dailyNutritionTotals_producesEntryPerDay() {
        var plan = new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                        recipe(400, 20, 50, 10, 300),
                        null, null),
                new WeeklyPlan.DailyPlan(DayOfWeek.WEDNESDAY,
                        null,
                        recipe(600, 30, 80, 15, 500),
                        null)
        ));

        var totals = plan.dailyNutritionTotals();
        assertThat(totals).hasSize(2);
        assertThat(totals).containsKey(DayOfWeek.MONDAY);
        assertThat(totals).containsKey(DayOfWeek.WEDNESDAY);
    }

    @Test
    void totalMealCount_countsOnlyPresentMeals() {
        var plan = new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                        recipe(400, 20, 50, 10, 300),
                        null,
                        recipe(700, 35, 70, 20, 600)),
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY,
                        null, null,
                        recipe(500, 25, 60, 12, 400))
        ));

        assertThat(plan.totalMealCount()).isEqualTo(3);
    }
}
