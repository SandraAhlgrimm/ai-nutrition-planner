package com.example.nutritionplanner;

import com.embabel.common.ai.converters.JacksonOutputConverter;
import com.embabel.common.ai.converters.RequiredFieldNormalization;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void duplicateDayCandidatesRetainAllNutritionAndAreRejectedByTheAudit() {
        var day = NutritionTestData.plan("Lentils").days().getFirst();
        var plan = new WeeklyPlan(List.of(day, day));

        assertEquals(2000, plan.dailyNutritionTotals().get(DayOfWeek.MONDAY).calories());
        assertEquals(2000, plan.nutritionTotalsForDay(DayOfWeek.MONDAY).calories());
        var audit = NutritionTestData.PASS.withRequiredMealChecks(plan, NutritionTestData.REQUEST);
        assertFalse(audit.allPassed());
        assertTrue(audit.violations().stream().anyMatch(violation -> violation.explanation().contains("Duplicate day")));
    }

    @Test
    void nativeOutputSchemaAndPublicJsonPreserveOptionalMealContract() {
        var mapper = JsonMapper.builder().build();
        var converter = new JacksonOutputConverter<>(WeeklyPlan.class, mapper, RequiredFieldNormalization.ENABLED);
        var schema = converter.getJsonSchema();
        assertTrue(schema.contains("\"breakfast\"") && schema.contains("\"lunch\"") && schema.contains("\"dinner\""));
        assertFalse(schema.contains("breakfastRecipe"));

        var lunch = recipe(500, 25, 60, 12, 400);
        var plan = converter.convert("""
                {"days":[{"day":"MONDAY","lunch":%s}]}
                """.formatted(mapper.writeValueAsString(lunch)));
        var day = plan.days().getFirst();
        assertEquals(Optional.empty(), day.breakfast());
        assertEquals(Optional.of(lunch), day.lunch());
        assertEquals(Optional.empty(), day.dinner());
        var json = mapper.readTree(mapper.writeValueAsString(plan)).path("days").get(0);
        assertEquals(java.util.Set.of("day", "breakfast", "lunch", "dinner"), json.propertyNames());
        assertTrue(json.path("breakfast").isNull());
        assertTrue(json.path("dinner").isNull());
        assertEquals(plan, mapper.readValue(mapper.writeValueAsString(plan), WeeklyPlan.class));
    }
}
