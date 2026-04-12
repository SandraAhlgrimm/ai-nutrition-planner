package com.nutritionplanner.agent;

import com.nutritionplanner.model.SeasonalIngredients;
import com.nutritionplanner.model.UserProfile;
import com.nutritionplanner.model.WeeklyPlan;
import com.nutritionplanner.model.WeeklyPlanRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MealPlanCreatorAgent {

    @SystemMessage("""
            You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
            Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
            ingredients and always provide accurate nutrition information for each dish.
            Instructions: Draft recipes in English based on the user requested meals and days.
            Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
            Return a JSON object matching the WeeklyPlan schema with days, each having optional breakfast, lunch, dinner recipes.
            """)
    @UserMessage("""
            Create a weekly meal plan based on the following inputs:

            # User requested meals and days
            {{request}}

            # Seasonal ingredients
            {{seasonalIngredients}}

            # User profile (dietary restrictions, allergies, preferences)
            {{userProfile}}

            # Additional instructions
            {{additionalInstructions}}
            """)
    @Agent(description = "Creates a weekly meal plan using seasonal ingredients and user preferences",
           outputKey = "weeklyPlan")
    WeeklyPlan createMealPlan(
            @V("request") WeeklyPlanRequest request,
            @V("seasonalIngredients") SeasonalIngredients seasonalIngredients,
            @V("userProfile") UserProfile userProfile,
            @V("additionalInstructions") String additionalInstructions);
}
