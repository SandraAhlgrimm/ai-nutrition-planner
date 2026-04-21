package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/nutrition-plan")
class NutritionPlannerController {

    private final AgentPlatform agentPlatform;

    NutritionPlannerController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @PostMapping
     ResponseEntity<WeeklyPlan> createNutritionPlan(@RequestBody WeeklyPlanRequest request, Principal principal) {
        var invocation = AgentInvocation.builder(agentPlatform).build(WeeklyPlan.class);
        var inputs = Map.of(
                "user", principal.getName(),
                "request", request
        );

        var weeklyPlan = invocation.invoke(inputs);
        return ResponseEntity.ok(weeklyPlan);
    }
}