package com.example.nutritionplanner;

import com.embabel.agent.api.annotation.*;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.common.ai.model.LlmOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Agent(description = "Supports conscious meal planning and sustainable eating habits.")
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;
    private final BundledSkills bundledSkills;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties, BundledSkills bundledSkills) {
        this.userProfileProperties = userProfileProperties;
        this.bundledSkills = bundledSkills;
    }

    @State
    sealed interface Stage permits NutritionAudit, ReviseWeeklyPlan, Done {}

    record PlanningContext(WeeklyPlanRequest request, SeasonalIngredients seasonalIngredients, UserProfile userProfile) {}

    @Action
    UserProfile fetchUserProfile() {
        return userProfileProperties.getUserProfile(PlannerIdentity.username());
    }

    @Action
    SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest weeklyPlanRequest, Ai ai) {
        var country = Locale.of("", weeklyPlanRequest.countryCode()).getDisplayCountry(Locale.ENGLISH);
        return ai
                .withLlm(LlmOptions.withAutoLlm())
                .withReferences(bundledSkills.forRequest().asIndividualReferences())
                .createObject("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Activate the current-month skill using its advertised activation tool, then execute its script
                        tool to determine the current month. Use the tool names in the supplied tool catalog.
                        Do not guess the month. If the script fails, report the failure rather than inventing a month.
                        Return a list of ingredients in English that are currently in season for that month in %s.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """.formatted(country),
                        SeasonalIngredients.class);
    }

    @Action
    NutritionAudit createWeeklyPlan(WeeklyPlanRequest weeklyPlanRequest, SeasonalIngredients seasonalIngredients,
                                    UserProfile userProfile, Ai ai) {
        var weeklyPlan = ai
                .withLlm(LlmOptions.withAutoLlm())
                .withPromptElements(Personas.RECIPE_CURATOR)
                .createObject("""
                        Create a weekly meal plan with recipes for EVERY requested meal below. Do not skip any meal
                        or add unrequested days or meals. Omit properties for meals that were not requested.
                        Write all recipe names, instructions, ingredient names, quantities, and units in English.
                        For EACH requested day, the requested meals together must total within +/-10%% of
                        that day's calorie budget. Each meal slot covers a share of the user's daily
                        calorie target: breakfast 25%%, lunch 35%%, dinner 40%%. Sum the shares of the day's
                        requested meals to get its budget. A full day at a 2500 kcal target accepts
                        2250-2750 kcal; a dinner-only day has a 1000 kcal budget and accepts 900-1100 kcal.
                        Never offset one day with another.

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
        return new NutritionAudit(weeklyPlan,
                new PlanningContext(weeklyPlanRequest, seasonalIngredients, userProfile), new RevisionBudget(0));
    }

    @State
    record NutritionAudit(WeeklyPlan weeklyPlan, PlanningContext context, RevisionBudget budget) implements Stage {

        @Action(canRerun = true, clearBlackboard = true)
        Stage validate(Ai ai) {
            log.info("Auditing nutrition plan candidate {}", budget.auditNumber());
            var validationResult = ai
                    .withLlm(LlmOptions.withAutoLlm())
                    .withToolObject(weeklyPlan)
                    .withPromptElements(Personas.NUTRITION_GUARD)
                    .createObject("""
                        Unfold weekly_meal_plan_tools with category nutrition and use its tools to calculate
                        daily calories, protein, carbs, fat, and sodium. Check ALL requested days and meals.
                        Each requested day's total must be within +/-10%% of that day's calorie budget.
                        Each meal slot covers a share of the user's daily calorie target: breakfast 25%%,
                        lunch 35%%, dinner 40%%; sum the shares of the day's requested meals. A full day at a
                        2500 kcal target accepts 2250-2750 kcal; a dinner-only day accepts 900-1100 kcal.
                        Reject both under-target and over-target days; never average or offset across days.
                        
                        # Validate these recipes:
                        %s

                        # Against this user profile:
                        %s

                        # Requested days and meals:
                        %s

                        # Additional instructions:
                        %s
                        """.formatted(weeklyPlan, context.userProfile(), context.request().days(),
                        context.request().additionalInstructions()), NutritionAuditValidationResult.class)
                    .withDeterministicChecks(weeklyPlan, context.request(), context.userProfile());
            if (validationResult.allPassed()) {
                return new Done(weeklyPlan);
            }
            if (budget.exhausted()) {
                throw new NutritionPlanRejectedException(budget, validationResult, weeklyPlan);
            }
            return new ReviseWeeklyPlan(weeklyPlan, context, budget, validationResult);
        }

    }

    @State
    record ReviseWeeklyPlan(WeeklyPlan weeklyPlan, PlanningContext context, RevisionBudget budget,
                           NutritionAuditValidationResult validationResult) implements Stage {

        @Action(canRerun = true, clearBlackboard = true)
        Stage revise(Ai ai) {
            var nextBudget = budget.nextRevision();
            log.info("Revising nutrition plan, revision {} of {}", nextBudget.revisionsUsed(), RevisionBudget.MAX_REVISIONS);
            var revisedWeeklyPlan = ai
                    .withLlm(LlmOptions.withAutoLlm())
                    .withPromptElements(Personas.RECIPE_CURATOR)
                    .createObject("""
                        Revise the recipes based on the following feedback from a nutrition expert.
                        Keep EVERY requested day and meal, omit unrequested meal properties, and provide complete
                        nutrition information. Write all recipe content in English.
                        Each requested day's meals together must total within +/-10%% of that day's calorie
                        budget. Each meal slot covers a share of the user's daily calorie target:
                        breakfast 25%%, lunch 35%%, dinner 40%%; sum the shares of the day's requested meals.
                        A full day at a 2500 kcal target accepts 2250-2750 kcal; a dinner-only day accepts
                        900-1100 kcal. Never offset one day with another.

                        # Recipes
                        %s

                        # Feedback from a nutrition expert
                        %s

                        # Requested days and meals
                        %s

                        # User profile
                        %s

                        # Seasonal ingredients
                        %s

                        # Additional instructions
                        %s
                        """.formatted(weeklyPlan, validationResult, context.request().days(), context.userProfile(),
                        context.seasonalIngredients(), context.request().additionalInstructions()), WeeklyPlan.class);
            return new NutritionAudit(revisedWeeklyPlan, context, nextBudget);
        }
    }

    @State
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
            2. CALORIE_OUT_OF_RANGE: any requested day's total is below 90% or above 110% of that day's
               calorie budget, where breakfast covers 25%, lunch 35% and dinner 40% of the daily calorie
               target (inclusive bounds); never offset a deficit or excess with another day
            3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
            4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
            5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
            """);
    }

}
