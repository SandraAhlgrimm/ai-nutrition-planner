package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Stream;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class RequestShapeAuditTest {

    private final RequestShapeAudit shapeAudit = new RequestShapeAudit();

    @ParameterizedTest
    @MethodSource("invalidPlans")
    void rejectsInvalidShapeEvenWhenModelPassed(WeeklyPlan plan, String expectedFeedback) {
        var audit = shapeAudit.checkRequestShape(plan, REQUEST, PASSED);

        assertThat(audit.allPassed()).isFalse();
        assertThat(audit.consolidatedFeedback()).contains(expectedFeedback);
        assertThat(audit.violations()).anySatisfy(violation ->
                assertThat(violation.explanation()).contains(expectedFeedback));
    }

    @Test
    void mergesShapeFindingsWithoutDiscardingModelViolationsOrFeedback() {
        var modelAudit = failed(1);
        var incomplete = new WeeklyPlan(List.of(plan(1).days().getFirst()));
        var audit = shapeAudit.checkRequestShape(incomplete, REQUEST, modelAudit);

        assertThat(audit.allPassed()).isFalse();
        assertThat(audit.violations()).hasSize(2).startsWith(modelAudit.violations().getFirst());
        assertThat(audit.consolidatedFeedback()).startsWith(modelAudit.consolidatedFeedback() + "\n")
                .contains("Missing requested day: THURSDAY");
    }

    @Test
    void preservesPassingAndFailingModelAuditsWhenShapeMatches() {
        var failingAudit = failed(1);

        assertThat(shapeAudit.checkRequestShape(plan(1), REQUEST, PASSED)).isSameAs(PASSED);
        assertThat(shapeAudit.checkRequestShape(plan(1), REQUEST, failingAudit)).isSameAs(failingAudit);
    }

    @Test
    void dayOrderIsNotARequestShapeViolation() {
        var days = plan(1).days();

        assertThat(shapeAudit.checkRequestShape(new WeeklyPlan(List.of(days.getLast(), days.getFirst())),
                REQUEST, PASSED)).isSameAs(PASSED);
    }

    static Stream<Arguments> invalidPlans() {
        var monday = plan(1).days().getFirst();
        var thursday = plan(1).days().getLast();
        return Stream.of(
                Arguments.of(new WeeklyPlan(List.of(
                        new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, null, null, null), thursday)),
                        "Missing requested meal: MONDAY DINNER"),
                Arguments.of(new WeeklyPlan(List.of(
                        new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, monday.dinner(), null, monday.dinner()), thursday)),
                        "Unrequested meal: MONDAY BREAKFAST"),
                Arguments.of(new WeeklyPlan(List.of(monday)), "Missing requested day: THURSDAY"),
                Arguments.of(new WeeklyPlan(List.of(monday, thursday, monday)), "Duplicate day: MONDAY"),
                Arguments.of(new WeeklyPlan(List.of(monday,
                        new WeeklyPlan.DailyPlan(DayOfWeek.FRIDAY, null, thursday.lunch(), null))),
                        "Unrequested day: FRIDAY"),
                Arguments.of(new WeeklyPlan(List.of()), "Missing requested day: MONDAY"));
    }
}
