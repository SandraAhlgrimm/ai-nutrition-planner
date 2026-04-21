package com.example.nutritionplanner;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final UserProfileProperties userProfileProperties;
    private final AgentMonitor agentMonitor;

    NutritionPlannerAgent(UserProfileProperties userProfileProperties, AgentMonitor agentMonitor) {
        this.userProfileProperties = userProfileProperties;
        this.agentMonitor = agentMonitor;
    }

    WeeklyPlan createNutritionPlan(String username, WeeklyPlanRequest request) {
        log.info("Starting meal plan creation for user: {}", username);
        var userProfile = userProfileProperties.getUserProfile(username);
        var month = LocalDate.now().getMonth().toString();
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);

        var weeklyPlan = AgenticServices.createAgenticSystem(Agents.NutritionPlanner.class)
                .createNutritionPlan(userProfile, request, month, country, request.additionalInstructions());
        log.info("NutritionPlannerAgent finished with agents invocations {}", agentMonitor.successfulExecutions().getFirst());
        return weeklyPlan;
    }
}
