package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.stream.Collectors;

import static com.example.nutritionplanner.NutritionAuditValidationResult.NutritionAuditRecipeViolation;
import static com.example.nutritionplanner.WeeklyPlanRequest.MealType;

public class RequestShapeAudit {

    @Agent(description = "Checks exact requested days and meals and merges findings with the model audit",
            typedOutputKey = NutritionAuditValidationResult.class)
    public NutritionAuditValidationResult checkRequestShape(
            @K(WeeklyPlan.class) WeeklyPlan plan,
            @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
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
        if (shapeViolations.isEmpty()) {
            return modelAudit;
        }

        var violations = new ArrayList<>(modelAudit.violations());
        violations.addAll(shapeViolations);
        var shapeFeedback = shapeViolations.stream()
                .map(violation -> violation.explanation() + " " + violation.suggestedFix())
                .collect(Collectors.joining("\n"));
        var feedback = modelAudit.consolidatedFeedback().isBlank() ? shapeFeedback
                : modelAudit.consolidatedFeedback() + "\n" + shapeFeedback;
        return new NutritionAuditValidationResult(false, violations, feedback);
    }
}
