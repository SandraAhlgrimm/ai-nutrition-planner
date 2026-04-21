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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record WeeklyPlan(List<DailyPlan> days) implements TypedKey<WeeklyPlan> {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    public WeeklyPlan() {
        this(Collections.emptyList());
    }

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public static Map<DayOfWeek, NutritionInfo> dailyNutritionTotals(List<DailyPlan> days) {
        var dailyNutritionTotals = days.stream().collect(Collectors.toMap(
                DailyPlan::day, dailyPlan -> nutritionTotalsForDay(dailyPlan.day(), days)
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @Tool("Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public static NutritionInfo nutritionTotalsForDay(DayOfWeek day, List<DailyPlan> days) {
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

    @Tool("Returns the total number of meals across all days of the weekly meal plan")
    public static long totalMealCount( List<DailyPlan> days) {
        var count = days.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .filter(Objects::nonNull)
                .count();
        log.info("WeeklyPlan:totalMealCount tool method finished with {}", count);
        return count;
    }

    // Required to make it an optional parameter for the Agents:createWeeklyPlan method
    @Override
    public WeeklyPlan defaultValue() {
        return new WeeklyPlan();
    }

    record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {}
}
