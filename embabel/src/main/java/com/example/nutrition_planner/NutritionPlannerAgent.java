package com.example.nutrition_planner;

import com.embabel.agent.api.annotation.*;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.common.ai.model.LlmOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Agent flow:
 *
 * sequential:
 *   parallel:
 *     fetchUserProfile
 *     fetchSeasonalIngredients
 *   createWeeklyPlan
 *   NutritionAudit:validate
 *   optional loop:
 *     ReviseWeeklyPlan:revise
 *     NutritionAudit:validate
 *   Done:createNutritionPlan
 */
@Agent(description = "Supports conscious meal planning and sustainable eating habits.")
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties) {
        this.userProfileProperties = userProfileProperties;
    }

    @State
    interface Stage {}

    @Action
    UserProfile fetchUserProfileForUser(String user) {
        log.info("NutritionPlanner:fetchUserProfile action called");
        var userProfile = userProfileProperties.getUserProfile(user);
        log.info("NutritionPlanner:fetchUserProfile action ended with {}", userProfile);
        return userProfile;
    }

    // Required for MCP Server support
    @Action
    UserProfile fetchUserProfile() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return fetchUserProfileForUser(auth.getName());
    }

    @Action
    SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest weeklyPlanRequest, Ai ai) {
        log.info("NutritionPlanner:fetchSeasonalIngredients action called");
        var currentMonth = LocalDate.now().getMonth();
        var country = Locale.of("", weeklyPlanRequest.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var seasonalIngredients = ai
                .withLlm(LlmOptions.withAutoLlm()) // PromptRunner
                .createObject("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Return a list of ingredients in English that are currently in season for the month of %s in %s.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        Use English for all ingredient names, quantities, and units (e.g., "200", "g").
                        """.formatted(currentMonth, country),
                        SeasonalIngredients.class);
        log.info("NutritionPlanner:fetchSeasonalIngredients action ended with {}", seasonalIngredients);
        return seasonalIngredients;
    }

    @Action
    NutritionAudit createWeeklyPlan(WeeklyPlanRequest weeklyPlanRequest, SeasonalIngredients seasonalIngredients,
                                    UserProfile userProfile, Ai ai) {
        log.info("NutritionPlanner:createWeeklyPlan action called");
        var weeklyPlan = ai
                .withLlm(LlmOptions.withAutoLlm())
                .withPromptElements(Personas.RECIPE_CURATOR)
                .createObject("""
                        Create a weekly meal plan with recipes for EVERY requested meal below. Do not skip any meal.
                        Write all recipe names, instructions, ingredient names, quantities, and units in English.

                        # User requested meals and days
                        %s

                        # Seasonal ingredients
                        %s

                        # User profile
                        %s

                        # Additional instructions
                        %s

                        IMPORTANT: You MUST provide a recipe for each requested meal. Include complete nutrition information
                        (calories, proteinGrams, carbGrams, fatGrams, sodiumMg) for every recipe.
                        """.formatted(weeklyPlanRequest.days(), seasonalIngredients, userProfile, weeklyPlanRequest.additionalInstructions()),
                        WeeklyPlan.class);
        log.info("NutritionPlanner:createWeeklyPlan action ended with {}", weeklyPlan);
        return new NutritionAudit(weeklyPlan, seasonalIngredients, userProfile, weeklyPlanRequest.additionalInstructions());
    }

    @State
    record NutritionAudit (WeeklyPlan weeklyPlan, SeasonalIngredients seasonalIngredients, UserProfile userProfile,
                           String additionalInstructions) implements Stage {

        @Action(canRerun = true)
        Stage validate(Ai ai) {
            log.info("NutritionPlanner:NutritionAudit:validate action called");
            var validationResult = ai
                    .withLlm(LlmOptions.withAutoLlm())
                    .withToolObject(weeklyPlan)
                    .withPromptElements(Personas.NUTRITION_GUARD)
                    .createObject("""
                        Use available tools to calculate total calories, protein, carbs, fat, and sodium etc.
                        
                        # Validate these recipes:
                        %s

                        # Against this user profile:
                        %s
                        """.formatted(weeklyPlan, userProfile), NutritionAuditValidationResult.class);
            log.info("NutritionPlanner:NutritionAudit:validate action ended with {}", validationResult);
            if (validationResult.allPassed()) {
                return new Done(weeklyPlan);
            }
            return new ReviseWeeklyPlan(weeklyPlan, seasonalIngredients, userProfile, validationResult, additionalInstructions);
        }

    }

    @State
    record ReviseWeeklyPlan(WeeklyPlan weeklyPlan, SeasonalIngredients seasonalIngredients, UserProfile userProfile,
                            NutritionAuditValidationResult validationResult, String additionalInstructions) implements Stage {

        @Action(canRerun = true)
        Stage revise(Ai ai) {
            log.info("NutritionPlanner:WeeklyPlan:revise action called");
            var revisedWeeklyPlan = ai
                    .withLlm(LlmOptions.withAutoLlm())
                    .withPromptElements(Personas.RECIPE_CURATOR)
                    .createObject("""
                        Revise the recipes based on the following feedback from a nutrition expert.

                        # Recipes
                        %s

                        # Feedback from a nutrition expert
                        %s

                        # Additional instructions
                        %s
                        """.formatted(weeklyPlan, validationResult, additionalInstructions), WeeklyPlan.class);
            log.info("NutritionPlanner:WeeklyPlan:revise action ended with {}", revisedWeeklyPlan);
            return new NutritionAudit(revisedWeeklyPlan, seasonalIngredients, userProfile, additionalInstructions);
        }
    }

    @State
    record Done(WeeklyPlan weeklyPlan) implements Stage {

        @AchievesGoal(description = "Provides a nutrition plan for the week",
                export = @Export(remote = true, name = "createNutritionPlan", startingInputTypes = WeeklyPlanRequest.class))
        @Action
        WeeklyPlan createNutritionPlan() {
            log.info("NutritionPlanner:Done:createNutritionPlan action called with result: {}", weeklyPlan);
            return weeklyPlan;
        }
    }

    static class Personas {
        static Persona RECIPE_CURATOR = new Persona("Recipe Curator",
            """
            A culinary expert specializing in weekly meal planning.
            """,
            """
            Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
            and always provide accurate nutrition information for each dish.
            """,
            """
            Draft recipes in English based on the user requested meals and days.
            Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
            """);

        static Persona NUTRITION_GUARD = new Persona("Nutrition Guard",
            """
            A strict dietary compliance validator
            specialized in ensuring meal plans meet user health requirements and dietary restrictions.
            """,
            """
            Thorough, precise, and uncompromising. You apply dietary rules consistently and
            flag every violation without exception. Be concise and factual in your assessments.
            """,
            """
            Validate a list of recipes against a user profile and flag any violations.
            Check each recipe for:
            1. NUTRITION_INFO: Nutrition information is available for each recipe
            2. CALORIE_OVERFLOW: calories exceed daily calorie target
            3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
            4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
            5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
            """);
    }

}
