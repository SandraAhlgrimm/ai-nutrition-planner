package com.example.nutritionplanner;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController {

    private final ChatModel chatModel;
    private final NutritionPlannerAgent nutritionPlannerAgent;

    NutritionPlannerUiController(ChatModel chatModel, NutritionPlannerAgent nutritionPlannerAgent) {
        this.chatModel = chatModel;
        this.nutritionPlannerAgent = nutritionPlannerAgent;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        return "login";
    }

    @GetMapping("/")
    String form(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        return "index";
    }

    @PostMapping("/plan")
    String createPlan(
            @RequestParam(required = false) List<String> monday,
            @RequestParam(required = false) List<String> tuesday,
            @RequestParam(required = false) List<String> wednesday,
            @RequestParam(required = false) List<String> thursday,
            @RequestParam(required = false) List<String> friday,
            @RequestParam(required = false) List<String> saturday,
            @RequestParam(required = false) List<String> sunday,
            @RequestParam(defaultValue = "DE") String countryCode,
            @RequestParam(required = false, defaultValue = "") String additionalInstructions,
            Model model,
            Principal principal) {

        var days = new ArrayList<WeeklyPlanRequest.DayPlanRequest>();
        addDay(days, DayOfWeek.MONDAY, monday);
        addDay(days, DayOfWeek.TUESDAY, tuesday);
        addDay(days, DayOfWeek.WEDNESDAY, wednesday);
        addDay(days, DayOfWeek.THURSDAY, thursday);
        addDay(days, DayOfWeek.FRIDAY, friday);
        addDay(days, DayOfWeek.SATURDAY, saturday);
        addDay(days, DayOfWeek.SUNDAY, sunday);

        var request = new WeeklyPlanRequest(days, countryCode, additionalInstructions);
        var weeklyPlan = nutritionPlannerAgent.createNutritionPlan(principal.getName(), request);

        model.addAttribute("plan", weeklyPlan);
        model.addAttribute("aiModel", getAiModelName());
        return "fragments/plan :: plan";
    }

    private void addDay(List<WeeklyPlanRequest.DayPlanRequest> days, DayOfWeek day, List<String> meals) {
        if (meals != null && !meals.isEmpty()) {
            days.add(new WeeklyPlanRequest.DayPlanRequest(day,
                    meals.stream().map(WeeklyPlanRequest.MealType::valueOf).toList()));
        }
    }

    private String getAiModelName() {
        var chatModelDefaultOptions = chatModel.getDefaultOptions();
        var provider = chatModel.getClass().getSimpleName().replace("ChatModel", "");

        try {
            var name = (String)FieldUtils.readField(chatModelDefaultOptions, "model", true);
            return "%s (%s)".formatted(provider, capitalize(name));
        } catch (Exception e) {
            try {
                var name = (String)FieldUtils.readField(chatModelDefaultOptions, "deploymentName", true);
                return "%s (%s)".formatted(provider, capitalize(name));
            } catch (Exception e2) {
                return  provider;
            }
        }
    }
}