package com.example.nutritionplanner;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    @InitBinder
    void bindRecordsByConstructor(WebDataBinder binder) {
        binder.setDeclarativeBinding(true);
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
        request.validate();

        return eventStream(model, username, interactionId -> {
            var askUserQuestionHandler = new AskUserQuestionHandler(questions -> 
                    sendEvent(interactionId, "fragments/hitl", Map.of("questions", questions)));
            questionHandlers.put(interactionId, askUserQuestionHandler);

            var plan = nutritionPlannerAgent.createNutritionPlan(username, request, askUserQuestionHandler);

            sendEvent(interactionId, "fragments/plan", Map.of("plan", plan));
        }, interactionId -> {
            var handler = questionHandlers.remove(interactionId);
            if (handler != null) handler.close();
        });
    }

    @PostMapping("/interaction/{interactionId}/answers")
    @ResponseBody
    ResponseEntity<Void> provideAnswers(@PathVariable String interactionId, @ModelAttribute AnswersForm answersForm,
                                       Principal principal) {
        requireOwner(interactionId, principal);
        var handler = questionHandlers.get(interactionId);
        if (handler == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No pending questions");
        try {
            handler.provideAnswers(answersForm.toAnswers());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    private String getAiModelName() {
        var provider = chatModel.getClass().getSimpleName().replace("ChatModel", "");
        var name = chatModel.getOptions().getModel();
        return name == null ? provider : "%s (%s)".formatted(provider, name);
    }

    record AnswersForm(List<AnswerForm> answers) {
        List<AskUserQuestionHandler.Answer> toAnswers() {
            if (answers == null || answers.stream().anyMatch(java.util.Objects::isNull)) {
                throw new InvalidPlanRequestException("Answers are required for every question");
            }
            return answers.stream().map(answer -> new AskUserQuestionHandler.Answer(answer.question(),
                    answer.customAnswer() != null && !answer.customAnswer().isBlank()
                            ? answer.customAnswer() : answer.answer() == null ? "" : String.join(", ", answer.answer())))
                    .toList();
        }
    }

    record AnswerForm(String question, List<String> answer, String customAnswer) {}
}
