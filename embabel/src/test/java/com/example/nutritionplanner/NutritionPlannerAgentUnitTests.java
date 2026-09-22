package com.example.nutritionplanner;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.progressive.UnfoldingTool;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static com.example.nutritionplanner.NutritionTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class NutritionPlannerAgentUnitTests {

    private BundledSkills skills;
    private NutritionPlannerAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        skills = new BundledSkills(new DefaultResourceLoader());
        agent = new NutritionPlannerAgent(new UserProfileProperties(List.of(ALICE)), skills);
    }

    @AfterEach
    void closeSkills() throws IOException {
        skills.close();
    }

    @Test
    void seasonalPromptResolvesCountryAndExposesSkillActivationAndNativeScript() {
        var context = FakeOperationContext.create();
        context.expectResponse(SEASONAL);

        assertEquals(SEASONAL, agent.fetchSeasonalIngredients(REQUEST, context.ai()));

        var invocation = context.getLlmInvocations().getFirst();
        assertTrue(invocation.getPrompt().contains("Germany"));
        assertTrue(invocation.getPrompt().contains("current_month"));
        assertFalse(invocation.getPrompt().contains("{country}"));
        var names = invocation.getInteraction().getTools().stream().map(tool -> tool.getDefinition().getName()).toList();
        assertTrue(names.contains("current_month"), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.contains("current_month") && !name.equals("current_month")), names.toString());
    }

    @Test
    void draftingAndRevisionPreserveAllPlanningContext() {
        var fake = FakeOperationContext.create();
        var initial = plan("Initial lentils");
        var revised = plan("Revised lentils");
        fake.expectResponse(initial);
        var audit = agent.createWeeklyPlan(REQUEST, SEASONAL, ALICE, fake.ai());

        assertEquals(new RevisionBudget(0), audit.budget());
        assertEquals(REQUEST, audit.context().request());
        assertPromptContext(fake.getLlmInvocations().getFirst().getPrompt());
        assertTrue(fake.getLlmInvocations().getFirst().getInteraction().getPromptContributors()
                .contains(NutritionPlannerAgent.Personas.RECIPE_CURATOR));

        fake.expectResponse(FAIL);
        var revision = assertInstanceOf(NutritionPlannerAgent.ReviseWeeklyPlan.class, audit.validate(fake.ai()));
        assertTrue(fake.getLlmInvocations().getLast().getInteraction().getPromptContributors()
                .contains(NutritionPlannerAgent.Personas.NUTRITION_GUARD));
        fake.expectResponse(revised);
        var nextAudit = assertInstanceOf(NutritionPlannerAgent.NutritionAudit.class, revision.revise(fake.ai()));

        assertEquals(new RevisionBudget(1), nextAudit.budget());
        assertEquals(audit.context(), nextAudit.context());
        assertEquals(revised, nextAudit.weeklyPlan());
        assertPromptContext(fake.getLlmInvocations().getLast().getPrompt());
        assertTrue(fake.getLlmInvocations().getLast().getPrompt().contains(FAIL.consolidatedFeedback()));
    }

    @Test
    void auditExposesCategoryBasedUnfoldingAndExactNutritionTools() {
        var fake = FakeOperationContext.create();
        fake.expectResponse(PASS);
        var plan = plan("Lentils");
        var audit = new NutritionPlannerAgent.NutritionAudit(plan,
                new NutritionPlannerAgent.PlanningContext(REQUEST, SEASONAL, ALICE), new RevisionBudget(0));

        assertInstanceOf(NutritionPlannerAgent.Done.class, audit.validate(fake.ai()));

        var facade = fake.getLlmInvocations().getFirst().getInteraction().getTools().stream()
                .filter(tool -> tool.getDefinition().getName().equals("weekly_meal_plan_tools")).findFirst().orElseThrow();
        var unfolding = assertInstanceOf(UnfoldingTool.class, facade);
        var nutrition = unfolding.selectTools("{\"category\":\"nutrition\"}");
        assertEquals(List.of("dailyNutritionTotals", "nutritionTotalsForDay").stream().sorted().toList(),
                nutrition.stream().map(tool -> tool.getDefinition().getName()).sorted().toList());
        assertEquals(List.of("totalMealCount"),
                unfolding.selectTools("{\"category\":\"meal\"}").stream().map(tool -> tool.getDefinition().getName()).toList());
        var daily = nutrition.stream().filter(tool -> tool.getDefinition().getName().equals("dailyNutritionTotals"))
                .findFirst().orElseThrow();
        var result = assertInstanceOf(Tool.Result.WithArtifact.class, daily.call("{}"));
        assertEquals(plan.dailyNutritionTotals(), result.getArtifact());
        assertTrue(result.getContent().contains("1000"));
        assertTrue(result.getContent().contains("MONDAY"));
    }

    @Test
    void passingModelAuditCannotHideMissingRequestedMealsOrNutrition() {
        var broken = new WeeklyPlan(List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, Optional.empty(),
                Optional.of(new Recipe("No nutrition", List.of(), null, "Cook.", 10)), Optional.empty())));
        var checked = PASS.withRequiredMealChecks(broken, REQUEST);
        assertFalse(checked.allPassed());
        assertEquals(3, checked.violations().size());
        assertTrue(checked.consolidatedFeedback().contains("Nutrition information is missing"));
    }

    @Test
    void passingModelAuditCannotAddUnrequestedDaysEvenWithoutMeals() {
        var days = new java.util.ArrayList<>(plan("Lentils").days());
        days.add(new WeeklyPlan.DailyPlan(DayOfWeek.FRIDAY, Optional.empty(), Optional.empty(), Optional.empty()));
        var checked = PASS.withRequiredMealChecks(new WeeklyPlan(days), REQUEST);
        assertFalse(checked.allPassed());
        assertEquals(1, checked.violations().size());
        assertEquals("Unrequested day", checked.violations().getFirst().explanation());
    }

    @Test
    void duplicateDayCandidateCanBeRevisedInsteadOfFailingDuringBindingOrToolUse() {
        var fake = FakeOperationContext.create();
        var valid = plan("Lentils");
        var days = new java.util.ArrayList<>(valid.days());
        days.add(valid.days().getFirst());
        var duplicate = new WeeklyPlan(days);
        assertEquals(2000, duplicate.dailyNutritionTotals().get(DayOfWeek.MONDAY).calories());
        fake.expectResponse(PASS);
        var audit = new NutritionPlannerAgent.NutritionAudit(duplicate,
                new NutritionPlannerAgent.PlanningContext(REQUEST, SEASONAL, ALICE), new RevisionBudget(0));

        var revision = assertInstanceOf(NutritionPlannerAgent.ReviseWeeklyPlan.class, audit.validate(fake.ai()));
        assertTrue(revision.validationResult().consolidatedFeedback().contains("Duplicate day"));
        fake.expectResponse(valid);
        var revisedAudit = assertInstanceOf(NutritionPlannerAgent.NutritionAudit.class, revision.revise(fake.ai()));
        fake.expectResponse(PASS);
        assertInstanceOf(NutritionPlannerAgent.Done.class, revisedAudit.validate(fake.ai()));
        assertEquals(1, revisedAudit.budget().revisionsUsed());
    }

    @Test
    void fourthFailedAuditThrowsInsteadOfCreatingAnUnauditedOrInvalidResult() {
        var fake = FakeOperationContext.create();
        fake.expectResponse(FAIL);
        var audit = new NutritionPlannerAgent.NutritionAudit(plan("Fourth candidate"),
                new NutritionPlannerAgent.PlanningContext(REQUEST, SEASONAL, ALICE), new RevisionBudget(3));
        var failure = assertThrows(NutritionPlanRejectedException.class, () -> audit.validate(fake.ai()));
        assertEquals(4, failure.audits());
        assertEquals(3, failure.revisions());
        assertEquals(1, fake.getLlmInvocations().size());
        assertThrows(IllegalStateException.class, () -> audit.budget().nextRevision());
    }

    @Test
    void profileRequiresScopedAuthenticatedIdentityAndNeverDefaultsToAlice() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, agent::fetchUserProfile);
        assertEquals(ALICE, PlannerIdentity.callAs(() -> "alice", agent::fetchUserProfile));
        assertThrows(IllegalArgumentException.class, () -> PlannerIdentity.callAs(() -> "unknown", agent::fetchUserProfile));
        assertThrows(AuthenticationCredentialsNotFoundException.class, agent::fetchUserProfile);
    }

    private static void assertPromptContext(String prompt) {
        assertTrue(prompt.contains(REQUEST.days().toString()));
        assertTrue(prompt.contains(ALICE.toString()));
        assertTrue(prompt.contains(SEASONAL.toString()));
        assertTrue(prompt.contains(REQUEST.additionalInstructions()));
    }
}
