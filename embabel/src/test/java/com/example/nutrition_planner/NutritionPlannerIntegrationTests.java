package com.example.nutrition_planner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NutritionPlannerIntegrationTests extends EmbabelMockitoIntegrationTest {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30, 60, 15, 800);

    private static final Recipe PASTA = new Recipe(
            "Pasta Primavera", List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Boil pasta, add vegetables.", 20);

    private static final Recipe REVISED_PASTA = new Recipe(
            "Pasta Primavera (revised)", List.of(new Recipe.Ingredient("spinach", "200", "g")),
            NUTRITION, "Boil pasta, add spinach.", 20);

    private static final WeeklyPlan INITIAL_PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                    Optional.of(PASTA), Optional.of(PASTA), Optional.of(PASTA))));

    private static final WeeklyPlan REVISED_PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                    Optional.of(REVISED_PASTA), Optional.of(REVISED_PASTA), Optional.of(REVISED_PASTA))));

    private static final NutritionAuditValidationResult.NutritionAuditRecipeViolation ALLERGEN_VIOLATION =
            new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                    DayOfWeek.MONDAY, "Pasta Primavera", "Recipe contains nuts which are in user's allergen list", "Replace nuts with seeds");

    @Test
    void shouldExecuteFullFlowWithOneRevisionLoop() {
        var request = new WeeklyPlanRequest(
                List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST, WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER))),
                "DE", "Low-carb meals preferred");

        // Step 1 – fetchSeasonalIngredients (parallel with fetchUserProfile, no LLM for profile)
        whenCreateObject(prompt -> prompt.contains("seasonal produce"), SeasonalIngredients.class)
                .thenReturn(new SeasonalIngredients(List.of(new Recipe.Ingredient("asparagus", "500", "g"))));

        // Step 2 – createMealPlan
        whenCreateObject(prompt -> prompt.contains("Recipe Curator Agent"), WeeklyPlan.class)
                .thenReturn(INITIAL_PLAN);

        // Step 3 – NutritionAudit:validate (first pass — fails, triggering the revision loop)
        // Step 5 – NutritionAudit:validate (second pass — passes, exiting the loop)
        whenCreateObject(prompt -> prompt.contains("Nutrition Guard Agent"), NutritionAuditValidationResult.class)
                .thenReturn(new NutritionAuditValidationResult(
                        false,
                        List.of(ALLERGEN_VIOLATION),
                        "Pasta contains nuts — allergen violation"))
                .thenReturn(new NutritionAuditValidationResult(true, List.of(), "All checks passed"));

        // Step 4 – ReviseMealPlan:revise
        whenCreateObject(prompt -> prompt.contains("Revise the recipes"), WeeklyPlan.class)
                .thenReturn(REVISED_PLAN);

        var inputs = Map.of(
                "user", "alice",
                "request", request
        );
        var result = AgentInvocation.create(agentPlatform, WeeklyPlan.class).invoke(inputs);

        assertNotNull(result);
        assertEquals(REVISED_PLAN, result, "Final plan should be the revised version");

        // Verify call order and content

        // 1. fetchSeasonalIngredients — prompt includes resolved country name
        verifyCreateObjectMatching(
                prompt -> prompt.contains("seasonal produce") && prompt.contains("Germany"),
                SeasonalIngredients.class, llm -> llm.getTools().isEmpty());

        // 2. createMealPlan — initial plan, not a revision
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Recipe Curator Agent") && prompt.contains("Your responsibilities"), WeeklyPlan.class, llm -> llm.getTools().isEmpty());

        // 3. NutritionAudit:validate (first) — dailyNutritionTotals tool must be registered via withToolObject()
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Nutrition Guard Agent") && prompt.contains("alice") && !prompt.contains("(revised)"),
                NutritionAuditValidationResult.class,
                llm -> llm.getTools().size() == 1);

        // 4. ReviseMealPlan:revise — prompt contains violation feedback, temperature 0.7
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Recipe Curator Agent") && prompt.contains("Revise the recipes"),
                WeeklyPlan.class,
                llm -> llm.getTools().isEmpty());

        // 5. NutritionAudit:validate (second) — tool still registered, revised plan in prompt, now passes
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Nutrition Guard Agent") && prompt.contains("alice") && prompt.contains("(revised)"),
                NutritionAuditValidationResult.class,
                llm -> llm.getTools().size() == 1);

        verifyNoMoreInteractions();
    }
}
