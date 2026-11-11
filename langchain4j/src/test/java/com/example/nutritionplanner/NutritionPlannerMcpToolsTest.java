package com.example.nutritionplanner;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NutritionPlannerMcpToolsTest {

    private final NutritionPlannerAgent planner = mock(NutritionPlannerAgent.class);
    private final NutritionPlannerMcpTools tools = new NutritionPlannerMcpTools(planner, "alice");
    private final WeeklyPlanRequest request = new WeeklyPlanRequest(
            Map.of(DayOfWeek.MONDAY, Set.of(WeeklyPlanRequest.MealType.LUNCH)), "DE", "Quick meals");
    private final AgenticScope scope = mock(AgenticScope.class);
    private final WeeklyPlan plan = new WeeklyPlan();

    @Test
    void returnsTheExistingPlannerResultForTheConfiguredUser() {
        when(planner.createNutritionPlan(anyString(), eq("alice"), eq(request)))
                .thenReturn(new ResultWithAgenticScope<>(scope, plan));

        assertThat(tools.createNutritionPlan(request)).isSameAs(plan);
        verify(planner).createNutritionPlan(anyString(), eq("alice"), eq(request));
    }

    @Test
    void resumesThePreferenceStepWithoutOpeningAUi() {
        var responseId = MealPreferences.class.getName();
        when(scope.pendingResponseIds()).thenReturn(Set.of(responseId));
        when(planner.createNutritionPlan(anyString(), eq("alice"), eq(request)))
                .thenReturn(new ResultWithAgenticScope<>(scope, null, true,
                        () -> new ResultWithAgenticScope<>(scope, plan)));

        assertThat(tools.createNutritionPlan(request)).isSameAs(plan);
        verify(scope).completePendingResponse(responseId, new MealPreferences());
    }

    @Test
    void rejectsAnUnknownInteractiveStepInsteadOfHanging() {
        when(scope.pendingResponseIds()).thenReturn(Set.of("unexpected-question"));
        when(planner.createNutritionPlan(anyString(), eq("alice"), eq(request)))
                .thenReturn(new ResultWithAgenticScope<>(scope, null, true));

        assertThatIllegalStateException().isThrownBy(() -> tools.createNutritionPlan(request))
                .withMessageContaining("meal-preferences");
    }

    @Test
    void doesNotReturnASuccessForAStillSuspendedWorkflow() {
        when(scope.pendingResponseIds()).thenReturn(Set.of(MealPreferences.class.getName()));
        when(planner.createNutritionPlan(anyString(), eq("alice"), eq(request)))
                .thenReturn(new ResultWithAgenticScope<>(scope, null, true,
                        () -> new ResultWithAgenticScope<>(scope, null, true)));

        assertThatIllegalStateException().isThrownBy(() -> tools.createNutritionPlan(request))
                .withMessageContaining("still waiting");
    }

    @Test
    void rejectsInvalidRequestsBeforeInvokingThePlanner() {
        assertThatIllegalArgumentException().isThrownBy(() -> tools.createNutritionPlan(null))
                .withMessageContaining("request is required");
        assertThatIllegalArgumentException().isThrownBy(() -> tools.createNutritionPlan(new WeeklyPlanRequest()))
                .withMessageContaining("at least one day");
        assertThatIllegalArgumentException().isThrownBy(() -> tools.createNutritionPlan(
                new WeeklyPlanRequest(Map.of(DayOfWeek.MONDAY, Set.of()), "DE", "")))
                .withMessageContaining("at least one meal");
        assertThatIllegalArgumentException().isThrownBy(() -> tools.createNutritionPlan(
                new WeeklyPlanRequest(request.meals(), null, "")))
                .withMessageContaining("countryCode");

        verifyNoInteractions(planner);
    }
}
