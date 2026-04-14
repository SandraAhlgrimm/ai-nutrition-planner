package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record WeeklyPlan(List<DailyPlan> days) {

    WeeklyPlan() {
        this(Collections.emptyList());
    }

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var dailyNutritionTotals = days.stream().collect(Collectors.toMap(
                DailyPlan::day, day -> nutritionTotalsForDay(day.day())
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var nutritionInfo = days.stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .map(d -> new NutritionInfo(
                        Stream.of(d.breakfast(), d.lunch(), d.dinner())
                                .flatMap(Optional::stream)
                                .collect(Collectors.toList())
                ))
                .orElse(new NutritionInfo(List.of()));
        log.info("WeeklyPlan:nutritionTotalsForDay tool method finished with {} for {}", nutritionInfo, day);
        return nutritionInfo;
    }

    @Tool("Returns the total number of meals across all days of the weekly meal plan")
    public long totalMealCount() {
        var count = days.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .filter(Optional::isPresent)
                .count();
        log.info("WeeklyPlan:totalMealCount tool method finished with {}", count);
        return count;
    }

    record DailyPlan(DayOfWeek day, Optional<Recipe> breakfast, Optional<Recipe> lunch, Optional<Recipe> dinner) {}
}
