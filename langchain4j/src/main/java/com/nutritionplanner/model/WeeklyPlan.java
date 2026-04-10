package com.nutritionplanner.model;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record WeeklyPlan(List<DailyPlan> days) {

    public record DailyPlan(DayOfWeek day, Recipe breakfast, Recipe lunch, Recipe dinner) {}

    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        return days.stream().collect(Collectors.toMap(
                DailyPlan::day,
                day -> nutritionTotalsForDay(day.day())
        ));
    }

    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        return days.stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .map(d -> new NutritionInfo(
                        Stream.of(d.breakfast(), d.lunch(), d.dinner())
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                ))
                .orElse(new NutritionInfo(List.of()));
    }

    public long totalMealCount() {
        return days.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .filter(Objects::nonNull)
                .count();
    }
}
