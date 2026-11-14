package com.example.nutritionplanner;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.hitl.FormBindingRequest;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.util.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private final AgentPlatform agentPlatform;
    private final ModelProvider modelProvider;
    private final Environment environment;
    private final ConcurrentHashMap<String, HitlHandler> hitlHandlers = new ConcurrentHashMap<>();

    NutritionPlannerUiController(AgentPlatform agentPlatform, ModelProvider modelProvider, Environment environment,
                                 TemplateEngine templateEngine, AsyncTaskExecutor taskExecutor) {
        super(templateEngine, taskExecutor);
        this.agentPlatform = agentPlatform;
        this.modelProvider = modelProvider;
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
            var hitlHandler = new HitlHandler(form -> sendEvent(interactionId, "fragments/hitl", Map.of("form", form)));
            hitlHandlers.put(interactionId, hitlHandler);
            try {
                var process = AgentInvocation.create(agentPlatform, WeeklyPlan.class)
                        .run(Map.of("username", username, "request", request));

                // The process pauses with status WAITING whenever an action calls WaitFor.formSubmission(...)
                while (process.getStatus() == AgentProcessStatusCode.WAITING) {
                    hitlHandler.handle((FormBindingRequest<?>) process.lastResult(), process);
                    process.run();
                }

                sendEvent(interactionId, "fragments/plan", Map.of("plan", process.resultOfType(WeeklyPlan.class)));
                completeInteraction(interactionId);
            } finally {
                hitlHandlers.remove(interactionId);
            }
        });
    }

    @PostMapping("/interaction/{interactionId}/answers")
    @ResponseBody
    String provideAnswers(@PathVariable String interactionId, @RequestParam Map<String, String> answers) {
        var handler = hitlHandlers.get(interactionId);
        if (handler != null) handler.provideAnswers(answers);
        return "";
    }

    private String getAiModelName() {
        var provider = environment.matchesProfiles("azure") ? "Azure" : "OpenAI";
        var llm = modelProvider.getLlm(ModelSelectionCriteria.getPlatformDefault());
        return "%s (%s)".formatted(provider, capitalize(llm.getName()));
    }
}
