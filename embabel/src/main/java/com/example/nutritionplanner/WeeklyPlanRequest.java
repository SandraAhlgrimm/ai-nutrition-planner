package com.example.nutritionplanner;

import java.time.DayOfWeek;
import java.util.Collection;
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
        BREAKFAST(25),
        LUNCH(35),
        DINNER(40);

        private final int caloriePercent;

        MealType(int caloriePercent) {
            this.caloriePercent = caloriePercent;
        }

        public int caloriePercent() {
            return caloriePercent;
        }

        /**
         * Share of the daily calorie target that the given meal slots together should cover.
         * A day with all three slots sums to 100, so full days keep the original target.
         */
        public static int caloriePercentOf(Collection<MealType> meals) {
            return meals.stream().mapToInt(MealType::caloriePercent).sum();
        }
    }
}
