package com.example.nutritionplanner;

import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionPlannerAgentUnitTests {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30.0, 60.0, 15.0, 800);

    private static final Recipe PASTA = new Recipe(
            "Pasta Primavera", List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Boil pasta, add vegetables.", 25);

    private static final WeeklyPlan PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, PASTA, PASTA, PASTA)));

    private static final SeasonalIngredients SEASONAL = new SeasonalIngredients(List.of("asparagus"));

    private static final UserProfile ALICE = new UserProfile(
            "alice", List.of("vegetarian"), List.of("weight-loss"), 1800,
            List.of("nuts"), List.of("cilantro"));

    private static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(
            Map.of(DayOfWeek.MONDAY, Set.of(WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER)),
            "DE", "Low-carb meals preferred");

    private NutritionPlannerAgent agent;

    @BeforeEach
    void setUp() {
        agent = new NutritionPlannerAgent(new UserProfileProperties(List.of(ALICE)));
        ReflectionTestUtils.setField(agent, "skillsResource", new ClassPathResource("skills"));
    }

    @Test
    void promptIncludesResolvedCountryNameAndCurrentMonth() throws IOException {
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

        var audit = new NutritionPlannerAgent.NutritionAudit(PLAN);
        audit.validate(ALICE, context.ai());

        var tools = context.getLlmInvocations().getFirst().getInteraction().getTools();
        assertEquals(1, tools.size(), "WeeklyPlan should be registered as a tool object for calorie calculation");
    }
}
