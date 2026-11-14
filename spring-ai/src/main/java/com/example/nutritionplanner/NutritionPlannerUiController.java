package com.example.nutritionplanner;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.util.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private final ChatModel chatModel;
    private final NutritionPlannerAgent nutritionPlannerAgent;
    private final Environment environment;
    private final ConcurrentHashMap<String, AskUserQuestionHandler> questionHandlers = new ConcurrentHashMap<>();

    NutritionPlannerUiController(ChatModel chatModel, NutritionPlannerAgent nutritionPlannerAgent, Environment environment,
                                 TemplateEngine templateEngine, AsyncTaskExecutor taskExecutor) {
        super(templateEngine, taskExecutor);
        this.chatModel = chatModel;
        this.nutritionPlannerAgent = nutritionPlannerAgent;
        this.environment = environment;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        return "login";
    }

    @GetMapping("/")
    String form(Model model) {
        model.addAttribute("aiModel", getAiModelName());
        model.addAttribute("weeklyPlanRequest", new WeeklyPlanRequest());
        return "index";
    }

    @PostMapping("/plan")
    String createPlan(@ModelAttribute WeeklyPlanRequest request, Principal principal, Model model) {
        var username = principal.getName();

        return eventStream(model, interactionId -> {
            var askUserQuestionHandler = new AskUserQuestionHandler(questions -> 
                    sendEvent(interactionId, "fragments/hitl", Map.of("questions", questions)));
            questionHandlers.put(interactionId, askUserQuestionHandler);
            try {
                var plan = nutritionPlannerAgent.createNutritionPlan(username, request, askUserQuestionHandler);
                sendEvent(interactionId, "fragments/plan", Map.of("plan", plan));
                completeInteraction(interactionId);
            } finally {
                questionHandlers.remove(interactionId);
            }
        });
    }

    @PostMapping("/interaction/{interactionId}/answers")
    @ResponseBody
    String provideAnswers(@PathVariable String interactionId, @ModelAttribute AnswersForm answersForm) {
        var handler = questionHandlers.get(interactionId);
        if (handler != null) handler.provideAnswers(answersForm.answers());
        return "";
    }

    private String getAiModelName() {
        var provider = environment.matchesProfiles("azure") ? "Azure" : "OpenAI";
        return "%s (%s)".formatted(provider, capitalize(chatModel.getOptions().getModel()));
    }

    record AnswersForm(List<AskUserQuestionHandler.Answer> answers) {}
}
