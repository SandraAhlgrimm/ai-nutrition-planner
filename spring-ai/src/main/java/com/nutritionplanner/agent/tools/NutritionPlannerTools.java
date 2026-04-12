package com.nutritionplanner.agent.tools;

import com.nutritionplanner.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Tools available to AI agents for creating and validating nutrition plans.
 * Methods annotated with @Tool are automatically detected and made available to agents
 * when registered via ChatClient.defaultTools().
 *
 * These tools encapsulate the core capabilities agents can invoke autonomously:
 * - Fetching user profiles and seasonal ingredients
 * - Generating and revising meal plans
 * - Validating plans against user constraints
 */
@Component
public class NutritionPlannerTools {

    private final ChatClient chatClient;
    private final UserProfileProperties userProfileProperties;

    public NutritionPlannerTools(ChatClient.Builder builder, UserProfileProperties userProfileProperties) {
        this.chatClient = builder.build();
        this.userProfileProperties = userProfileProperties;
    }

    @Tool(description = "Fetch the user profile by username. Returns dietary restrictions, allergies, health goals, and daily calorie target.")
    public UserProfile fetchUserProfile(String username) {
        return userProfileProperties.getUserProfile(username);
    }

    @Tool(description = "Fetch seasonal ingredients for a given month and country. Returns a list of ingredients in season at that time.")
    public SeasonalIngredients fetchSeasonalIngredients(String month, String country) {
        var prompt = """
                Return a list of ingredients in English that are currently in season for the month of %s in %s.
                Provide diverse ingredients suitable for meal planning.
                """
                .formatted(month, country);

        return chatClient.prompt()
                .system("You are a nutrition expert with deep knowledge of seasonal produce.")
                .user(prompt)
                .call()
                .entity(SeasonalIngredients.class);
    }

    @Tool(description = "Generate a weekly meal plan (WeeklyPlan) based on user requirements and seasonal ingredients. Returns recipes with nutrition information.")
    public WeeklyPlan generateMealPlan(String prompt) {
        return chatClient.prompt()
                .system("""
                        You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
                        Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
                        ingredients and always provide accurate nutrition information for each dish.
                        Instructions: Draft recipes in English based on the user requested meals and days.
                        Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                        """)
                .user(prompt)
                .call()
                .entity(WeeklyPlan.class);
    }

    @Tool(description = "Validate a weekly meal plan against a user profile for nutritional compliance. Checks calories, allergens, dietary restrictions, and disliked ingredients. Returns validation result with violations if any.")
    public NutritionAuditValidationResult validateMealPlan(String weeklyPlanJson, String userProfileJson) {
        var validatePrompt = """
                # Validate these recipes:
                %s

                # Against this user profile:
                %s
                """
                .formatted(weeklyPlanJson, userProfileJson);

        return chatClient.prompt()
                .system("""
                        You are a Nutrition Guard — a strict dietary compliance validator
                        specialized in ensuring meal plans meet user health requirements and dietary restrictions.
                        Tone: Thorough, precise, and uncompromising. You apply dietary rules consistently and
                        flag every violation without exception. Be concise and factual in your assessments.
                        Instructions: Validate a list of recipes against a user profile and flag any violations.
                        Check each recipe for:
                        1. NUTRITION_INFO: Nutrition information is available for each recipe
                        2. CALORIE_OVERFLOW: calories exceed daily calorie target
                        3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                        4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                        5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
                        """)
                .user(validatePrompt)
                .call()
                .entity(NutritionAuditValidationResult.class);
    }

    @Tool(description = "Revise a meal plan based on validation feedback. Returns an improved WeeklyPlan that addresses the violations.")
    public WeeklyPlan reviseMealPlan(String prompt) {
        return chatClient.prompt()
                .system("""
                        You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
                        Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
                        ingredients and always provide accurate nutrition information for each dish.
                        Instructions: Revise recipes based on feedback to correct violations while maintaining appealing meals.
                        """)
                .user(prompt)
                .call()
                .entity(WeeklyPlan.class);
    }
}
