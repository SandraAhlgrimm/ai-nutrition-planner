package com.example.nutrition_planner;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.UnfoldingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Beyond basic Tool-Calling support, Embabel provides more advanced capabilities, such as
 * "Agentic Tools" that delegate to an LLM to orchestrate sub-tools, and "Progressive Tools".
 *
 * {@code @UnfoldingTools} enables progressive tool disclosure: the LLM is initially presented with
 * a single high-level tool (this one), and only when it invokes that tool does Embabel reveal the
 * inner {@link LlmTool}-annotated methods below. This reduces token usage and avoids overwhelming
 * the LLM with choices upfront — the LLM expresses intent first, then receives the relevant
 * detailed tools.
 *
 * Categories group the inner tools so only the relevant subset is unfolded on each invocation:
 * 'nutrition': daily and weekly nutrition totals
 * 'meal':      meal-related information such as counts and statistics
 *
 * For cases where the prompt contribution is large or spans multiple unrelated tools, Embabel also
 * provides {@code LlmReference} — an alternative that contributes context via the system prompt
 * rather than as a single invokable tool.
 */
@UnfoldingTools(name = "weekly_meal_plan_tools", description = "Weekly meal plan tools. Pass category: 'nutrition' for daily/weekly nutrition totals, or 'meal' for meal-related information such as counts")
record WeeklyPlan(List<DailyPlan> days) {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlan.class);

    @LlmTool(category = "nutrition", description = "Returns the total calories, protein, carbs, fat, and sodium for each day of the weekly meal plan")
    public Map<DayOfWeek, NutritionInfo> dailyNutritionTotals() {
        var dailyNutritionTotals = days.stream().collect(Collectors.toMap(
                DailyPlan::day, day -> nutritionTotalsForDay(day.day())
        ));
        log.info("WeeklyPlan:dailyNutritionTotals tool method finished with {}", dailyNutritionTotals);
        return dailyNutritionTotals;
    }

    @LlmTool(category = "nutrition", description = "Returns the total calories, protein, carbs, fat, and sodium for a specific day of the weekly meal plan")
    public NutritionInfo nutritionTotalsForDay(DayOfWeek day) {
        var nutritionInfo = days.stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .map(d -> new NutritionInfo(
                        Stream.of(d.breakfast(), d.lunch(), d.dinner())
                                .flatMap(Optional::stream)
                                .collect(Collectors.toList())
                ))
                .orElse(new NutritionInfo(List.of()));
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

    record DailyPlan(DayOfWeek day, Optional<Recipe> breakfast, Optional<Recipe> lunch, Optional<Recipe> dinner) {}
}
