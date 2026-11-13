package com.example.nutritionplanner;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    // The model can omit a closing brace in WeeklyPlan JSON when a tool is attached; strict schema mode rejects nullable meals.
    private static final String WEEKLY_PLAN_SCHEMA = new BeanOutputConverter<>(WeeklyPlan.class).getJsonSchema();

    private final UserProfileProperties userProfileProperties;
    private final ChatClient chatClient;
    private final AsyncTaskExecutor taskExecutor;

    @Value("classpath:skills")
    private Resource skillsResource;

    private @Nullable Path skillsDirectory;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties, ChatClient.Builder chatClientBuilder, AsyncTaskExecutor taskExecutor) {
        this.userProfileProperties = userProfileProperties;
        this.chatClient = chatClientBuilder.build();
        this.taskExecutor = taskExecutor;
    }

    private synchronized Path skillsDirectory() {
        if (skillsDirectory == null) {
            try {
                skillsDirectory = SkillsDirectory.of(skillsResource);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return skillsDirectory;
    }

    @McpTool(description = "Provides a nutrition plan for the week")
    WeeklyPlan createNutritionPlan(WeeklyPlanRequest request) {
        var username = SecurityContextHolder.getContext().getAuthentication().getName();
        return createNutritionPlan(username, request, _ -> Map.of()); // No human in the loop for MCP clients
    }

    WeeklyPlan createNutritionPlan(String username, WeeklyPlanRequest request, AskUserQuestionTool.QuestionHandler questionHandler) {
        // Independent steps run in parallel
        var userProfile = CompletableFuture.supplyAsync(() -> userProfileProperties.getUserProfile(username), taskExecutor);
        var seasonalIngredients = CompletableFuture.supplyAsync(() -> fetchSeasonalIngredients(request), taskExecutor);

        return createWeeklyPlan(request, seasonalIngredients.join(), userProfile.join(), questionHandler);
    }

    private SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest request) {
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var skillsTool = SkillsTool.builder().addSkillsDirectory(skillsDirectory().toString()).build();

        var seasonalIngredients = chatClient.prompt()
                .user(u -> u.text("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Use the available skill as part for the SkillsTool to determine the current month, then return a list of ingredients \
                        in English that are currently in season for that month in {country}.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """).param("country", country))
                .tools(skillsTool, ShellTools.builder().build()) // ShellTools is required by SkillsTool to execute skill scripts
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString())) // Required for Tool Search Advisor
                .call()
                .entity(SeasonalIngredients.class);
        log.info("Seasonal ingredients: {}", seasonalIngredients);
        return seasonalIngredients;
    }

    private WeeklyPlan createWeeklyPlan(WeeklyPlanRequest request, SeasonalIngredients seasonalIngredients,
                                        UserProfile userProfile, AskUserQuestionTool.QuestionHandler questionHandler) {
        var validationRetryAdvisor = new ValidationRetryAdvisor<>(WeeklyPlan.class, plan -> validateWeeklyPlan(plan, userProfile));
        var askUserQuestionTool = AskUserQuestionTool.builder().questionHandler(questionHandler).build();

        var weeklyPlan = chatClient.prompt()
                .system(RECIPE_CURATOR)
                .user(u -> u.text("""
                        # User requested meals and days
                        {mealsAndDays}

                        # Seasonal ingredients
                        {ingredients}

                        # Additional instructions
                        {instructions}

                        Use a tool to ask the user for additional information to refine the recipes if there is no current response included!
                        Never ask the user about which meals to include for a weekday, dietary restrictions, allergies, or nutritional requirements.
                        """).param("mealsAndDays", request.meals())
                        .param("ingredients", seasonalIngredients)
                        .param("instructions", request.additionalInstructions()))
                .advisors(validationRetryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString())) // Required for Tool Search Advisor
                .tools(askUserQuestionTool)
                .options(weeklyPlanOptions())
                .call()
                .entity(WeeklyPlan.class);
        log.info("Weekly plan: {}", weeklyPlan);
        return weeklyPlan;
    }

    static OpenAiChatOptions.Builder weeklyPlanOptions() {
        return OpenAiChatOptions.builder().responseFormat(OpenAiChatModel.ResponseFormat.builder()
                .jsonSchema(WEEKLY_PLAN_SCHEMA)
                .strict(false)
                .build());
    }

    private NutritionAuditValidationResult validateWeeklyPlan(WeeklyPlan weeklyPlan, UserProfile userProfile) {
        var validationResult = chatClient.prompt()
                .system(NUTRITION_GUARD)
                .user(u -> u.text("""
                        You have to use available tools to calculate total calories, protein, carbs, fat, and sodium etc.

                        # Validate these recipes:
                        {weeklyPlan}

                        # Against this user profile:
                        {userProfile}
                        """).param("weeklyPlan", weeklyPlan).param("userProfile", userProfile))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString())) // Required for Tool Search Advisor
                .tools(weeklyPlan)
                .call()
                .entity(NutritionAuditValidationResult.class);
        log.info("Validation result: {}", validationResult);
        return validationResult;
    }

    private static final String RECIPE_CURATOR = """
            You are a Recipe Curator.
            Your persona: A culinary expert specializing in weekly meal planning.
            Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
            and always provide accurate nutrition information for each dish.
            Your objective is to draft recipes in English based on the user requested meals and days.
            Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
            """;

    private static final String NUTRITION_GUARD = """
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
