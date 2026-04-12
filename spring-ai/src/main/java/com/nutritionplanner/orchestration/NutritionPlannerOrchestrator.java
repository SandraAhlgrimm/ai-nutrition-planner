package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.OrchestratorAgent;
import com.nutritionplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Nutrition Planner Orchestrator - delegates to OrchestratorAgent for agentic planning.
 *
 * This service now uses an Advisor-pattern agent (OrchestratorAgent) that autonomously
 * manages the meal planning workflow with adaptive strategies:
 * - Fetches user profile and seasonal ingredients
 * - Creates initial meal plan
 * - Validates against user constraints
 * - Adjusts strategy based on validation failures
 * - Iterates until plan passes validation or max iterations reached
 *
 * The agent uses:
 * - ToolCallAdvisor for autonomous tool invocation
 * - PromptChatMemoryAdvisor for maintaining planning context
 * - Adaptive workflow to adjust cuisine/ingredients based on failures
 */
@Service
public class NutritionPlannerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerOrchestrator.class);

    private final OrchestratorAgent orchestratorAgent;
    private final UserProfileProperties userProfileProperties;

    public NutritionPlannerOrchestrator(OrchestratorAgent orchestratorAgent,
                                        UserProfileProperties userProfileProperties) {
        this.orchestratorAgent = orchestratorAgent;
        this.userProfileProperties = userProfileProperties;
    }

    public WeeklyPlan createPlan(WeeklyPlanRequest request, String username) {
        log.info("Starting meal plan creation for user: {} using OrchestratorAgent (agentic pattern)", username);

        // Prepare context for the orchestrator agent
        UserProfile userProfile = userProfileProperties.getUserProfile(username);
        String currentMonth = LocalDate.now().getMonth().toString();
        String country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);

        var orchestratorPrompt = """
                Create a personalized weekly meal plan based on these requirements:

                # User Profile
                Name: %s
                Dietary Restrictions: %s
                Health Goals: %s
                Daily Calorie Target: %s calories
                Allergies: %s
                Disliked Ingredients: %s

                # Requested Meals and Days (MUST include ALL days listed below)
                %s
                You MUST create meals for EVERY day listed above. Do NOT skip any day.

                # Month and Location
                Month: %s
                Country: %s

                # Additional Instructions
                %s

                # IMPORTANT WORKFLOW INSTRUCTIONS:
                1. First, fetch the seasonal ingredients available for %s in %s
                2. Create an initial meal plan using these seasonal ingredients
                3. Validate the plan against the user profile for:
                   - Dietary restrictions compliance
                   - Allergen avoidance
                   - Daily calorie targets
                   - Disliked ingredients avoidance
                4. If validation passes, return the plan
                5. If validation fails:
                   - Analyze the specific violations
                   - Adjust your strategy (e.g., different cuisine, lower-calorie alternatives, allergen-free options)
                   - Create a revised meal plan addressing the violations explicitly
                   - Re-validate the revised plan
                   - Iterate up to 3 times total (initial creation + 2 revisions)
                6. Return the final meal plan (validated if possible, or best-effort after max iterations)

                Ensure all recipes include detailed nutrition information (calories, protein, carbs, fat, sodium).
                """
                .formatted(
                        userProfile.name(),
                        userProfile.dietaryRestrictions(),
                        userProfile.healthGoals(),
                        userProfile.dailyCalorieTarget(),
                        userProfile.allergies(),
                        userProfile.dislikedIngredients(),
                        formatRequestedDays(request),
                        currentMonth,
                        country,
                        request.additionalInstructions(),
                        currentMonth,
                        country
                );

        log.info("Delegating to OrchestratorAgent for user: {}", username);
        WeeklyPlan weeklyPlan = orchestratorAgent.createMealPlan(orchestratorPrompt);

        log.info("Meal plan created for user: {} with {} days", username, weeklyPlan.days().size());
        return weeklyPlan;
    }

    private String formatRequestedDays(WeeklyPlanRequest request) {
        return request.days().stream()
                .map(day -> "- %s: %s".formatted(
                        day.day(),
                        day.meals().stream().map(Enum::name).collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n"));
    }
}
