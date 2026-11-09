package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.hitl.FormBindingRequest;
import com.embabel.agent.core.hitl.FormResponse;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.embabel.ux.form.FormSubmission;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestPropertySource(properties = {"embabel.agent.platform.models.openai.api-key = test"})
class NutritionPlannerAgentIntegrationTests extends EmbabelMockitoIntegrationTest {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30.0, 60.0, 15.0, 800);

    private static final Recipe PASTA = new Recipe(
            "Pasta Primavera", List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Boil pasta, add vegetables.", 20);

    private static final Recipe REVISED_PASTA = new Recipe(
            "Pasta Primavera (revised)", List.of(new Recipe.Ingredient("spinach", "200", "g")),
            NUTRITION, "Boil pasta, add spinach.", 20);

    private static final WeeklyPlan INITIAL_PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, PASTA, PASTA, PASTA)));

    private static final WeeklyPlan REVISED_PLAN = new WeeklyPlan(
            List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, REVISED_PASTA, REVISED_PASTA, REVISED_PASTA)));

    private static final NutritionAuditValidationResult.NutritionAuditRecipeViolation ALLERGEN_VIOLATION =
            new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                    DayOfWeek.MONDAY, "Pasta Primavera", "Recipe contains nuts which are in user's allergen list", "Replace nuts with seeds");

    @Test
    void shouldExecuteFullFlowWithOneRevisionLoop() {
        var request = new WeeklyPlanRequest(
                Map.of(DayOfWeek.MONDAY, Set.of(WeeklyPlanRequest.MealType.BREAKFAST,
                        WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.DINNER)),
                "DE", "Low-carb meals preferred");

        // Step 2 – fetchSeasonalIngredients (parallel with fetchUserProfile, no LLM for profile)
        whenCreateObject(prompt -> prompt.contains("seasonal produce"), SeasonalIngredients.class)
                .thenReturn(new SeasonalIngredients(List.of("asparagus")));

        // Step 2 – createMealPlan
        whenCreateObject(prompt -> prompt.contains("EVERY requested meal"), WeeklyPlan.class)
                .thenReturn(INITIAL_PLAN);

        // Step 3 – NutritionAudit:validate (first pass — fails, triggering the revision loop)
        // Step 5 – NutritionAudit:validate (second pass — passes, exiting the loop)
        whenCreateObject(prompt -> prompt.contains("Validate these recipes"), NutritionAuditValidationResult.class)
                .thenReturn(new NutritionAuditValidationResult(
                        false,
                        List.of(ALLERGEN_VIOLATION),
                        "Pasta contains nuts — allergen violation"))
                .thenReturn(new NutritionAuditValidationResult(true, List.of(), "All checks passed"));

        // Step 4 – ReviseMealPlan:revise
        whenCreateObject(prompt -> prompt.contains("Revise the recipes"), WeeklyPlan.class)
                .thenReturn(REVISED_PLAN);

        var inputs = Map.of("username", "alice", "request", request);

        // Step 1 – gatherMealPreferences pauses the flow via WaitFor.formSubmission(...)
        var process = AgentInvocation.create(agentPlatform, WeeklyPlan.class).run(inputs);
        assertEquals(AgentProcessStatusCode.WAITING, process.getStatus(),
                "Process should pause while waiting for the meal-preferences form");

        // Simulate the user submitting the Human-in-the-Loop form and resume the process
        var formRequest = (FormBindingRequest<?>) process.lastResult();
        var form = formRequest.getPayload();
        var submission = new FormSubmission(
                form.getId(),
                Map.of("cuisineStyle", "Mediterranean", "maxCookingTime", "30 minutes", "flavorsToEmphasize", "fresh herbs"),
                UUID.randomUUID().toString(),
                Instant.now());
        formRequest.onResponse(
                new FormResponse(UUID.randomUUID().toString(), formRequest.getId(), submission, false, Instant.now()),
                process);
        process.run();

        var result = process.resultOfType(WeeklyPlan.class);

        assertNotNull(result);
        assertEquals(REVISED_PLAN, result, "Final plan should be the revised version");

        // 2. fetchSeasonalIngredients — prompt includes resolved country name; skill tools are registered
        verifyCreateObjectMatching(
                prompt -> prompt.contains("seasonal produce") && prompt.contains("Germany"),
                SeasonalIngredients.class, llm -> !llm.getTools().isEmpty());

        // 2. createMealPlan — initial plan, not a revision, includes the user's meal preferences
        verifyCreateObjectMatching(
                prompt -> prompt.contains("EVERY requested meal") && prompt.contains("Mediterranean"),
                WeeklyPlan.class, llm -> llm.getTools().isEmpty());

        // 3. NutritionAudit:validate (first) — dailyNutritionTotals tool must be registered via withToolObject()
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Validate these recipes") && !prompt.contains("(revised)"),
                NutritionAuditValidationResult.class,
                llm -> llm.getTools().size() == 1);

        // 4. ReviseMealPlan:revise — prompt contains violation feedback, temperature 0.7
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Revise the recipes"),
                WeeklyPlan.class,
                llm -> llm.getTools().isEmpty());

        // 5. NutritionAudit:validate (second) — tool still registered, revised plan in prompt, now passes
        verifyCreateObjectMatching(
                prompt -> prompt.contains("Validate these recipes") && prompt.contains("(revised)"),
                NutritionAuditValidationResult.class,
                llm -> llm.getTools().size() == 1);

        verifyNoMoreInteractions();
    }
}
