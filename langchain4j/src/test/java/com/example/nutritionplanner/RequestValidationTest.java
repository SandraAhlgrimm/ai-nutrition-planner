package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestValidationTest {

    static Stream<WeeklyPlanRequest> invalidRequests() {
        var monday = new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                List.of(WeeklyPlanRequest.MealType.LUNCH));
        return Stream.of(
                new WeeklyPlanRequest(null, "DE", ""),
                new WeeklyPlanRequest(List.of(), "DE", ""),
                new WeeklyPlanRequest(Collections.singletonList(null), "DE", ""),
                new WeeklyPlanRequest(List.of(monday, monday), "DE", ""),
                new WeeklyPlanRequest(List.of(monday), null, ""),
                new WeeklyPlanRequest(List.of(monday), "", ""),
                new WeeklyPlanRequest(List.of(monday), "ZZ", ""),
                new WeeklyPlanRequest(List.of(monday), "de", ""),
                new WeeklyPlanRequest(List.of(new WeeklyPlanRequest.DayPlanRequest(null,
                        List.of(WeeklyPlanRequest.MealType.LUNCH))), "DE", ""),
                new WeeklyPlanRequest(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, null)), "DE", ""),
                new WeeklyPlanRequest(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, List.of())), "DE", ""),
                new WeeklyPlanRequest(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        Collections.singletonList(null))), "DE", ""),
                new WeeklyPlanRequest(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.LUNCH, WeeklyPlanRequest.MealType.LUNCH))), "DE", ""));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestsBeforeInvokingAnyAgent(WeeklyPlanRequest request) {
        var workflow = mock(Agents.NutritionPlanner.class);
        var planner = new NutritionPlannerAgent(workflow, Clock.systemUTC());

        assertThatThrownBy(() -> planner.createNutritionPlan("alice", request))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(workflow);
    }

    @Test
    void requestSnapshotsMutableCollectionsAndNormalizesOptionalInstructions() {
        var meals = new ArrayList<>(List.of(WeeklyPlanRequest.MealType.LUNCH));
        var days = new ArrayList<>(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, meals)));
        var request = new WeeklyPlanRequest(days, "DE", null);
        meals.clear();
        days.clear();

        assertThat(request.days()).hasSize(1);
        assertThat(request.days().getFirst().meals()).containsExactly(WeeklyPlanRequest.MealType.LUNCH);
        assertThat(request.additionalInstructions()).isEmpty();
    }
}
