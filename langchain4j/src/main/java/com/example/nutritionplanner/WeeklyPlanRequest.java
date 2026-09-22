package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode, String additionalInstructions) implements TypedKey<WeeklyPlanRequest> {

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    public WeeklyPlanRequest {
        days = days == null ? null : Collections.unmodifiableList(new ArrayList<>(days));
        additionalInstructions = additionalInstructions == null ? "" : additionalInstructions;
    }

    public WeeklyPlanRequest() {
        this(Collections.emptyList(), "", "");
    }

    public void validate() {
        if (days == null || days.isEmpty()) {
            throw new InvalidPlanRequestException("Select at least one day");
        }
        if (countryCode == null || !COUNTRY_CODES.contains(countryCode)) {
            throw new InvalidPlanRequestException("Country code must be an uppercase ISO 3166-1 alpha-2 code");
        }
        var seenDays = EnumSet.noneOf(DayOfWeek.class);
        for (var day : days) {
            if (day == null || day.day() == null || day.meals() == null || day.meals().isEmpty()) {
                throw new InvalidPlanRequestException("Each selected day must have a day and at least one meal");
            }
            if (!seenDays.add(day.day())) {
                throw new InvalidPlanRequestException("Requested days must be unique");
            }
            var seenMeals = EnumSet.noneOf(MealType.class);
            for (var meal : day.meals()) {
                if (meal == null || !seenMeals.add(meal)) {
                    throw new InvalidPlanRequestException("Requested meals must be non-null and unique per day");
                }
            }
        }
    }

    record DayPlanRequest(DayOfWeek day, List<MealType> meals) {
        DayPlanRequest {
            meals = meals == null ? null : Collections.unmodifiableList(new ArrayList<>(meals));
        }
    }

    enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
