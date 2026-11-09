package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import dev.langchain4j.skills.Skills;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.Executor;

interface Agents {

    /**
     * Typed entry point for the composed agentic nutrition planning workflow
     */
    interface NutritionPlanner {

        // Non-Declarative API: AgenticServices.sequenceBuilder(Agents.NutritionPlanner.class)
        @SequenceAgent(description = "Creates a validated weekly nutrition plan", typedOutputKey = WeeklyPlan.class,
                subAgents = {PlanInputsFetcher.class, MealPreferencesAgent.class, WeeklyValidatedPlanCreator.class})
        ResultWithAgenticScope<WeeklyPlan> createNutritionPlan(
                @MemoryId String planId,
                @V("username") String username,
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @V("country") String country,
                @V("additionalInstructions") String additionalInstructions);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return ApplicationContextProvider.getBean(ChatModel.class);
        }

        @AgentListenerSupplier
        static AgentListener agentListener() {
            return new ComposedAgentListener(ApplicationContextProvider.getBean(ObservationAgentListener.class),
                    ApplicationContextProvider.getBean(AgentMonitor.class));
        }
    }

    interface PlanInputsFetcher {

        // Non-Declarative API: AgenticServices.parallelBuilder()
        @ParallelAgent(description = "Fetches the user profile and seasonal ingredients in parallel",
                subAgents = {UserProfileAgent.class, SeasonalIngredientAgent.class})
        void fetchPlanInputs();

        // The Spring-managed executor propagates the current observation, so that the spans of the parallel agents
        // become part of the trace
        @ParallelExecutor
        static Executor executor() {
            return ApplicationContextProvider.getBean(AsyncTaskExecutor.class);
        }
    }

    // Non-AI agent
    // Non-Declarative API: AgenticServices.nonAiAgentBuilder()
    class UserProfileAgent {

        @Agent(description = "Fetches the profile of a user", typedOutputKey = UserProfile.class)
        public static UserProfile fetchUserProfile(@V("username") String username) {
            return ApplicationContextProvider.getBean(UserProfileProperties.class).getUserProfile(username);
        }
    }

    interface SeasonalIngredientAgent {

        Skills SKILLS = Skills.from(CustomSkillLoader.loadSkills("skills"));

        // Non-Declarative API: AgenticServices.agentBuilder(Agents.SeasonalIngredientAgent.class)
        @UserMessage("""
            You are a nutrition expert with deep knowledge of seasonal produce.

            Use the available skill to determine the current month, then return a list of ingredients in English
            that are currently in season for that month in {{country}}.
            Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
            """)
        @Agent(description = "Fetches seasonal ingredients for a given location and time of year",
                typedOutputKey = SeasonalIngredients.class)
        SeasonalIngredients fetchSeasonalIngredients(@V("country") String country);

        @SystemMessageProviderSupplier
        static String systemMessage(Object memoryId) {
            return """
                You have access to the following skills:
                %s
                When the user's request relates to one of these skills, activate it first.
                """.formatted(SKILLS.formatAvailableSkills());
        }

        @ToolProviderSupplier
        static ToolProvider toolProvider() {
            return SKILLS.toolProvider();
        }
    }

    interface MealPreferencesAgent {

        // Non-Declarative API: AgenticServices.humanInTheLoopBuilder()
        @HumanInTheLoop(description = "Asks the user for meal preferences to tailor the plan",
                outputKey = "MealPreferences")
        static Object askForMealPreferences() {
            // The class name as response id tells the UI which response is expected
            return new SuspendedResponse<MealPreferences>(MealPreferences.class.getName());
        }
    }

    interface WeeklyValidatedPlanCreator {

        // Non-Declarative API: AgenticServices.loopBuilder()
        @LoopAgent(subAgents = {WeeklyPlanCreator.class, NutritionGuard.class}, typedOutputKey = WeeklyPlan.class,
                maxIterations = 3)
        WeeklyPlan createValidatedWeeklyPlan();

        @ExitCondition(testExitAtLoopEnd = true, description = "all validations passed")
        static boolean exit(@K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult) {
            return validationResult.allPassed();
        }
    }

    interface WeeklyPlanCreator {

        @SystemMessage(Personas.RECIPE_CURATOR)
        @UserMessage("""
            Create a weekly meal plan based on the following inputs:

            # User requested meals and days
            {{WeeklyPlanRequest}}

            # Seasonal ingredients
            {{SeasonalIngredients}}

            # User profile (dietary restrictions, allergies, preferences)
            {{UserProfile}}

            # Meal preferences provided by the user
            {{MealPreferences}}

            # Additional instructions
            {{additionalInstructions}}
            
            If a current response and feedback are provided below, revise the current response based on the feedback \
            instead of creating a new plan.

            # Current response
            {{WeeklyPlan}}

            # Feedback
            {{NutritionAuditValidationResult}}
            """)
        @Agent(description = "Creates a weekly meal plan using seasonal ingredients and user preferences",
                typedOutputKey = WeeklyPlan.class)
        WeeklyPlan createWeeklyPlan(
                @K(WeeklyPlanRequest.class) WeeklyPlanRequest request,
                @K(SeasonalIngredients.class) SeasonalIngredients seasonalIngredients,
                @K(UserProfile.class) UserProfile userProfile,
                @K(MealPreferences.class) MealPreferences mealPreferences,
                @V("additionalInstructions") String additionalInstructions,
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(NutritionAuditValidationResult.class) NutritionAuditValidationResult validationResult);
    }

    interface NutritionGuard {

        @SystemMessage(Personas.NUTRITION_GUARD)
        @UserMessage("""
            Validate these recipes against the user profile and flag any violations.
            You have to use available tools to calculate total calories, protein, carbs, fat, and sodium etc.


            # Recipes
            {{WeeklyPlan}}

            # User profile
            {{UserProfile}}
            """)
        @Agent(description = "Validates a weekly plan against user dietary requirements",
                typedOutputKey = NutritionAuditValidationResult.class)
        NutritionAuditValidationResult validate(
                @K(WeeklyPlan.class) WeeklyPlan weeklyPlan,
                @K(UserProfile.class) UserProfile userProfile);

        @ToolsSupplier
        static Object[] tools() {
            return new Object[] { new WeeklyPlan() };
        }

        @ToolSearchStrategySupplier
        static ToolSearchStrategy toolSearchStrategy() {
            return new SimpleToolSearchStrategy();
        }
    }

    interface Personas {
        String RECIPE_CURATOR = """
                You are a Recipe Curator.
                Your persona: A culinary expert specializing in weekly meal planning.
                Your voice: Creative yet practical. You craft balanced, appealing recipes using seasonal ingredients
                and always provide accurate nutrition information for each dish.
                Your objective is to draft recipes in English based on the user requested meals and days.
                Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                """;

        String NUTRITION_GUARD = """
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
