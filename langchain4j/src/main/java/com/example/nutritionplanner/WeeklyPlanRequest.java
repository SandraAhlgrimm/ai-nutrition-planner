package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;

public record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode, String additionalInstructions) implements TypedKey<WeeklyPlanRequest> {

    public WeeklyPlanRequest() {
        this(Collections.emptyList(), "", "");
    }

    record DayPlanRequest(DayOfWeek day, List<MealType> meals) {}

    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
