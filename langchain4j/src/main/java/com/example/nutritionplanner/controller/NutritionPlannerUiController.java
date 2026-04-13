package com.example.nutritionplanner.controller;

import com.example.nutritionplanner.orchestration.NutritionPlannerService;
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
                                         @Value("${ai.provider:unknown}") String provider,
                                         @Value("${ai.model:unknown}") String modelName) {
        this.plannerService = plannerService;
        this.aiModel = "LangChain4j · " + provider + " (" + modelName + ")";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("aiModel", aiModel);
        return "login";
    }

    @GetMapping("/")
    public String form(Model model) {
        model.addAttribute("aiModel", aiModel);
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
        model.addAttribute("aiModel", aiModel);
        return "fragments/plan :: plan";
    }
}
