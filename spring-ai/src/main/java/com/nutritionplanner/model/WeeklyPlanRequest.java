package com.nutritionplanner.model;

import java.time.DayOfWeek;
import java.util.List;

public record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode, String additionalInstructions) {

    public record DayPlanRequest(DayOfWeek day, List<MealType> meals) {}

    public enum MealType { BREAKFAST, LUNCH, DINNER }
}
