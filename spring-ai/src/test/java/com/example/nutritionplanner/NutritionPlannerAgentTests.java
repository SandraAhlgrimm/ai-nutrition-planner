package com.example.nutritionplanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.tool.search.ToolSearcher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionPlannerAgentTests {

    private static final WeeklyPlanRequest REQUEST = new WeeklyPlanRequest(
            meals(DayOfWeek.MONDAY, WeeklyPlanRequest.MealType.LUNCH),
            "DE", "Quick meals");

    private static final WeeklyPlan PLAN = new WeeklyPlan(List.of());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNutritionPlanUsesAuthenticatedUsername() {
        var agent = spy(newAgent(new UserProfileProperties(List.of(profile("alice"), profile("bob")))));
        doReturn(PLAN).when(agent).createNutritionPlan(eq("bob"), same(REQUEST), any());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null, "ROLE_USER"));

        var plan = agent.createNutritionPlan(REQUEST);

        assertSame(PLAN, plan);
        verify(agent).createNutritionPlan(eq("bob"), same(REQUEST), any());
    }

    @Test
    void createNutritionPlanFallsBackToFirstConfiguredUserWithoutAuthentication() {
        var agent = spy(newAgent(new UserProfileProperties(List.of(profile("alice")))));
        doReturn(PLAN).when(agent).createNutritionPlan(eq("alice"), same(REQUEST), any());

        var plan = agent.createNutritionPlan(REQUEST);

        assertSame(PLAN, plan);
        verify(agent).createNutritionPlan(eq("alice"), same(REQUEST), any());
    }

    private static NutritionPlannerAgent newAgent(UserProfileProperties properties) {
        var chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        return new NutritionPlannerAgent(properties, chatClientBuilder, mock(ToolSearcher.class));
    }

    private static UserProfile profile(String name) {
        return new UserProfile(name, List.of("vegetarian"), List.of("weight-loss"), 1800,
                List.of("nuts"), List.of("cilantro"));
    }

    private static EnumMap<DayOfWeek, Set<WeeklyPlanRequest.MealType>> meals(
            DayOfWeek day, WeeklyPlanRequest.MealType... mealTypes) {
        var meals = new EnumMap<DayOfWeek, Set<WeeklyPlanRequest.MealType>>(DayOfWeek.class);
        meals.put(day, Set.of(mealTypes));
        return meals;
    }
}
