package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.NutritionGuardService;
import com.nutritionplanner.agent.RecipeCuratorService;
import com.nutritionplanner.agent.SeasonalIngredientService;
import com.nutritionplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NutritionPlannerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerOrchestrator.class);

    private final SeasonalIngredientService seasonalIngredientService;
    private final RecipeCuratorService recipeCuratorService;
    private final NutritionGuardService nutritionGuardService;
    private final UserProfileProperties userProfileProperties;

    @Value("${nutrition-planner.max-validation-iterations:3}")
    private int maxValidationIterations;

    public NutritionPlannerOrchestrator(SeasonalIngredientService seasonalIngredientService,
                                         RecipeCuratorService recipeCuratorService,
                                         NutritionGuardService nutritionGuardService,
                                         UserProfileProperties userProfileProperties) {
        this.seasonalIngredientService = seasonalIngredientService;
        this.recipeCuratorService = recipeCuratorService;
        this.nutritionGuardService = nutritionGuardService;
        this.userProfileProperties = userProfileProperties;
    }

    public WeeklyPlan createPlan(WeeklyPlanRequest request, String username) {
        log.info("Starting meal plan creation for user: {}", username);

        // Phase 1: Parallel — fetch user profile and seasonal ingredients
        UserProfile userProfile;
        SeasonalIngredients seasonalIngredients;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var profileFuture = CompletableFuture.supplyAsync(() -> {
                log.info("Fetching user profile for: {}", username);
                return userProfileProperties.getUserProfile(username);
            }, executor);
            var seasonalFuture = CompletableFuture.supplyAsync(() -> {
                var currentMonth = LocalDate.now().getMonth();
                var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
                log.info("Fetching seasonal ingredients for {} in {}", currentMonth, country);
                return seasonalIngredientService.fetchSeasonalIngredients(
                        "Return a list of ingredients in English that are currently in season for the month of %s in %s."
                                .formatted(currentMonth, country));
            }, executor);

            userProfile = profileFuture.join();
            seasonalIngredients = seasonalFuture.join();
        }

        log.info("Phase 1 complete — profile: {}, seasonal items: {}", userProfile.name(), seasonalIngredients.items().size());

        // Phase 2: Create meal plan
        var createPrompt = """
                # User requested meals and days
                %s

                # Seasonal ingredients
                %s

                # Additional instructions
                %s
                """.formatted(request, seasonalIngredients, request.additionalInstructions());

        WeeklyPlan weeklyPlan = recipeCuratorService.createMealPlan(createPrompt);
        log.info("Initial meal plan created with {} days", weeklyPlan.days().size());

        // Phase 2b: Validation loop
        for (int i = 0; i < maxValidationIterations; i++) {
            var validatePrompt = """
                    # Validate these recipes:
                    %s

                    # Against this user profile:
                    %s
                    """.formatted(weeklyPlan, userProfile);

            var validationResult = nutritionGuardService.validate(validatePrompt);
            log.info("Validation iteration {}: allPassed={}", i + 1, validationResult.allPassed());

            if (validationResult.allPassed()) {
                log.info("Meal plan passed validation after {} iteration(s)", i + 1);
                return weeklyPlan;
            }

            // Revise the plan based on feedback
            var revisePrompt = """
                    Revise the recipes based on the following feedback from a nutrition expert.

                    # Recipes
                    %s

                    # Feedback from a nutrition expert
                    %s

                    # Additional instructions
                    %s
                    """.formatted(weeklyPlan, validationResult, request.additionalInstructions());

            weeklyPlan = recipeCuratorService.reviseMealPlan(revisePrompt);
            log.info("Meal plan revised after validation iteration {}", i + 1);
        }

        log.warn("Returning meal plan after exhausting {} validation iterations", maxValidationIterations);
        return weeklyPlan;
    }
}
