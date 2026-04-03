package com.example.nutrition_planner;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Agent flow:
 *
 * sequential:
 *   parallel:
 *     fetchUserProfile
 *     fetchSeasonalIngredients
 *   createMealPlan
 *   NutritionAudit:validate
 *   optional loop:
 *     ReviseMealPlan:revise
 *     NutritionAudit:validate
 *   Done:createMealPlan
 */
@Agent(description = "Supports conscious meal planning and sustainable eating habits.")
class NutritionPlanner {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlanner.class);

    private final UserProfileProperties userProfileProperties;

    NutritionPlanner(UserProfileProperties userProfileProperties) {
        this.userProfileProperties = userProfileProperties;
    }

    @State
    interface Stage {}

    @Action
    UserProfile fetchUserProfile(String user) {
        log.info("NutritionPlanner:fetchUserProfile action called");
        var userProfile = userProfileProperties.getUserProfile(user);
        log.info("NutritionPlanner:fetchUserProfile action ended with {}", userProfile);
        return userProfile;
    }

    @Action
    SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest weeklyPlanRequest, Ai ai) {
        log.info("NutritionPlanner:fetchSeasonalIngredients action called");
        var currentMonth = LocalDate.now().getMonth();
        var country = Locale.of("", weeklyPlanRequest.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var seasonalIngredients = ai
                .withLlm(LlmOptions.withAutoLlm())
                .createObject("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Return a list of ingredients in English that are currently in season for the month of %s in %s.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """.formatted(currentMonth, country),
                        SeasonalIngredients.class);
        log.info("NutritionPlanner:fetchSeasonalIngredients action ended with {}", seasonalIngredients);
        return seasonalIngredients;
    }

    @Action
    NutritionAudit createMealPlan(WeeklyPlanRequest weeklyPlanRequest, SeasonalIngredients seasonalIngredients,
                              UserProfile userProfile, Ai ai) {
        log.info("NutritionPlanner:createMealPlan action called");
        var weeklyPlan = ai
                .withLlm(LlmOptions.withAutoLlm())
                .createObject("""
                        You are the Recipe Curator Agent, a culinary expert specializing in weekly meal planning.

                        Your responsibilities:
                        - Draft recipes, in English based on the user requested meals and days
                        - Use seasonal ingredients as much as possible
                        - Provide nutrition information for each recipe
                        - Be creative but practical

                        # User requested meals and days
                        %s

                        # Seasonal ingredients
                        %s

                        # Additional instructions
                        %s
                        """.formatted(weeklyPlanRequest, seasonalIngredients, weeklyPlanRequest.additionalInstructions()),
                        WeeklyPlan.class);
        log.info("NutritionPlanner:createMealPlan action ended with {}", weeklyPlan);
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
                    .createObject("""
                        You are the Nutrition Guard Agent, a strict dietary compliance validator.
        
                        Your job is to validate a list of recipes against a user profile and flag any violations.

                        Check each recipe for:
                        1. NUTRITION_INFO: Nutrition information is available for each recipe
                        2. CALORIE_OVERFLOW: calories exceed daily calorie target. Use available tools to calculate it.
                        3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                        4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                        5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
                        
                        # Validate these recipes:
                        %s
                        
                        # Against this user profile:
                        %s
                        """.formatted(weeklyPlan, userProfile), NutritionAuditValidationResult.class);
            log.info("NutritionPlanner:NutritionAudit:validate action ended with {}", validationResult);
            if (validationResult.allPassed()) {
                return new Done(weeklyPlan);
            }
            return new ReviseMealPlan(weeklyPlan, seasonalIngredients, userProfile, validationResult, additionalInstructions);
        }

    }

    @State
    record ReviseMealPlan(WeeklyPlan weeklyPlan, SeasonalIngredients seasonalIngredients, UserProfile userProfile,
                          NutritionAuditValidationResult validationResult, String additionalInstructions) implements Stage {

        @Action(canRerun = true)
        Stage revise(Ai ai) {
            log.info("NutritionPlanner:ReviseMealPlan:revise action called");
            var revisedWeeklyPlan = ai
                    .withLlm(LlmOptions.withAutoLlm())
                    .createObject("""
                        You are the Recipe Curator Agent, a culinary expert specializing in weekly meal planning.

                        Revise the recipes based on the following feedback from a nutrition expert.

                        # Recipes
                        %s

                        # Feedback from a nutrition expert
                        %s

                        # Additional instructions
                        %s
                        """.formatted(weeklyPlan, validationResult, additionalInstructions), WeeklyPlan.class);
            log.info("NutritionPlanner:ReviseMealPlan:revise action ended with {}", revisedWeeklyPlan);
            return new NutritionAudit(revisedWeeklyPlan, seasonalIngredients, userProfile, additionalInstructions);
        }
    }

    @State
    record Done(WeeklyPlan weeklyPlan) implements Stage {

        @AchievesGoal(description = "Provides a meal plan for the week")
        @Action
        WeeklyPlan createMealPlan() {
            log.info("NutritionPlanner:Done:createMealPlan action called with result: {}", weeklyPlan);
            return weeklyPlan;
        }
    }

}
