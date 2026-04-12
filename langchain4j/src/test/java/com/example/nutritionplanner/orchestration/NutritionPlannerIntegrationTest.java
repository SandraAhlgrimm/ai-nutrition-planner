package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.NutritionPlannerWorkflow;
import com.nutritionplanner.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class NutritionPlannerIntegrationTest {

    private static final NutritionInfo NUTRITION = new NutritionInfo(500, 30, 60, 15, 800);

    private static final Recipe PASTA = new Recipe("Pasta Primavera",
            List.of(new Recipe.Ingredient("asparagus", "200", "g")),
            NUTRITION, "Cook pasta with seasonal vegetables.", 25);

    private static final WeeklyPlan INITIAL_PLAN = new WeeklyPlan(List.of(
            new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, PASTA, PASTA, PASTA)
    ));

    @MockitoBean
    private NutritionPlannerWorkflow workflow;

    @Autowired
    private NutritionPlannerService plannerService;

    @Test
    void shouldDelegateToWorkflowWithCorrectArguments() {
        when(workflow.createNutritionPlan(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(INITIAL_PLAN);

        var request = new WeeklyPlanRequest(
                List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST,
                                WeeklyPlanRequest.MealType.LUNCH,
                                WeeklyPlanRequest.MealType.DINNER))),
                "DE", "Low-carb meals preferred");

        var result = plannerService.createPlan(request, "alice");

        assertThat(result).isNotNull();
        assertThat(result.days()).hasSize(1);
        assertThat(result.days().getFirst().day()).isEqualTo(DayOfWeek.MONDAY);

        var expectedMonth = LocalDate.now().getMonth().toString();
        var expectedCountry = Locale.of("", "DE").getDisplayCountry(Locale.ENGLISH);

        verify(workflow).createNutritionPlan(
                argThat(profile -> profile.name().equals("alice")
                        && profile.dailyCalorieTarget() == 1800
                        && profile.allergies().contains("nuts")),
                eq(request),
                eq(expectedMonth),
                eq(expectedCountry),
                eq("Low-carb meals preferred"));
    }

    @Test
    void shouldPassEmptyStringWhenAdditionalInstructionsNull() {
        when(workflow.createNutritionPlan(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(INITIAL_PLAN);

        var request = new WeeklyPlanRequest(
                List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST))),
                "US", null);

        plannerService.createPlan(request, "alice");

        verify(workflow).createNutritionPlan(any(), eq(request), anyString(), anyString(), eq(""));
    }
}
