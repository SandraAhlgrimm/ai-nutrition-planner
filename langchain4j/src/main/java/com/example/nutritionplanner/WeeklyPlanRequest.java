package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record WeeklyPlanRequest(Map<DayOfWeek, Set<MealType>> meals, String countryCode, String additionalInstructions)
        implements TypedKey<WeeklyPlanRequest> {

    public WeeklyPlanRequest() {
        this(new EnumMap<>(DayOfWeek.class), "DE", "");
    }
    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
