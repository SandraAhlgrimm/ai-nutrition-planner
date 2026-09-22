package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

interface Agents {

    interface NutritionPlanner {

        @Agent(description = "Creates an audited weekly nutrition plan", typedOutputKey = WeeklyPlan.class)
        WeeklyPlan createNutritionPlan(
                @V("username") String username,
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
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
                typedOutputKey = SeasonalIngredients.class)
        SeasonalIngredients fetchSeasonalIngredients(@V("month") String month, @V("country") String country);
    }

    interface WeeklyPlanCreator {

        @SystemMessage(Personas.RECIPE_CURATOR)
        @UserMessage("""
            Create a weekly meal plan based on the following inputs:

            # User requested meals and days
            {{WeeklyPlanRequest}}

            # Seasonal ingredients
            {{SeasonalIngredients}}

            # User profile (dietary restrictions, allergies, preferences)
            {{UserProfile}}

            # Additional instructions
            {{additionalInstructions}}

            Include exactly the requested days and meals. Leave unrequested meals null.
            This is the initial candidate; there is no previous plan or audit feedback.
            """)
        @Agent(description = "Creates the initial weekly meal plan",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan createWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions);
    }

    interface WeeklyPlanReviser {

        @SystemMessage(Personas.RECIPE_CURATOR)
        @UserMessage("""
            Revise the current weekly meal plan to resolve every audit violation.

            # Original requested meals and days (preserve exactly; leave unrequested meals null)
            {{WeeklyPlanRequest}}

            # Seasonal ingredients
            {{SeasonalIngredients}}

            # User profile (all dietary restrictions, allergies, goals and preferences still apply)
            {{UserProfile}}

            # Original additional instructions (still apply)
            {{additionalInstructions}}

            # Current response
            {{WeeklyPlan}}

            # Feedback
            {{NutritionAuditValidationResult}}
            """)
        @Agent(description = "Revises a weekly meal plan using the latest audit feedback",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan reviseWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions,
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult);
    }

    interface NutritionGuard {

        @SystemMessage(Personas.NUTRITION_GUARD)
        @UserMessage("""
            Audit this candidate meal plan against the user profile and the original request.
            Use the nutrition tools to calculate totals from the current candidate, not invented data.
            Return allPassed=true only when every check passes, with an empty violations list.
            Otherwise return allPassed=false, every violation and actionable consolidatedFeedback.

            # Recipes
            {{WeeklyPlan}}

            # User profile
            {{UserProfile}}

            # Requested days and meals (must match exactly)
            {{WeeklyPlanRequest}}

            # Additional instructions
            {{additionalInstructions}}
            """)
        @Agent(description = "Validates a weekly plan against user dietary requirements",
                typedOutputKey = NutritionAuditValidationResult.class)
        NutritionAuditValidationResult validate(
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(UserProfile.class) UserProfile userProfile,
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @V("additionalInstructions") String additionalInstructions);
    }

    interface Personas {
        String RECIPE_CURATOR = """
                You are a Recipe Curator.
                Your persona: A culinary expert specializing in weekly meal planning.
                Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                """;

        String NUTRITION_GUARD = """
                You are a Nutrition Guard.
                Your persona: A strict dietary compliance validator
                specialized in ensuring meal plans meet user health requirements and dietary restrictions.
                Your voice: Thorough, precise, and uncompromising. You apply dietary rules consistently and
                flag every violation without exception. Be concise and factual in your assessments.
                Your objective is to validate a list of recipes against a user profile and flag any violations.
                Check each recipe for:
                1. NUTRITION_INFO: Nutrition information is available for each recipe
                2. CALORIE_OVERFLOW: calories exceed daily calorie target
                3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
                6. REQUEST_MISMATCH: requested days, meals or additional instructions were not preserved
                """;
    }
}
