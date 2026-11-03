package com.example.nutritionplanner;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.UnfoldingTools;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Category-based progressive disclosure: invoking the facade reveals nutrition totals
 * or meal counts. This is not semantic tool search. Unlike this facade, the skill's
 * {@code LlmReference} contributes both context and ordinary activation/script tools.
 */
@UnfoldingTools(name = "weekly_meal_plan_tools", description = "Weekly meal plan tools. Pass category: 'nutrition' for per-day nutrition totals, or 'meal' for the meal count")
public record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    public WeeklyPlan {
        days = List.copyOf(days);
    }

    @LlmTool(category = "nutrition", description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var dailyNutritionTotals = days.stream().map(DailyPlan::day).distinct().collect(Collectors.toMap(
                day -> day, this::nutritionTotalsForDay
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @LlmTool(category = "nutrition", description = "Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var matchingDays = days.stream().filter(d -> d.day() == day).toList();
        if (matchingDays.isEmpty()) {
            throw new IllegalArgumentException("Day is not present in the plan: " + day);
        }
        var nutritionInfo = new NutritionInfo(matchingDays.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .flatMap(Optional::stream).toList());
        log.info("WeeklyPlan:nutritionTotalsForDay tool method finished with {} for {}", nutritionInfo, day);
        return nutritionInfo;
    }

    @LlmTool(category = "meal", description = "Returns the total number of meals across all days of the weekly meal plan")
    public long totalMealCount() {
        var count = days.stream()
                .flatMap(d -> Stream.of(d.breakfast(), d.lunch(), d.dinner()))
                .filter(Optional::isPresent)
                .count();
        log.info("WeeklyPlan:totalMealCount tool method finished with {}", count);
        return count;
    }

    List<NutritionAuditValidationResult.NutritionAuditRecipeViolation> requiredMealViolations(WeeklyPlanRequest request) {
        var violations = new ArrayList<NutritionAuditValidationResult.NutritionAuditRecipeViolation>();
        var requested = request.days().stream().collect(Collectors.toMap(
                WeeklyPlanRequest.DayPlanRequest::day, WeeklyPlanRequest.DayPlanRequest::meals));
        for (var requestedDay : request.days()) {
            for (var meal : requestedDay.meals()) {
                if (days.stream().filter(day -> day.day() == requestedDay.day())
                        .noneMatch(day -> day.meal(meal).isPresent())) {
                    violations.add(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                            requestedDay.day(), meal.name(), "Requested meal is missing", "Provide the requested recipe"));
                }
            }
        }
        var seenDays = EnumSet.noneOf(DayOfWeek.class);
        for (var day : days) {
            if (!seenDays.add(day.day())) {
                violations.add(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                        day.day(), "", "Duplicate day", "Return exactly one entry for each requested day"));
            }
            if (!requested.containsKey(day.day())) {
                violations.add(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                        day.day(), "", "Unrequested day", "Remove this day"));
            }
            for (var meal : WeeklyPlanRequest.MealType.values()) {
                day.meal(meal).ifPresent(recipe -> {
                    if (requested.containsKey(day.day()) && !requested.get(day.day()).contains(meal)) {
                        violations.add(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                                day.day(), recipe.name(), "Unrequested meal", "Remove this meal"));
                    }
                    if (recipe.nutrition() == null) {
                        violations.add(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                                day.day(), recipe.name(), "Nutrition information is missing", "Provide complete nutrition information"));
                    }
                });
            }
        }
        return List.copyOf(violations);
    }

    // Nullable storage avoids Embabel 1.5.2's Optional-schema type-array normalization bug.
    // Preserve the JSON field names and the existing Optional-based Java accessors.
    public record DailyPlan(DayOfWeek day,
                            @JsonProperty("breakfast") @Nullable Recipe breakfastRecipe,
                            @JsonProperty("lunch") @Nullable Recipe lunchRecipe,
                            @JsonProperty("dinner") @Nullable Recipe dinnerRecipe) {
        public DailyPlan {
            Objects.requireNonNull(day, "Day is required");
        }

        public DailyPlan(DayOfWeek day, Optional<Recipe> breakfast, Optional<Recipe> lunch, Optional<Recipe> dinner) {
            this(day, breakfast.orElse(null), lunch.orElse(null), dinner.orElse(null));
        }

        public Optional<Recipe> breakfast() {
            return Optional.ofNullable(breakfastRecipe);
        }

        public Optional<Recipe> lunch() {
            return Optional.ofNullable(lunchRecipe);
        }

        public Optional<Recipe> dinner() {
            return Optional.ofNullable(dinnerRecipe);
        }

        Optional<Recipe> meal(WeeklyPlanRequest.MealType meal) {
            return switch (meal) {
                case BREAKFAST -> breakfast();
                case LUNCH -> lunch();
                case DINNER -> dinner();
            };
        }
    }
}
