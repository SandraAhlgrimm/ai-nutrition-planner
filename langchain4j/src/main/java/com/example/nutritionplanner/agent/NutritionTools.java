package com.example.nutritionplanner.agent;

import com.example.nutritionplanner.model.NutritionInfo;
import com.example.nutritionplanner.model.WeeklyPlan;
import dev.langchain4j.agent.tool.Tool;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Tool methods for nutrition calculations, exposed to the NutritionGuard agent.
 * Matches embabel's @LlmTool / @UnfoldingTools pattern — allows the LLM to
 * programmatically query nutrition data instead of parsing text.
 */
public class NutritionTools {

    private WeeklyPlan currentPlan;

    public void setCurrentPlan(WeeklyPlan plan) {
        this.currentPlan = plan;
    }

    @Tool("Get daily nutrition totals (calories, protein, carbs, fat, sodium) for all days in the meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        return currentPlan.dailyNutritionTotals();
    }

    @Tool("Get nutrition totals for a specific day of the week")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        return currentPlan.nutritionTotalsForDay(day);
    }

    @Tool("Get total number of meals in the plan")
    public long totalMealCount() {
        return currentPlan.totalMealCount();
    }
}
