package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.NutritionPlannerWorkflow;
import com.nutritionplanner.model.UserProfileProperties;
import com.nutritionplanner.model.WeeklyPlan;
import com.nutritionplanner.model.WeeklyPlanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class NutritionPlannerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerOrchestrator.class);

    private final NutritionPlannerWorkflow workflow;
    private final UserProfileProperties userProfileProperties;

    public NutritionPlannerOrchestrator(NutritionPlannerWorkflow workflow,
                                         UserProfileProperties userProfileProperties) {
        this.workflow = workflow;
        this.userProfileProperties = userProfileProperties;
    }

    public WeeklyPlan createPlan(WeeklyPlanRequest request, String username) {
        log.info("Starting meal plan creation for user: {}", username);

        var userProfile = userProfileProperties.getUserProfile(username);
        var month = LocalDate.now().getMonth().toString();
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var additionalInstructions = request.additionalInstructions() != null
                ? request.additionalInstructions() : "";

        log.info("Delegating to agentic workflow — month: {}, country: {}", month, country);
        return workflow.createNutritionPlan(userProfile, request, month, country, additionalInstructions);
    }
}
