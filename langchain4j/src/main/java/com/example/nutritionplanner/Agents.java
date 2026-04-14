package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface Agents {

    /**
     * Typed entry point for the composed agentic nutrition planning workflow.
     */
    interface NutritionPlanner {

        @Agent(description = "Creates a validated weekly nutrition plan",
                outputKey = "weeklyPlan")
        WeeklyPlan createNutritionPlan(
                @V("userProfile") UserProfile userProfile,
                @V("request") WeeklyPlanRequest request,
                @V("month") String month,
                @V("country") String country,
                @V("additionalInstructions") String additionalInstructions);
    }

    interface SeasonalIngredientAgent {

        @UserMessage("""
            You are a nutrition expert with deep knowledge of seasonal produce.

            Return a list of ingredients in English that are currently in season for the month of {{month}} in {{country}}.
            Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
            """)
        @Agent(description = "Fetches seasonal ingredients for a given location and time of year",
                outputKey = "seasonalIngredients")
        SeasonalIngredients fetchSeasonalIngredients(@V("month") String month, @V("country") String country);
    }

    interface WeeklyPlanCreator {

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
        WeeklyPlan createWeeklyPlan(
                @V("request") WeeklyPlanRequest request,
                @V("seasonalIngredients") SeasonalIngredients seasonalIngredients,
                @V("userProfile") UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions);
    }

    interface WeeklyPlanReviserAgent {

        @SystemMessage("""
                You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
                Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
                ingredients and always provide accurate nutrition information for each dish.
                Instructions: Revise the recipes based on feedback from a nutrition expert.
                Fix all violations while keeping the meals appealing and seasonal.
                Return a JSON object matching the WeeklyPlan schema with days, each having optional breakfast, lunch, dinner recipes.
                """)
        @UserMessage("""
                Revise the recipes based on the following feedback from a nutrition expert.
                
                # Current recipes
                {{weeklyPlan}}
                
                # Feedback from nutrition expert
                {{validationResult}}
                
                # Additional instructions
                {{additionalInstructions}}
                """)
        @Agent(description = "Revises a weekly plan based on validation feedback",
                outputKey = "weeklyPlan")
        WeeklyPlan revisWeeklyPlan(
                @V("weeklyPlan") WeeklyPlan weeklyPlan,
                @V("validationResult") NutritionAuditValidationResult validationResult,
                @V("additionalInstructions") String additionalInstructions);

    }

    interface NutritionGuard {

        @SystemMessage("""
            You are a Nutrition Guard — a strict dietary compliance validator specialized in ensuring
            meal plans meet user health requirements and dietary restrictions.
            Tone: Thorough, precise, and uncompromising. You apply dietary rules consistently and
            flag every violation without exception. Be concise and factual in your assessments.
            Instructions: Validate a list of recipes against a user profile and flag any violations.
            Check each recipe for:
            1. NUTRITION_INFO: Nutrition information is available for each recipe
            2. CALORIE_OVERFLOW: calories exceed daily calorie target
            3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
            4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
            5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
            Use the available tools to query nutrition totals for precise validation.
            Return a JSON object with allPassed (boolean), violations (array), and consolidatedFeedback (string).
            """)
        @UserMessage("""
            Validate these recipes against the user profile and flag any violations.

            # Recipes
            {{weeklyPlan}}

            # User profile
            {{userProfile}}
            """)
        @Agent(description = "Validates a weekly plan against user dietary requirements",
                outputKey = "validationResult")
        NutritionAuditValidationResult validate(
                @V("weeklyPlan") WeeklyPlan weeklyPlan,
                @V("userProfile") UserProfile userProfile);
    }
}
