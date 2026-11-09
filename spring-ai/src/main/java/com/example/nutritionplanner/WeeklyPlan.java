package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var totals = days.stream().collect(toMap(DailyPlan::day, DailyPlan::nutritionTotals));
        log.info("Tool dailyNutritionTotals: {}", totals);
        return totals;
    }

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var totals = days.stream().filter(d -> d.day() == day).findFirst()
                .map(DailyPlan::nutritionTotals).orElse(NutritionInfo.ZERO);
        log.info("Tool nutritionTotalsForDay({}): {}", day, totals);
        return totals;
    }

    @Tool(description = "Returns the total number of meals across all days of the weekly meal plan")
    public long totalMealCount() {
        var count = days.stream().mapToLong(d -> d.meals().size()).sum();
        log.info("Tool totalMealCount: {}", count);
        return count;
    }

    public record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {

        List<Recipe> meals() {
            return Stream.of(breakfast, lunch, dinner).filter(Objects::nonNull).toList();
        }

        NutritionInfo nutritionTotals() {
            return meals().stream().map(Recipe::nutrition).filter(Objects::nonNull).reduce(NutritionInfo.ZERO, NutritionInfo::plus);
        }
    }
}
