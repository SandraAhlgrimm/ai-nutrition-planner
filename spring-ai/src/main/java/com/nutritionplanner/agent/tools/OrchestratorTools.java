package com.nutritionplanner.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritionplanner.agent.RecipeCuratorAgent;
import com.nutritionplanner.agent.NutritionGuardAgent;
import com.nutritionplanner.agent.SeasonalIngredientAgent;
import com.nutritionplanner.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Orchestrator Tools - exposes worker agents as tools callable by the Orchestrator Agent.
 * This enables the Orchestrator Agent to dynamically invoke specialized workers
 * while maintaining adaptive control over the workflow.
 */
@Component
public class OrchestratorTools {

    private final RecipeCuratorAgent recipeCuratorAgent;
    private final NutritionGuardAgent nutritionGuardAgent;
    private final SeasonalIngredientAgent seasonalIngredientAgent;
    private final NutritionPlannerTools basicTools;
    private final ObjectMapper objectMapper;

    public OrchestratorTools(
            RecipeCuratorAgent recipeCuratorAgent,
            NutritionGuardAgent nutritionGuardAgent,
            SeasonalIngredientAgent seasonalIngredientAgent,
            NutritionPlannerTools basicTools,
            ObjectMapper objectMapper) {
        this.recipeCuratorAgent = recipeCuratorAgent;
        this.nutritionGuardAgent = nutritionGuardAgent;
        this.seasonalIngredientAgent = seasonalIngredientAgent;
        this.basicTools = basicTools;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Fetch the user profile. Returns dietary restrictions, allergies, health goals, and daily calorie target.")
    public UserProfile fetchUserProfile(String username) {
        return basicTools.fetchUserProfile(username);
    }

    @Tool(description = "Get seasonal ingredients for a specific month and country. Useful for creating location-aware meal plans.")
    public SeasonalIngredients getSeasonalIngredients(String month, String country) {
        return basicTools.fetchSeasonalIngredients(month, country);
    }

    @Tool(description = "Create a meal plan. Ask Recipe Curator agent to generate recipes with meals for all requested days based on provided context (user profile, seasonal ingredients, additional instructions).")
    public WeeklyPlan createMealPlan(String prompt) {
        return recipeCuratorAgent.createMealPlan(prompt);
    }

    @Tool(description = "Validate a meal plan against user profile constraints. Check for nutrition info presence, calorie violations, allergen presence, dietary restriction violations, and disliked ingredients.")
    public NutritionAuditValidationResult validateMealPlan(String weeklyPlanJson, String userProfileJson) {
        return basicTools.validateMealPlan(weeklyPlanJson, userProfileJson);
    }

    @Tool(description = "Revise a meal plan based on validation feedback. Ask Recipe Curator to improve the plan addressing specific violations.")
    public WeeklyPlan reviseMealPlan(String prompt) {
        return recipeCuratorAgent.reviseMealPlan(prompt);
    }
}
