package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
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

            The daily calorie target is in the user profile. Each meal slot covers a share of it:
            breakfast 25%, lunch 35%, dinner 40%. For every requested day, sum the shares of that day's
            requested meals and keep the day's summed calories between 90% and 110% of that amount,
            inclusive. A day with all three meals therefore uses the full target; a dinner-only day
            uses 40% of it. Check days independently.

            # User profile (dietary restrictions, allergies, preferences)
            {{UserProfile}}

            # Additional instructions
            {{additionalInstructions}}

            # Required meal slots (include every one)
            {{requiredMeals}}

            Return the complete plan, with exactly these days and meals, even if the request describes one dish.
            A requested dish may be reused in multiple slots; never drop a slot to reduce the number of recipes.
            When instructions request a specific dish, use it for every selected slot unless a slot is explicitly excluded.
            Leave unrequested meals null. Nutrition and ingredient quantities are for one serving of each recipe.
            This is the initial candidate; there is no previous plan or audit feedback.
            """)
        @Agent(description = "Creates the initial weekly meal plan",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan createWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions,
                @V("requiredMeals") String requiredMeals,
                @V("planParameters") ChatRequestParameters parameters);
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

            Each meal slot covers a share of the daily calorie target in the user profile: breakfast 25%,
            lunch 35%, dinner 40%. Keep each requested day's summed calories between 90% and 110% of the
            combined share of that day's requested meals, inclusive. Check each day independently.

            # Original additional instructions (still apply)
            {{additionalInstructions}}

            # Current response
            {{WeeklyPlan}}

            # Feedback
            {{NutritionAuditValidationResult}}

            # Required meal slots (include every one, including previously compliant meals)
            {{requiredMeals}}

            Return the complete revised plan, not just the recipes mentioned in feedback.
            Retain compliant meals and repair or add the others. Never omit a requested slot.
            A requested dish may be reused in multiple slots. Nutrition and quantities are for one serving.
            Keep the requested dish in every selected slot unless the user explicitly requested different dishes.
            """)
        @Agent(description = "Revises a weekly meal plan using the latest audit feedback",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan reviseWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @V("additionalInstructions") String additionalInstructions,
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult,
                @V("requiredMeals") String requiredMeals,
                @V("planParameters") ChatRequestParameters parameters);
    }

    interface NutritionGuard {

        @SystemMessage(Personas.NUTRITION_GUARD)
        @UserMessage("""
            Audit this candidate meal plan against the user profile and the original request.
            Use the nutrition tools to calculate totals from the current candidate, not invented data.
            Prefer dailyNutritionTotals for the candidate's actual days. If a day is absent, do not
            repeatedly request its totals: report a violation only if that day was requested.
            Audit only ingredients and nutrition actually present in this candidate, not earlier candidates.
            Nutrition values are per serving. Each meal slot covers a share of dailyCalorieTarget:
            breakfast 25%, lunch 35%, dinner 40%. For every requested day, its tool-calculated calorie
            total must be between 90% and 110% of the combined share of that day's requested meals,
            inclusive. Check each day independently;
            neither a different day's calories nor a per-meal total can offset or satisfy this range.
            For each violation identify the actual recipe ingredient, missing field, or numeric comparison.
            Do not invent allergies, disliked ingredients, missing nutrition, or unlisted ingredients.
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
                Your voice: Practical and request-focused. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Dietary restrictions, the selected meal slots and explicit dish/time instructions are mandatory.
                Seasonal ingredients are suggestions, not a reason to replace a requested dish with a different meal.
                When the user requests one dish across several meal slots, reuse it rather than inventing variety.
                Provide ingredient quantities and nutrition for one serving of each recipe.
                """;

        String NUTRITION_GUARD = """
                You are a Nutrition Guard.
                Your persona: An evidence-based dietary compliance reviewer.
                Your voice: Precise and factual. Accept compliant plans and reject actual violations.
                An empty violations list is the correct result when the current plan satisfies the profile and request.
                Never invent a violation merely to fill the output.

                Apply these rejection criteria only when their condition is true:
                1. NUTRITION_INFO: a recipe's nutrition object is missing. Present numeric fields are per serving.
                2. DAILY_CALORIE_RANGE: a requested day's tool-calculated total is below 90% or above 110%
                   of the combined share of that day's requested meals, where breakfast covers 25%, lunch
                   35% and dinner 40% of dailyCalorieTarget. The bounds are inclusive and each day is
                   checked independently.
                3. ALLERGEN_PRESENT: an actual recipe ingredient matches an allergy in the profile.
                   Do not treat allergy names in the profile or "no nuts" in instructions as recipe ingredients.
                4. RESTRICTION_VIOLATION: an actual recipe ingredient violates a stated restriction, such as meat
                   in a vegetarian plan.
                5. DISLIKED_INGREDIENTS_PRESENT: an actual recipe ingredient is listed as disliked in the profile.
                   Do not invent extra dislikes.
                6. REQUEST_MISMATCH: a requested meal is missing, an unrequested meal is present, or an explicit
                   cooking instruction is violated.

                For a compliant plan return allPassed=true, violations=[], consolidatedFeedback="".
                Otherwise return allPassed=false and report only supported violations with the exact evidence.
                """;
    }
}
