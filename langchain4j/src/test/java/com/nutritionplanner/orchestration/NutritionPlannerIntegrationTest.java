package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.NutritionGuardService;
import com.nutritionplanner.agent.RecipeCuratorService;
import com.nutritionplanner.agent.SeasonalIngredientService;
import com.nutritionplanner.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class NutritionPlannerIntegrationTest {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30, 60, 15, 800);

    private static final Recipe PASTA = new Recipe("Pasta Primavera",
            List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Cook pasta with seasonal vegetables.", 25);

    private static final Recipe REVISED_PASTA = new Recipe("Pasta Primavera (revised)",
            List.of(new Recipe.Ingredient("spinach", "200", "g")),
            NUTRITION, "Cook pasta with spinach instead.", 25);

    private static final WeeklyPlan INITIAL_PLAN = new WeeklyPlan(List.of(
            new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, PASTA, PASTA, PASTA)
    ));

    private static final WeeklyPlan REVISED_PLAN = new WeeklyPlan(List.of(
            new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, REVISED_PASTA, REVISED_PASTA, REVISED_PASTA)
    ));

    private static final SeasonalIngredients SEASONAL = new SeasonalIngredients(
            List.of(new Recipe.Ingredient("asparagus", "1", "bunch")));

    private static final NutritionAuditValidationResult FAIL_RESULT = new NutritionAuditValidationResult(
            false,
            List.of(new NutritionAuditValidationResult.NutritionAuditRecipeViolation(
                    DayOfWeek.MONDAY, "Pasta Primavera", "Contains nuts", "Remove nuts")),
            "Allergen violation found.");

    private static final NutritionAuditValidationResult PASS_RESULT = new NutritionAuditValidationResult(
            true, List.of(), "All checks passed.");

    @MockitoBean
    private SeasonalIngredientService seasonalIngredientService;

    @MockitoBean
    private RecipeCuratorService recipeCuratorService;

    @MockitoBean
    private NutritionGuardService nutritionGuardService;

    @Autowired
    private NutritionPlannerOrchestrator orchestrator;

    @Test
    void shouldExecuteFullFlowWithOneRevisionLoop() {
        when(seasonalIngredientService.fetchSeasonalIngredients(anyString())).thenReturn(SEASONAL);
        when(recipeCuratorService.createMealPlan(anyString())).thenReturn(INITIAL_PLAN);
        when(nutritionGuardService.validate(anyString()))
                .thenReturn(FAIL_RESULT)
                .thenReturn(PASS_RESULT);
        when(recipeCuratorService.reviseMealPlan(anyString())).thenReturn(REVISED_PLAN);

        var request = new WeeklyPlanRequest(
                List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST,
                                WeeklyPlanRequest.MealType.LUNCH,
                                WeeklyPlanRequest.MealType.DINNER))),
                "DE", "Low-carb meals preferred");

        var result = orchestrator.createPlan(request, "alice");

        assertThat(result).isNotNull();
        assertThat(result.days()).hasSize(1);
        assertThat(result.days().getFirst().day()).isEqualTo(DayOfWeek.MONDAY);

        verify(seasonalIngredientService).fetchSeasonalIngredients(anyString());
        verify(recipeCuratorService).createMealPlan(anyString());
        verify(nutritionGuardService, times(2)).validate(anyString());
        verify(recipeCuratorService).reviseMealPlan(anyString());
    }

    @Test
    void shouldReturnImmediatelyWhenValidationPasses() {
        when(seasonalIngredientService.fetchSeasonalIngredients(anyString())).thenReturn(SEASONAL);
        when(recipeCuratorService.createMealPlan(anyString())).thenReturn(INITIAL_PLAN);
        when(nutritionGuardService.validate(anyString())).thenReturn(PASS_RESULT);

        var request = new WeeklyPlanRequest(
                List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST))),
                "US", "");

        var result = orchestrator.createPlan(request, "alice");

        assertThat(result).isEqualTo(INITIAL_PLAN);
        verify(recipeCuratorService, never()).reviseMealPlan(anyString());
    }
}
