package com.example.nutritionplanner;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private final ChatModel chatModel;
    private final NutritionPlannerAgent nutritionPlannerAgent;
    private final ConcurrentHashMap<String, AskUserQuestionHandler> questionHandlers = new ConcurrentHashMap<>();

    NutritionPlannerUiController(ChatModel chatModel, NutritionPlannerAgent nutritionPlannerAgent, TemplateEngine templateEngine) {
        super(templateEngine);
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

            var plan = nutritionPlannerAgent.createNutritionPlan(username, request, askUserQuestionHandler);

            questionHandlers.remove(interactionId);
            sendEvent(interactionId, "fragments/plan", Map.of("plan", plan));
            completeInteraction(interactionId);
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
        var chatModelDefaultOptions = chatModel.getDefaultOptions();
        var provider = chatModel.getClass().getSimpleName().replace("ChatModel", "");
        try {
            var name = (String) FieldUtils.readField(chatModelDefaultOptions, "model", true);
            return "%s (%s)".formatted(provider, capitalize(name));
        } catch (Exception e) {
            try {
                var name = (String) FieldUtils.readField(chatModelDefaultOptions, "deploymentName", true);
                return "%s (%s)".formatted(provider, capitalize(name));
            } catch (Exception e2) {
                return provider;
            }
        }
    }

    record AnswersForm(List<AskUserQuestionHandler.Answer> answers) {}
}
