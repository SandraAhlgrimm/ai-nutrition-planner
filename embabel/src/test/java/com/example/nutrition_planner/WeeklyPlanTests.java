package com.example.nutrition_planner;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeklyPlanTests {

    private static Recipe recipe(int calories, double protein, double carbs, double fat, int sodium) {
        return new Recipe("Test Recipe", List.of(),
                new NutritionInfo(calories, protein, carbs, fat, sodium), "", 0);
    }

    @Test
    void dailyNutritionTotals_sumsAllThreeMeals() {
        var breakfast = recipe(400, 20, 50, 10, 300);
        var lunch    = recipe(600, 30, 70, 15, 500);
        var dinner   = recipe(700, 35, 80, 20, 600);

        var plan = new WeeklyPlan(
                List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                        Optional.of(breakfast), Optional.of(lunch), Optional.of(dinner))));

        var totals = plan.dailyNutritionTotals();

        assertEquals(1, totals.size());
        var monday = totals.get(DayOfWeek.MONDAY);
        assertEquals(1700,  monday.calories());
        assertEquals(85.0,  monday.proteinGrams());
        assertEquals(200.0, monday.carbGrams());
        assertEquals(45.0,  monday.fatGrams());
        assertEquals(1400,  monday.sodiumMg());
    }

    @Test
    void dailyNutritionTotals_skipsEmptyMeals() {
        var lunch  = recipe(600, 30, 70, 15, 500);
        var dinner = recipe(700, 35, 80, 20, 600);

        var plan = new WeeklyPlan(
                List.of(new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY,
                        Optional.empty(), Optional.of(lunch), Optional.of(dinner))));

        var totals = plan.dailyNutritionTotals();

        var tuesday = totals.get(DayOfWeek.TUESDAY);
        assertEquals(1300,  tuesday.calories());
        assertEquals(65.0,  tuesday.proteinGrams(), 0.01);
        assertEquals(150.0, tuesday.carbGrams(),    0.01);
        assertEquals(35.0,  tuesday.fatGrams(),     0.01);
        assertEquals(1100,  tuesday.sodiumMg());
    }

    @Test
    void dailyNutritionTotals_producesEntryPerDay() {
        var meal = recipe(500, 25, 60, 12, 400);

        var plan = new WeeklyPlan(
                List.of(
                        new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                                Optional.of(meal), Optional.empty(), Optional.empty()),
                        new WeeklyPlan.DailyPlan(DayOfWeek.WEDNESDAY,
                                Optional.empty(), Optional.of(meal), Optional.empty())));

        var totals = plan.dailyNutritionTotals();

        assertEquals(2, totals.size());
        assertEquals(500, totals.get(DayOfWeek.MONDAY).calories());
        assertEquals(500, totals.get(DayOfWeek.WEDNESDAY).calories());
    }
}
