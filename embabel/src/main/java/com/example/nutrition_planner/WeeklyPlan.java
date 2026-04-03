package com.example.nutrition_planner;

import com.embabel.agent.api.annotation.LlmTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @LlmTool(description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var dailyNutritionTotals = days.stream().collect(Collectors.toMap(
                DailyPlan::day, day -> new NutritionInfo(
                        Stream.of(day.breakfast(), day.lunch(), day.dinner())
                                .flatMap(Optional::stream)
                                .collect(Collectors.toList())
                )
        ));

        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    record DailyPlan(DayOfWeek day, Optional<Recipe> breakfast, Optional<Recipe> lunch, Optional<Recipe> dinner) {}
}

