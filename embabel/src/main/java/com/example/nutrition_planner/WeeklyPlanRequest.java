package com.example.nutrition_planner;

import java.time.DayOfWeek;
import java.util.List;

record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode) {
    record DayPlanRequest(DayOfWeek day, List<MealType> meals) {}

    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
