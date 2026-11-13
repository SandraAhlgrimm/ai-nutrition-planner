package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("mcp-server")
public class NutritionPlannerMcpTools {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerMcpTools.class);

    private final NutritionPlannerAgent planner;
    private final String username;

    NutritionPlannerMcpTools(NutritionPlannerAgent planner,
                            @Value("${spring.security.user.name}") String username) {
        this.planner = planner;
        this.username = username;
    }

    @Tool("Create a weekly nutrition plan for the configured local user. "
            + "Include cuisine and cooking-time preferences in additionalInstructions; "
            + "this tool does not ask interactive follow-up questions.")
    public WeeklyPlan createNutritionPlan(WeeklyPlanRequest request) {
        Assert.notNull(request, "request is required");
        Assert.notEmpty(request.meals(), "request.meals must include at least one day");
        request.meals().forEach((day, meals) -> {
            Assert.notNull(day, "A day is required for each requested meal");
            Assert.notEmpty(meals, "Each requested day must include at least one meal");
            Assert.noNullElements(meals, "Meal types must not be null");
        });
        Assert.isTrue(request.countryCode() != null && request.countryCode().matches("[A-Z]{2}"),
                "request.countryCode must be a two-letter uppercase country code");

        try {
            var result = planner.createNutritionPlan(UUID.randomUUID().toString(), username, request);
            if (result.suspended()) {
                var responseId = MealPreferences.class.getName();
                Assert.state(result.agenticScope().pendingResponseIds().equals(Set.of(responseId)),
                        "The MCP adapter can only complete the meal-preferences step");
                result = result.completePendingResponse(responseId, new MealPreferences());
            }
            Assert.state(!result.suspended(), "The MCP workflow is still waiting for a response");
            return Objects.requireNonNull(result.result(), "The MCP workflow did not return a weekly plan");
        } catch (RuntimeException e) {
            // The MCP server only returns the exception message to the client, so keep the cause in the server log
            log.error("MCP nutrition plan creation failed", e);
            throw e;
        }
    }
}
