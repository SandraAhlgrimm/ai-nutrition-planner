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
        return currentPlan(scope).days().stream()
                .collect(Collectors.toMap(WeeklyPlan.DailyPlan::day, NutritionTools::totals));
    }

    @Tool("Returns nutrition totals for the specified day of the current candidate plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day, AgenticScope scope) {
        return currentPlan(scope).days().stream()
                .filter(dailyPlan -> dailyPlan.day() == day)
                .findFirst()
                .map(NutritionTools::totals)
                .orElseThrow(() -> new IllegalArgumentException("No meals planned for " + day));
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

    private static NutritionInfo totals(WeeklyPlan.DailyPlan day) {
        List<Recipe> recipes = meals(day).toList();
        if (recipes.stream().anyMatch(recipe -> recipe.nutrition() == null)) {
            throw new IllegalStateException("Missing nutrition information for " + day.day());
        }
        return new NutritionInfo(recipes);
    }
}
