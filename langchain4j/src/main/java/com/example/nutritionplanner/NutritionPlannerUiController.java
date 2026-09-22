package com.example.nutritionplanner;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.model.chat.ChatModel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Controller
class NutritionPlannerUiController {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerUiController.class);
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
            @RequestParam(required = false) @Nullable List<String> monday,
            @RequestParam(required = false) @Nullable List<String> tuesday,
            @RequestParam(required = false) @Nullable List<String> wednesday,
            @RequestParam(required = false) @Nullable List<String> thursday,
            @RequestParam(required = false) @Nullable List<String> friday,
            @RequestParam(required = false) @Nullable List<String> saturday,
            @RequestParam(required = false) @Nullable List<String> sunday,
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

    @ExceptionHandler(PlanValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    String validationFailed(PlanValidationException exception, Model model) {
        log.warn("Meal plan validation exhausted");
        model.addAttribute("error", exception.getMessage());
        model.addAttribute("feedback", exception.audit().consolidatedFeedback());
        return "fragments/plan :: error";
    }

    @ExceptionHandler(AgentInvocationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    String generationFailed(AgentInvocationException exception, Model model) {
        log.error("Meal plan generation failed", exception);
        model.addAttribute("error", "The AI workflow could not generate or audit the meal plan. No plan was returned.");
        return "fragments/plan :: error";
    }

    private void addDay(List<WeeklyPlanRequest.DayPlanRequest> days, DayOfWeek day, @Nullable List<String> meals) {
        if (meals != null && !meals.isEmpty()) {
            days.add(new WeeklyPlanRequest.DayPlanRequest(day,
                    meals.stream().map(WeeklyPlanRequest.MealType::valueOf).toList()));
        }
    }

    private String getAiModelName() {
        var chatModelProvider = chatModel.getClass().getSimpleName().replace("ChatModel", "");
        var modelName = chatModel.defaultRequestParameters().modelName();
        return hasText(modelName) ? "%s (%s)".formatted(chatModelProvider, modelName) : chatModelProvider;
    }
}
