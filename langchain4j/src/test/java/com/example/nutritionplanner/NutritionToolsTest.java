package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static com.example.nutritionplanner.PlanFixtures.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionToolsTest {

    private final NutritionTools tools = new NutritionTools();

    @Test
    void toolsOnlyExposeDayAsAnArgumentNotThePlanOrScope() {
        for (var specification : ToolSpecifications.toolSpecificationsFrom(NutritionTools.class)) {
            if (specification.name().equals("nutritionTotalsForDay")) {
                assertThat(specification.parameters().properties().keySet()).containsExactly("day");
            } else {
                assertThat(specification.parameters()).isNull();
            }
        }
    }

    @Test
    void usesUpdatedCandidateAndNeverAnotherRequestsState() {
        var alice = DefaultAgenticScope.ephemeralAgenticScope();
        var bob = DefaultAgenticScope.ephemeralAgenticScope();
        alice.writeState(WeeklyPlan.class, plan(1));
        bob.writeState(WeeklyPlan.class, plan(3));
        assertThat(tools.nutritionTotalsForDay(DayOfWeek.MONDAY, alice).calories()).isEqualTo(100);
        alice.writeState(WeeklyPlan.class, plan(2));
        assertThat(tools.nutritionTotalsForDay(DayOfWeek.MONDAY, alice).calories()).isEqualTo(200);
        assertThat(tools.nutritionTotalsForDay(DayOfWeek.MONDAY, bob).calories()).isEqualTo(300);
        assertThat(tools.totalMealCount(alice)).isEqualTo(2);
    }

    @Test
    void absentPlanUnplannedDayAndMissingNutritionAreExplicitErrorsNotZeroTotals() {
        var scope = DefaultAgenticScope.ephemeralAgenticScope();
        assertThatThrownBy(() -> tools.totalMealCount(scope)).hasMessageContaining("No current plan");
        scope.writeState(WeeklyPlan.class, plan(1));
        assertThatThrownBy(() -> tools.nutritionTotalsForDay(DayOfWeek.SUNDAY, scope))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No meals planned for SUNDAY");
        scope.writeState(WeeklyPlan.class, new WeeklyPlan(List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY,
                new Recipe("incomplete", List.of(), null, "", 0), null, null))));
        assertThatThrownBy(() -> tools.dailyNutritionTotals(scope))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing nutrition information");
    }

    @Test
    void duplicateDaysAreFullyCountedAndReachTheShapeAudit() {
        var original = plan(1);
        var duplicate = new WeeklyPlan(List.of(original.days().getFirst(),
                original.days().getFirst(), original.days().getLast()));
        var scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState(WeeklyPlan.class, duplicate);

        assertThat(tools.dailyNutritionTotals(scope).get(DayOfWeek.MONDAY).calories()).isEqualTo(200);
        assertThat(tools.nutritionTotalsForDay(DayOfWeek.MONDAY, scope).calories()).isEqualTo(200);
        assertThat(tools.totalMealCount(scope)).isEqualTo(3);
        var audit = new RequestShapeAudit().checkRequestShape(duplicate, PlanFixtures.REQUEST, PlanFixtures.PASSED);
        assertThat(audit.allPassed()).isFalse();
        assertThat(audit.consolidatedFeedback()).contains("Duplicate day");
    }
}
