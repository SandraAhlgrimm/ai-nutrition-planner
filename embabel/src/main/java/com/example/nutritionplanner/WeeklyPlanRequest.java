package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record WeeklyPlanRequest(List<DayPlanRequest> days, String countryCode, String additionalInstructions) {

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    public WeeklyPlanRequest {
        days = List.copyOf(days);
        if (days.isEmpty() || days.stream().map(DayPlanRequest::day).distinct().count() != days.size()) {
            throw new IllegalArgumentException("Select at least one day, without duplicate days");
        }
        Objects.requireNonNull(countryCode, "Country code is required");
        countryCode = countryCode.toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODES.contains(countryCode)) {
            throw new IllegalArgumentException("Country code must be a valid two-letter ISO country code");
        }
        additionalInstructions = additionalInstructions == null ? "" : additionalInstructions;
    }

    public record DayPlanRequest(DayOfWeek day, List<MealType> meals) {
        public DayPlanRequest {
            Objects.requireNonNull(day, "Day is required");
            meals = List.copyOf(meals);
            if (meals.isEmpty() || meals.stream().distinct().count() != meals.size()) {
                throw new IllegalArgumentException("Select at least one meal per day, without duplicate meals");
            }
        }
    }

    public enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER
    }
}
