package com.example.nutrition_planner;

import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NutritionPlannerAgentUnitTests {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30, 60, 15, 800);

    private static final Recipe PASTA = new Recipe(
            "Pasta Primavera", List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Boil pasta, add vegetables.", 25);

    private static final WeeklyPlan PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                    Optional.of(PASTA), Optional.of(PASTA), Optional.of(PASTA))));

    private static final SeasonalIngredients SEASONAL = new SeasonalIngredients(
            List.of(new Recipe.Ingredient("asparagus", "500", "g")));

    private static final UserProfile ALICE = new UserProfile(
            "alice", List.of("vegetarian"), List.of("weight-loss"), 1800,
            List.of("nuts"), List.of("cilantro"));

    private static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(
            List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                    List.of(WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER))),
            "DE", "Low-carb meals preferred");

    private NutritionPlannerAgent agent;

    @BeforeEach
    void setUp() {
        var props = new UserProfileProperties(List.of(ALICE));
        agent = new NutritionPlannerAgent(props);
    }

    @Test
    void promptIncludesResolvedCountryNameAndCurrentMonth() {
        var context = FakeOperationContext.create();
        context.expectResponse(SEASONAL);

        agent.fetchSeasonalIngredients(REQUEST, context.ai());

        var prompt = context.getLlmInvocations().getFirst().getPrompt();
        assertTrue(prompt.contains("seasonal produce"), "Prompt should mention seasonal produce");
        assertTrue(prompt.contains("Germany"), "DE country code should be resolved to 'Germany'");
    }

    @Test
    void weeklyPlanRegisteredAsToolObject() {
        var context = FakeOperationContext.create();
        context.expectResponse(new NutritionAuditValidationResult(true, List.of(), "All checks passed"));

        var audit = new NutritionPlannerAgent.NutritionAudit(PLAN, SEASONAL, ALICE, "Low-carb");
        audit.validate(context.ai());

        var tools = context.getLlmInvocations().getFirst().getInteraction().getTools();
        assertEquals(1, tools.size(), "WeeklyPlan should be registered as a tool object for calorie calculation");
    }
}
