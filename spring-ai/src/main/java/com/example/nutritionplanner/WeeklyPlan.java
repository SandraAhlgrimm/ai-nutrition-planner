package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var dailyNutritionTotals = days.stream().collect(Collectors.toMap(
                DailyPlan::day, day -> nutritionTotalsForDay(day.day())
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @Tool(description = "Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var nutritionInfo = days.stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .map(d -> new NutritionInfo(
                        Stream.of(d.breakfast(), d.lunch(), d.dinner())
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                ))
                .orElse(new NutritionInfo(List.of()));
        log.info("WeeklyPlan:nutritionTotalsForDay tool method finished with {} for {}", nutritionInfo, day);
        return nutritionInfo;
    }

    @Tool(description = "Returns the total number of meals across all days of the weekly meal plan")
    public long totalMealCount() {
        var count = days.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .filter(Objects::nonNull)
                .count();
        log.info("WeeklyPlan:totalMealCount tool method finished with {}", count);
        return count;
    }

    public record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {}
}
