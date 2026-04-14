package com.example.nutritionplanner;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;
    private final ChatModel chatModel;
    private final MeterRegistry meterRegistry;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties, ChatModel chatModel, MeterRegistry meterRegistry) {
        this.userProfileProperties = userProfileProperties;
        this.chatModel = chatModel;
        this.meterRegistry = meterRegistry;
    }

    WeeklyPlan createNutritionPlan(String name, WeeklyPlanRequest request) {
        log.info("Starting meal plan creation for user: {}", name);
        var userProfile = userProfileProperties.getUserProfile(name);

        var seasonalIngredientAgent = AgenticServices.agentBuilder(Agents.SeasonalIngredientAgent.class)
                .chatModel(chatModel)
                .build();

        var weeklyPlanCreator = AgenticServices.agentBuilder(Agents.WeeklyPlanCreator.class)
                .chatModel(chatModel)
                .build();

        var nutritionGuard = AgenticServices.agentBuilder(Agents.NutritionGuard.class)
                .chatModel(chatModel)
                .tools(new WeeklyPlan())
                .build();

        var reviserAgent = AgenticServices.agentBuilder(Agents.WeeklyPlanReviserAgent.class)
                .chatModel(chatModel)
                .build();

        var validationLoop = AgenticServices.loopBuilder()
                .subAgents(nutritionGuard, reviserAgent)
                .maxIterations(3)
                .exitCondition(scope -> {
                    var result = (NutritionAuditValidationResult) scope.readState("validationResult", null);
                    return result != null && result.allPassed();
                })
                .build();

       var nutritionPlanner = AgenticServices.sequenceBuilder(Agents.NutritionPlanner.class)
                .subAgents(seasonalIngredientAgent, weeklyPlanCreator, validationLoop)
                .outputKey("weeklyPlan")
                .listener(new AgentListeners.MicrometerAgentListener(meterRegistry))
                .listener(new AgentListeners.LoggingListener())
                .build();

        var month = LocalDate.now().getMonth().toString();
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
        return nutritionPlanner.createNutritionPlan(userProfile, request, month, country, request.additionalInstructions());
    }
}
