package com.example.nutritionplanner;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);
    static final int MAX_REVISIONS = 3;

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean(destroyMethod = "close")
    ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    AgentListener agentListener(MeterRegistry registry, AgentMonitor monitor) {
        return new ComposedAgentListener(new AgentListeners.LoggingListener(),
                new AgentListeners.MicrometerAgentListener(registry), monitor);
    }

    @Bean
    MicrometerMetricsChatModelListener chatModelListener(MeterRegistry registry) {
        return new MicrometerMetricsChatModelListener(registry);
    }

    @Bean
    Agents.NutritionPlanner nutritionPlanner(ChatModel model, UserProfileProperties profiles,
                                            ExecutorService agentExecutor,
                                            @Qualifier("agentListener") AgentListener listener, JsonMapper mapper) {
        var seasonalIngredients = AgenticServices.agentBuilder(Agents.SeasonalIngredientAgent.class)
                .chatModel(model).build();
        var contextPropagation = new ContextPropagatingTaskDecorator();
        var preparation = AgenticServices.parallelBuilder()
                .name("prepareProfileAndIngredients")
                .subAgents(profiles, seasonalIngredients)
                .executor(task -> agentExecutor.execute(contextPropagation.decorate(task)))
                .build();

        var creator = AgenticServices.agentBuilder(Agents.WeeklyPlanCreator.class).chatModel(model).build();
        var reviser = AgenticServices.agentBuilder(Agents.WeeklyPlanReviser.class).chatModel(model).build();
        var revisions = AgenticServices.loopBuilder()
                .name("reviseAndAudit")
                .subAgents(reviser, nutritionGuard(model, "auditRevision"), new RequestShapeAudit())
                .maxIterations(MAX_REVISIONS)
                .testExitAtLoopEnd(true)
                .exitCondition("All dietary and request checks passed", AgentConfiguration::auditPassed)
                .outputKey(WeeklyPlan.class)
                .build();
        var reviseIfNeeded = AgenticServices.conditionalBuilder()
                .name("reviseOnlyOnFailure")
                .subAgents(scope -> !auditPassed(scope), revisions)
                .outputKey(WeeklyPlan.class)
                .build();

        return AgenticServices.sequenceBuilder(Agents.NutritionPlanner.class)
                .beforeCall(scope -> {
                    var request = Objects.requireNonNull(scope.readState(WeeklyPlanRequest.class));
                    scope.writeState("requiredMeals", RequestedPlanFormat.requiredMeals(request));
                    // Ollama applies JSON grammar to the entire response, so never apply it to tool-enabled audits.
                    scope.writeState("planParameters", model.provider() == ModelProvider.OLLAMA
                            ? RequestedPlanFormat.parameters(request, mapper) : DefaultChatRequestParameters.EMPTY);
                })
                .subAgents(preparation, creator, nutritionGuard(model, "auditInitialPlan"),
                        new RequestShapeAudit(), reviseIfNeeded)
                .output(AgentConfiguration::validatedPlan)
                .listener(listener)
                .build();
    }

    private static Agents.NutritionGuard nutritionGuard(ChatModel model, String name) {
        return AgenticServices.agentBuilder(Agents.NutritionGuard.class)
                .name(name)
                .chatModel(model)
                .tools(new NutritionTools())
                .toolArgumentsErrorHandler((error, context) -> {
                    throw new IllegalStateException("Invalid nutrition tool arguments", error);
                })
                .toolExecutionErrorHandler((error, context) -> {
                    if (error instanceof NutritionTools.UnplannedDayException) {
                        log.warn("Nutrition audit requested an absent day: {}", error.getMessage());
                        return ToolErrorHandlerResult.text("ERROR: " + error.getMessage()
                                + ". No nutrition total is available for that day. Use dailyNutritionTotals to"
                                + " inspect the candidate's actual days. If the absent day was requested,"
                                + " report it as a violation; otherwise do not require it.");
                    }
                    throw new IllegalStateException("Nutrition tool execution failed", error);
                })
                .build();
    }

    private static boolean auditPassed(AgenticScope scope) {
        return Objects.requireNonNull(scope.readState(NutritionAuditValidationResult.class),
                "The nutrition audit did not return a result").allPassed();
    }

    private static WeeklyPlan validatedPlan(AgenticScope scope) {
        var audit = Objects.requireNonNull(scope.readState(NutritionAuditValidationResult.class),
                "The nutrition audit did not return a result");
        if (!audit.allPassed()) {
            throw new PlanValidationException(audit,
                    Objects.requireNonNull(scope.readState(WeeklyPlan.class), "The planner did not return a plan"));
        }
        return Objects.requireNonNull(scope.readState(WeeklyPlan.class), "The planner did not return a plan");
    }
}
