package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.nutritionplanner.NutritionAuditValidationResult.NutritionAuditRecipeViolation;
import static com.example.nutritionplanner.WeeklyPlanRequest.MealType;

public class RequestShapeAudit {

    @Agent(description = "Checks requested days, meals and daily calories and merges findings with the model audit",
            typedOutputKey = NutritionAuditValidationResult.class)
    public NutritionAuditValidationResult checkRequestShape(
            @K(WeeklyPlan.class) WeeklyPlan plan,
            @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
            @K(UserProfile.class) UserProfile userProfile,
            @K(NutritionAuditValidationResult.class) NutritionAuditValidationResult modelAudit) {
        var requestedMeals = new EnumMap<DayOfWeek, EnumSet<MealType>>(DayOfWeek.class);
        for (var day : request.days()) {
            requestedMeals.computeIfAbsent(day.day(), ignored -> EnumSet.noneOf(MealType.class))
                    .addAll(day.meals());
        }

        var shapeViolations = new ArrayList<NutritionAuditRecipeViolation>();
        var seenDays = EnumSet.noneOf(DayOfWeek.class);
        for (var day : plan.days()) {
            if (!seenDays.add(day.day())) {
                shapeViolations.add(new NutritionAuditRecipeViolation(day.day(), "Day schedule",
                        "Duplicate day: " + day.day() + ".", "Return exactly one entry for " + day.day() + "."));
            }
            var meals = requestedMeals.get(day.day());
            if (meals == null) {
                shapeViolations.add(new NutritionAuditRecipeViolation(day.day(), "Day schedule",
                        "Unrequested day: " + day.day() + ".", "Remove " + day.day() + " from the plan."));
                continue;
            }
            for (var meal : MealType.values()) {
                boolean present = switch (meal) {
                    case BREAKFAST -> day.breakfast() != null;
                    case LUNCH -> day.lunch() != null;
                    case DINNER -> day.dinner() != null;
                };
                var slot = day.day() + " " + meal;
                if (meals.contains(meal) && !present) {
                    shapeViolations.add(new NutritionAuditRecipeViolation(day.day(), slot,
                            "Missing requested meal: " + slot + ".", "Include a recipe for " + slot + "."));
                } else if (!meals.contains(meal) && present) {
                    shapeViolations.add(new NutritionAuditRecipeViolation(day.day(), slot,
                            "Unrequested meal: " + slot + ".", "Leave " + slot + " null."));
                }
            }
        }
        for (var day : requestedMeals.keySet()) {
            if (!seenDays.contains(day)) {
                shapeViolations.add(new NutritionAuditRecipeViolation(day, "Day schedule",
                        "Missing requested day: " + day + ".", "Include " + day + " with its requested meals."));
            }
        }
        var violations = new ArrayList<>(modelAudit.violations());
        violations.addAll(shapeViolations);
        var shapeFeedback = shapeViolations.stream()
                .map(violation -> violation.explanation() + " " + violation.suggestedFix())
                .collect(Collectors.joining("\n"));

        var calorieViolations = new ArrayList<NutritionAuditRecipeViolation>();
        if (shapeViolations.isEmpty()) {
            long target = userProfile.dailyCalorieTarget();
            long minimum = (target * 90 + 99) / 100;
            long maximum = target * 110 / 100;
            for (var requestedDay : request.days()) {
                var matchingDays = plan.days().stream()
                        .filter(day -> day.day() == requestedDay.day())
                        .toList();
                var recipes = matchingDays.stream().flatMap(RequestShapeAudit::meals).toList();
                if (recipes.stream().anyMatch(recipe -> recipe.nutrition() == null)) {
                    calorieViolations.add(new NutritionAuditRecipeViolation(requestedDay.day(), "Daily calories",
                            "Cannot verify the daily calorie total for " + requestedDay.day()
                                    + " because a recipe is missing nutrition information.",
                            "Provide nutrition information for every recipe on " + requestedDay.day() + "."));
                    continue;
                }
                long calories = recipes.stream().mapToLong(recipe -> recipe.nutrition().calories()).sum();
                if (calories < minimum || calories > maximum) {
                    calorieViolations.add(new NutritionAuditRecipeViolation(requestedDay.day(), "Daily calories",
                            requestedDay.day() + " has " + calories + " kcal; the daily target is " + target
                                    + " kcal, so the accepted range is " + minimum + "–" + maximum + " kcal.",
                            "Revise " + requestedDay.day() + " to total between " + minimum + " and " + maximum
                                    + " kcal without changing another day's total."));
                }
            }
        }

        violations.addAll(calorieViolations);
        var calorieFeedback = calorieViolations.stream()
                .map(violation -> violation.explanation() + " " + violation.suggestedFix())
                .collect(Collectors.joining("\n"));
        var feedbackParts = new ArrayList<String>();
        if (!modelAudit.consolidatedFeedback().isBlank()) {
            feedbackParts.add(modelAudit.consolidatedFeedback());
        }
        if (!shapeFeedback.isBlank()) {
            feedbackParts.add(shapeFeedback);
        }
        if (!calorieFeedback.isBlank()) {
            feedbackParts.add(calorieFeedback);
        }
        if (violations.size() == modelAudit.violations().size()) {
            return modelAudit;
        }
        return new NutritionAuditValidationResult(false, violations, String.join("\n", feedbackParts));
    }

    private static Stream<Recipe> meals(WeeklyPlan.DailyPlan day) {
        return Stream.of(day.breakfast(), day.lunch(), day.dinner()).filter(Objects::nonNull);
    }
}
