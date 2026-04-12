package com.nutritionplanner.controller;

import com.nutritionplanner.orchestration.NutritionPlannerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NutritionPlannerUiController {

    private final NutritionPlannerService plannerService;
    private final String aiModel;

    public NutritionPlannerUiController(NutritionPlannerService plannerService,
                                         @Value("${langchain4j.azure-open-ai.chat-model.deployment-name:unknown}") String aiModel) {
        this.plannerService = plannerService;
        this.aiModel = aiModel;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "login";
    }

    @GetMapping("/")
    public String form(Model model) {
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "index";
    }

    @GetMapping("/plan/result")
    public String getCachedResult(@RequestParam String id, Model model) {
        var plan = plannerService.consumeResult(id);
        if (plan == null) {
            model.addAttribute("error", "Result expired or not found. Please generate a new plan.");
            return "fragments/plan :: error";
        }
        model.addAttribute("plan", plan);
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "fragments/plan :: plan";
    }
}
