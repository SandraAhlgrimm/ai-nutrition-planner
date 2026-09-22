package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.concurrent.CompletionException;

@Service
class NutritionPlanner {

    private final AgentPlatform agentPlatform;

    NutritionPlanner(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    WeeklyPlan plan(WeeklyPlanRequest request, Principal principal) {
        return PlannerIdentity.callAs(principal, () -> {
            try {
                return AgentInvocation.create(agentPlatform, WeeklyPlan.class).invokeAsync(request).join();
            } catch (CompletionException failure) {
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause instanceof NutritionPlanRejectedException rejected) {
                        throw rejected;
                    }
                }
                throw new NutritionPlanningException(failure.getCause());
            }
        });
    }
}
