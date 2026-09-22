package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;
    private final ChatClient chatClient;
    private final ToolIndex toolIndex;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallback skillsTool;
    private final CurrentMonthTool currentMonthTool;

    public NutritionPlannerAgent(UserProfileProperties userProfileProperties, ChatClient.Builder chatClientBuilder,
                                 ToolIndex toolIndex, ToolCallingManager toolCallingManager, Clock clock,
                                 @Value("classpath:skills") Resource skillsResource) {
        this.userProfileProperties = userProfileProperties;
        this.chatClient = chatClientBuilder.build();
        this.toolIndex = toolIndex;
        this.toolCallingManager = toolCallingManager;
        this.skillsTool = SkillsTool.builder().addSkillsResource(skillsResource).build();
        this.currentMonthTool = new CurrentMonthTool(clock);
    }

    @McpTool(description = "Creates an audited nutrition plan for the authenticated user without interactive questions")
    public WeeklyPlan createNutritionPlan(WeeklyPlanRequest request, McpSyncRequestContext context) {
        var username = context.transportContext().get(NutritionPlannerMcpConfiguration.PRINCIPAL_NAME);
        if (!(username instanceof String name) || name.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated MCP principal is required");
        }
        return createNutritionPlan(name, request);
    }

    WeeklyPlan createNutritionPlan(String name, WeeklyPlanRequest request) {
        return createNutritionPlan(name, request, null);
    }

    WeeklyPlan createNutritionPlan(String name, WeeklyPlanRequest request,
                                  AskUserQuestionTool.@Nullable QuestionHandler questionHandler) {
        request.validate();
        var result = Workflow.parallel(() -> fetchUserProfileForUser(name), () -> fetchSeasonalIngredients(request));
        var userProfile = result.first();
        var seasonalIngredients = result.second();

        var weeklyPlan = createWeeklyPlan(request, seasonalIngredients, userProfile, questionHandler);
        log.info("Audited nutrition plan created with {} meals", weeklyPlan.totalMealCount());
        return weeklyPlan;
    }

    private UserProfile fetchUserProfileForUser(String user) {
        return userProfileProperties.getUserProfile(user);
    }

    private SeasonalIngredients fetchSeasonalIngredients(WeeklyPlanRequest weeklyPlanRequest) {
        var country = Locale.of("", weeklyPlanRequest.countryCode()).getDisplayCountry(Locale.ENGLISH);

        var seasonalIngredients = chatClient.prompt()
                .user(u -> u.text("""
                        You are a nutrition expert with deep knowledge of seasonal produce.

                        Use the available skill to determine the current month, then return a list of ingredients \
                        in English that are currently in season for that month in {country}.
                        Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
                        """).param("country",country)
                )
                .tools(skillsTool, currentMonthTool)
                .call()
                .entity(SeasonalIngredients.class);
        return Objects.requireNonNull(seasonalIngredients, "Model returned no seasonal ingredients");
    }

    private WeeklyPlan createWeeklyPlan(WeeklyPlanRequest weeklyPlanRequest, SeasonalIngredients seasonalIngredients,
                                        UserProfile userProfile,
                                        AskUserQuestionTool.@Nullable QuestionHandler questionHandler) {
        var answers = new LinkedHashMap<String, String>();
        var validationRetryAdvisor = new ValidationRetryAdvisor<>(WeeklyPlan.class,
                plan -> this.validateWeeklyPlan(plan, userProfile, weeklyPlanRequest), 3, answers::toString);
        var prompt = chatClient.prompt()
                .system(Personas.RECIPE_CURATOR)
                .user(u -> u.text("""
                        # User requested meals and days
                        {mealsAndDays}

                        # Requested country
                        {country}

                        # User dietary profile (mandatory)
                        {profile}

                        # Seasonal ingredients
                        {ingredients}

                        # Additional instructions
                        {instructions}
                        
                        # Interaction mode
                        {interaction}
                        """).param("mealsAndDays", weeklyPlanRequest.meals().toString()).param("ingredients", seasonalIngredients)
                        .param("country", weeklyPlanRequest.countryCode())
                        .param("instructions", weeklyPlanRequest.additionalInstructions())
                        .param("profile", userProfile)
                        .param("interaction", questionHandler == null
                                ? "Non-interactive: use the supplied profile and request. Do not ask questions."
                                : """
                                  Ask the user for cooking preferences if no answers are included yet.
                                  Do not ask about dietary restrictions, allergies, or nutritional requirements;
                                  those are already in the profile. Use answers already collected on revisions.
                                  """)
                )
                .advisors(validationRetryAdvisor);
        if (questionHandler != null) {
            prompt.tools(AskUserQuestionTool.builder().questionHandler(questions -> {
                var collected = questionHandler.handle(questions);
                if (collected == null || questions.stream().anyMatch(q ->
                        !collected.containsKey(q.question()) || collected.get(q.question()) == null
                                || collected.get(q.question()).isBlank())) {
                    throw new AskUserQuestionTool.InvalidUserAnswerException("Every question requires a non-blank answer");
                }
                answers.putAll(collected);
                return collected;
            }).build());
        }
        return Objects.requireNonNull(prompt.call().entity(WeeklyPlan.class), "Model returned no weekly plan");
    }

    private ValidationRetryAdvisor.ValidationResult validateWeeklyPlan(WeeklyPlan weeklyPlan, UserProfile userProfile,
                                                                       WeeklyPlanRequest request) {
        var toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
                .toolIndex(toolIndex).toolCallingManager(toolCallingManager).build();
        var auditId = UUID.randomUUID().toString();
        try {
            var validationResult = Objects.requireNonNull(chatClient.prompt()
                .system(Personas.NUTRITION_GUARD)
                .user(u -> u.text("""
                        You have to use available tools to calculate total calories, protein, carbs, fat, and sodium etc.
                        
                        # Validate these recipes:
                        {weeklyPlan}

                        # Against this user profile:
                        {userProfile}

                        # Original requested days, meals, country and instructions:
                        {request}
                        """).param("weeklyPlan", weeklyPlan).param("userProfile", userProfile).param("request", request)
                )
                .advisors(toolSearchAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, auditId))
                .tools(weeklyPlan)
                .call()
                .entity(NutritionAuditValidationResult.class), "Model returned no nutrition audit");
            var shapeFeedback = request.shapeFeedback(weeklyPlan);
            if (!shapeFeedback.isEmpty()) {
                return new NutritionAuditValidationResult(false, validationResult.violations(),
                        shapeFeedback + "\n" + validationResult.consolidatedFeedback());
            }
            return validationResult;
        } finally {
            toolSearchAdvisor.evictSession(auditId);
        }
    }

    static class Personas {
        static final String RECIPE_CURATOR = """
                You are a Recipe Curator.
                Your persona: A culinary expert specializing in weekly meal planning.
                Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                """;

        static final String NUTRITION_GUARD = """
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
                6. REQUEST_MISMATCH: missing, duplicated or extra requested days or meals, or ignored instructions
                """;
    }

}