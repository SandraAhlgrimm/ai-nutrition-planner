package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

record WeeklyPlanRequest(Map<DayOfWeek, Set<MealType>> meals, String countryCode, String additionalInstructions) {

    WeeklyPlanRequest() {
        this(new EnumMap<>(DayOfWeek.class), "DE", "");
    }
    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
