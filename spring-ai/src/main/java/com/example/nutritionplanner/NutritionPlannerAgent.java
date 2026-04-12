package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.tool.search.ToolSearchToolCallAdvisor;
import org.springaicommunity.tool.search.ToolSearcher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Agent flow:
 *
 * sequential:
 *   parallel:
 *     fetchUserProfile
 *     fetchSeasonalIngredients
 *   loop:
 *     createWeeklyPlan
 *     validateWeeklyPlan
 */
@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;
    private final ChatClient chatClient;
    private final ToolSearcher toolSearcher;

    public NutritionPlannerAgent(UserProfileProperties userProfileProperties, ChatClient.Builder chatClientBuilder, ToolSearcher toolSearcher) {
        this.userProfileProperties = userProfileProperties;
        this.chatClient = chatClientBuilder.build();
        this.toolSearcher = toolSearcher;
    }

    @McpTool(description = "Provides a nutrition plan for the week")
    WeeklyPlan createNutritionPlan(WeeklyPlanRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return createNutritionPlan(auth.getName(), request);
    }

    WeeklyPlan createNutritionPlan(String name, WeeklyPlanRequest request) {
        // Phase 1: Parallel — fetch user profile and seasonal ingredients
        var result = Workflow.parallel(() -> fetchUserProfileForUser(name), () -> fetchSeasonalIngredients(request));
        var userProfile = (UserProfile) result.getFirst();
        var seasonalIngredients = (SeasonalIngredients) result.getLast();
        log.info("Phase 1 complete — profile: {}, seasonal items: {}", userProfile.name(), seasonalIngredients.items().size());

        // Phase 2: Create weekly plan with validation loop
        var weeklyPlan = createWeeklyPlan(request, seasonalIngredients, userProfile);
        log.info("Phase 2 complete — weekly plan created with {} days", weeklyPlan.days().size());
        return weeklyPlan;
    }

    private UserProfile fetchUserProfileForUser(String user) {
        log.info("NutritionService:fetchUserProfile action called");
        var userProfile = userProfileProperties.getUserProfile(user);
        log.info("NutritionService:fetchUserProfile action ended with {}", userProfile);
        return userProfile;
    }

    private SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest weeklyPlanRequest) {
        log.info("NutritionService:fetchSeasonalIngredients action called");
        var currentMonth = LocalDate.now().getMonth();
        var country = Locale.of("", weeklyPlanRequest.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var seasonalIngredients = chatClient.prompt()
                .user(u -> u.text("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Return a list of ingredients in English that are currently in season for the month of {month} in {country}.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """).param("month",currentMonth).param("country",country)
                )
                .call()
                .entity(SeasonalIngredients.class);
        log.info("NutritionService:fetchSeasonalIngredients action ended with {}", seasonalIngredients);
        return seasonalIngredients;
    }

    private WeeklyPlan createWeeklyPlan(WeeklyPlanRequest weeklyPlanRequest, SeasonalIngredients seasonalIngredients,
                                        UserProfile userProfile) {
        log.info("NutritionService:createWeeklyPlan action called");
        var validationRetryAdvisor = new ValidationRetryAdvisor<>(WeeklyPlan.class,
                plan -> this.validateWeeklyPlan(plan, userProfile));
        var weeklyPlan = chatClient.prompt()
                .system(Personas.RECIPE_CURATOR)
                .user(u -> u.text("""
                        # User requested meals and days
                        {mealsAndDays}

                        # Seasonal ingredients
                        {ingredients}

                        # Additional instructions
                        {instructions}
                        """).param("mealsAndDays", weeklyPlanRequest.days()).param("ingredients", seasonalIngredients)
                        .param("instructions", weeklyPlanRequest.additionalInstructions())
                )
                .advisors(validationRetryAdvisor)
                .tools()
                .call()
                .entity(WeeklyPlan.class);
        log.info("NutritionService:createWeeklyPlan action ended with {}", weeklyPlan);
        return weeklyPlan;
    }

    private NutritionAuditValidationResult validateWeeklyPlan(WeeklyPlan weeklyPlan, UserProfile userProfile) {
        log.info("NutritionService:validateWeeklyPlan action called");
        var toolSearchAdvisor = ToolSearchToolCallAdvisor.builder().toolSearcher(toolSearcher).build();
        var validationResult = chatClient.prompt()
                .system(Personas.NUTRITION_GUARD)
                .user(u -> u.text("""
                        Use available tools to calculate total calories, protein, carbs, fat, and sodium etc.
                        
                        # Validate these recipes:
                        {weeklyPlan}

                        # Against this user profile:
                        {userProfile}
                        """).param("weeklyPlan", weeklyPlan).param("userProfile", userProfile)
                )
                .advisors(toolSearchAdvisor) // Implements the Tool Search Tool pattern, see https://www.anthropic.com/engineering/advanced-tool-use
                .tools(weeklyPlan)
                .call()
                .entity(NutritionAuditValidationResult.class);

        log.info("NutritionService:validateWeeklyPlan action ended with {}", validationResult);
        return validationResult;
    }

    static class Personas {
        static String RECIPE_CURATOR = """
                You are a Recipe Curator.
                Your persona: A culinary expert specializing in weekly meal planning.
                Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                """;

        static String NUTRITION_GUARD = """
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
                """;
    }

}