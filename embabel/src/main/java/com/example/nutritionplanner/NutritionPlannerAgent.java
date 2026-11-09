package com.example.nutritionplanner;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Locale;

@Agent(description = "Supports conscious meal planning and sustainable eating habits.")
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;

    @Value("classpath:skills")
    private Resource skillsResource;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties) {
        this.userProfileProperties = userProfileProperties;
    }

    @Action
    UserProfile fetchUserProfile(String username) {
        return userProfileProperties.getUserProfile(username);
    }

    // Fallback for MCP clients, which don't pass a username. The higher cost makes the planner
    // prefer fetchUserProfile whenever a username is available.
    @Action(cost = 1.0)
    UserProfile fetchUserProfileOfAuthenticatedUser() {
        return fetchUserProfile(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Action
    SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest request, Ai ai) throws IOException {
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var skills = new Skills("classpath-skills", "Skills available on the classpath")
                .withLocalSkills(skillsResource.getFile().getPath())
                .withScriptExecutionEngine(new ProcessSkillScriptExecutionEngine());

        var seasonalIngredients = ai.withAutoLlm()
                .withReference(skills)
                .createObject("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Use the available skill to determine the current month, then return a list of ingredients \
                        in English that are currently in season for that month in %s.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """.formatted(country), SeasonalIngredients.class);
        log.info("Seasonal ingredients: {}", seasonalIngredients);
        return seasonalIngredients;
    }

    @Action
    MealPreferences gatherMealPreferences(UserProfile userProfile, SeasonalIngredients seasonalIngredients) {
        // Human-in-the-loop
        return WaitFor.formSubmission("A few questions to tailor your plan", MealPreferences.class);
    }

    @Action
    NutritionAudit createWeeklyPlan(WeeklyPlanRequest request, SeasonalIngredients seasonalIngredients,
                                    UserProfile userProfile, MealPreferences mealPreferences, Ai ai) {
        var weeklyPlan = ai.withAutoLlm()
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

                        # Meal preferences provided by the user
                        %s

                        # Additional instructions
                        %s

                        IMPORTANT: You MUST provide a recipe for each requested meal. Include complete nutrition information
                        (calories, proteinGrams, carbGrams, fatGrams, sodiumMg) for every recipe.
                        """.formatted(request.meals(), seasonalIngredients, userProfile, mealPreferences,
                                request.additionalInstructions()), WeeklyPlan.class);
        log.info("Weekly plan: {}", weeklyPlan);
        return new NutritionAudit(weeklyPlan);
    }

    // Returning a Stage hides previous stages and re-plans from the new one; non-stage objects like the
    // UserProfile and WeeklyPlanRequest stay on the blackboard and can still be injected into actions.
    @State
    sealed interface Stage {}

    record NutritionAudit(WeeklyPlan weeklyPlan) implements Stage {

        @Action(canRerun = true)
        Stage validate(UserProfile userProfile, Ai ai) {
            var validationResult = ai.withAutoLlm()
                    .withToolObject(weeklyPlan)
                    .withPromptElements(Personas.NUTRITION_GUARD)
                    .createObject("""
                            Use available tools to calculate total calories, protein, carbs, fat, and sodium etc.

                            # Validate these recipes:
                            %s

                            # Against this user profile:
                            %s
                            """.formatted(weeklyPlan, userProfile), NutritionAuditValidationResult.class);
            log.info("Validation result: {}", validationResult);
            return validationResult.allPassed() ? new Done(weeklyPlan) : new ReviseWeeklyPlan(weeklyPlan, validationResult);
        }
    }

    record ReviseWeeklyPlan(WeeklyPlan weeklyPlan, NutritionAuditValidationResult validationResult) implements Stage {

        @Action(canRerun = true)
        Stage revise(WeeklyPlanRequest request, Ai ai) {
            var revisedWeeklyPlan = ai.withAutoLlm()
                    .withPromptElements(Personas.RECIPE_CURATOR)
                    .createObject("""
                            Revise the recipes based on the following feedback from a nutrition expert.

                            # Recipes
                            %s

                            # Feedback from a nutrition expert
                            %s

                            # Additional instructions
                            %s
                            """.formatted(weeklyPlan, validationResult, request.additionalInstructions()), WeeklyPlan.class);
            log.info("Revised weekly plan: {}", revisedWeeklyPlan);
            return new NutritionAudit(revisedWeeklyPlan);
        }
    }

    record Done(WeeklyPlan weeklyPlan) implements Stage {

        @AchievesGoal(description = "Provides a nutrition plan for the week",
                export = @Export(remote = true, name = "createNutritionPlan", startingInputTypes = WeeklyPlanRequest.class))
        @Action
        WeeklyPlan createNutritionPlan() {
            return weeklyPlan;
        }
    }

    static class Personas {
        static final Persona RECIPE_CURATOR = new Persona("Recipe Curator",
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

        static final Persona NUTRITION_GUARD = new Persona("Nutrition Guard",
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
