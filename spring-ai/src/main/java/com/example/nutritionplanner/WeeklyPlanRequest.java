package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record WeeklyPlanRequest(Map<DayOfWeek, Set<MealType>> meals, String countryCode, String additionalInstructions) {

    WeeklyPlanRequest {
        if (meals != null) {
            var copy = new EnumMap<DayOfWeek, Set<MealType>>(DayOfWeek.class);
            meals.forEach((day, selected) -> {
                if (day == null || selected == null || selected.stream().anyMatch(java.util.Objects::isNull)) {
                    throw new InvalidPlanRequestException("Each day must include a day and meals");
                }
                copy.put(day, Set.copyOf(selected));
            });
            meals = Map.copyOf(copy);
        }
        additionalInstructions = additionalInstructions == null ? "" : additionalInstructions;
    }

    WeeklyPlanRequest() {
        this(new EnumMap<>(DayOfWeek.class), "DE", "");
    }

    void validate() {
        if (meals == null || meals.isEmpty() || meals.values().stream().anyMatch(Set::isEmpty)) {
            throw new InvalidPlanRequestException("Select at least one day and one meal per selected day");
        }
        if (countryCode == null || !Set.of(Locale.getISOCountries()).contains(countryCode)) {
            throw new InvalidPlanRequestException("countryCode must be an uppercase ISO 3166-1 alpha-2 code");
        }
    }

    String shapeFeedback(WeeklyPlan plan) {
        var actual = new EnumMap<DayOfWeek, Set<MealType>>(DayOfWeek.class);
        for (var day : plan.days()) {
            var selected = new HashSet<MealType>();
            if (day.breakfast() != null) selected.add(MealType.BREAKFAST);
            if (day.lunch() != null) selected.add(MealType.LUNCH);
            if (day.dinner() != null) selected.add(MealType.DINNER);
            if (actual.put(day.day(), selected) != null) {
                return "Each requested day must appear exactly once";
            }
        }
        return actual.equals(meals) ? "" : "Include exactly the requested days and meals: " + meals;
    }

    static WeeklyPlanRequest fromDays(List<DayPlanRequest> days, String countryCode, String additionalInstructions) {
        if (days == null) throw new InvalidPlanRequestException("days is required");
        var meals = new EnumMap<DayOfWeek, Set<MealType>>(DayOfWeek.class);
        for (var day : days) {
            if (day == null || day.day() == null || day.meals() == null
                    || day.meals().stream().anyMatch(java.util.Objects::isNull)) {
                throw new InvalidPlanRequestException("Each day must include a day and meals");
            }
            var selected = Set.copyOf(day.meals());
            if (selected.size() != day.meals().size()) {
                throw new InvalidPlanRequestException("Requested meals must be unique per day");
            }
            if (meals.put(day.day(), selected) != null) {
                throw new InvalidPlanRequestException("Requested days must be unique");
            }
        }
        var request = new WeeklyPlanRequest(meals, countryCode, additionalInstructions);
        request.validate();
        return request;
    }

    record DayPlanRequest(DayOfWeek day, List<MealType> meals) {}

    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
