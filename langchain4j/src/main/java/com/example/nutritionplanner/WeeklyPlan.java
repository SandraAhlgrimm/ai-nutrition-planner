package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.declarative.TypedKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public record WeeklyPlan(List<DailyPlan> days) implements TypedKey<WeeklyPlan> {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    public WeeklyPlan() {
        this(Collections.emptyList());
    }

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public static Map<DayOfWeek, NutritionInfo> dailyNutritionTotals(List<DailyPlan> days) {
        var totals = days.stream().collect(toMap(DailyPlan::day, DailyPlan::nutritionTotals));
        log.info("Tool dailyNutritionTotals: {}", totals);
        return totals;
    }

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public static NutritionInfo nutritionTotalsForDay(DayOfWeek day, List<DailyPlan> days) {
        var totals = days.stream().filter(d -> d.day() == day).findFirst()
                .map(DailyPlan::nutritionTotals).orElse(NutritionInfo.ZERO);
        log.info("Tool nutritionTotalsForDay({}): {}", day, totals);
        return totals;
    }

    @Tool("Returns the total number of meals across all days of the weekly meal plan")
    public static long totalMealCount(List<DailyPlan> days) {
        var count = days.stream().mapToLong(d -> d.meals().size()).sum();
        log.info("Tool totalMealCount: {}", count);
        return count;
    }

    // Required to make it an optional parameter for the Agents:createWeeklyPlan method
    @Override
    public WeeklyPlan defaultValue() {
        return new WeeklyPlan();
    }

    record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {

        List<Recipe> meals() {
            return Stream.of(breakfast, lunch, dinner).filter(Objects::nonNull).toList();
        }

        NutritionInfo nutritionTotals() {
            return meals().stream().map(Recipe::nutrition).filter(Objects::nonNull).reduce(NutritionInfo.ZERO, NutritionInfo::plus);
        }
    }
}
