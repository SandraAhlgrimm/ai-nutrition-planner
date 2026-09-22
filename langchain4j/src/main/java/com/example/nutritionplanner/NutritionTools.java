package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.scope.AgenticScope;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NutritionTools {

    @Tool("Returns total calories, protein, carbs, fat and sodium for each day of the current candidate plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals(AgenticScope scope) {
        return currentPlan(scope).days().stream().map(WeeklyPlan.DailyPlan::day).distinct()
                .collect(Collectors.toMap(day -> day, day -> nutritionTotalsForDay(day, scope)));
    }

    @Tool("Returns nutrition totals for the specified day of the current candidate plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day, AgenticScope scope) {
        var matchingDays = currentPlan(scope).days().stream()
                .filter(dailyPlan -> dailyPlan.day() == day)
                .toList();
        if (matchingDays.isEmpty()) {
            throw new IllegalArgumentException("No meals planned for " + day);
        }
        return totals(matchingDays, day);
    }

    @Tool("Returns the total number of meals in the current candidate plan")
    public long totalMealCount(AgenticScope scope) {
        return currentPlan(scope).days().stream().flatMap(NutritionTools::meals).count();
    }

    private static WeeklyPlan currentPlan(AgenticScope scope) {
        // AgenticScope is injected by LangChain4j and is not exposed as an LLM tool argument.
        return Objects.requireNonNull(scope.readState(WeeklyPlan.class), "No current plan available for nutrition tools");
    }

    private static Stream<Recipe> meals(WeeklyPlan.DailyPlan day) {
        return Stream.of(day.breakfast(), day.lunch(), day.dinner()).filter(Objects::nonNull);
    }

    private static NutritionInfo totals(List<WeeklyPlan.DailyPlan> days, DayOfWeek day) {
        List<Recipe> recipes = days.stream().flatMap(NutritionTools::meals).toList();
        if (recipes.stream().anyMatch(recipe -> recipe.nutrition() == null)) {
            throw new IllegalStateException("Missing nutrition information for " + day);
        }
        return new NutritionInfo(recipes);
    }
}
