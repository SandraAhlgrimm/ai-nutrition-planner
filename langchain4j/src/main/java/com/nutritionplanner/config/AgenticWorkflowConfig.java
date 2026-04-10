package com.nutritionplanner.config;

import com.nutritionplanner.agent.*;
import com.nutritionplanner.model.NutritionAuditValidationResult;
import com.nutritionplanner.model.WeeklyPlan;
import com.nutritionplanner.observability.MicrometerAgentListener;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import com.nutritionplanner.orchestration.NutritionPlannerService;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticWorkflowConfig {

    private static final Logger log = LoggerFactory.getLogger(AgenticWorkflowConfig.class);

    @Bean
    NutritionPlannerWorkflow nutritionPlannerWorkflow(
            ChatModel chatModel,
            MeterRegistry meterRegistry,
            @Value("${nutrition-planner.max-validation-iterations:3}") int maxIterations) {

        // Agent 1: Fetch seasonal ingredients
        var seasonalAgent = AgenticServices.agentBuilder(SeasonalIngredientAgent.class)
                .chatModel(chatModel)
                .build();

        // Agent 2: Create initial meal plan
        var creatorAgent = AgenticServices.agentBuilder(MealPlanCreatorAgent.class)
                .chatModel(chatModel)
                .build();

        // Agent 3: Nutrition guard with @Tool support for nutrition calculations
        var nutritionTools = new NutritionTools();
        var guardAgent = AgenticServices.agentBuilder(NutritionGuardAgent.class)
                .chatModel(chatModel)
                .tools(nutritionTools)
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest request) {
                        var plan = (WeeklyPlan) request.inputs().get("weeklyPlan");
                        if (plan != null) {
                            nutritionTools.setCurrentPlan(plan);
                        }
                    }
                })
                .build();

        // Agent 4: Revise meal plan based on validation feedback
        var reviserAgent = AgenticServices.agentBuilder(MealPlanReviserAgent.class)
                .chatModel(chatModel)
                .build();

        // Validation loop: validate → (exit if passed) → revise → repeat
        var validationLoop = AgenticServices.loopBuilder()
                .subAgents(guardAgent, reviserAgent)
                .maxIterations(maxIterations)
                .exitCondition(scope -> {
                    var result = (NutritionAuditValidationResult) scope.readState("validationResult", null);
                    return result != null && result.allPassed();
                })
                .build();

        // Full workflow: seasonal → create → validate/revise loop
        // AgentMonitor provides built-in observability for all agent invocations
        return AgenticServices.sequenceBuilder(NutritionPlannerWorkflow.class)
                .subAgents(seasonalAgent, creatorAgent, validationLoop)
                .outputKey("weeklyPlan")
                .listener(new MicrometerAgentListener(meterRegistry))
                .listener(NutritionPlannerService.sseProgressListener())
                .listener(new AgentListener() {
                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }

                    @Override
                    public void beforeAgentInvocation(AgentRequest request) {
                        log.info("Agent '{}' invoked with inputs: {}", request.agentName(), request.inputs().keySet());
                    }

                    @Override
                    public void afterAgentInvocation(dev.langchain4j.agentic.observability.AgentResponse response) {
                        log.info("Agent '{}' completed", response.agentName());
                    }
                })
                .build();
    }
}
