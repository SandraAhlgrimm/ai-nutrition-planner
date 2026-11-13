package com.example.nutritionplanner;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.util.StringUtils.capitalize;

@Controller
class NutritionPlannerUiController extends SseInteractionController {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerUiController.class);

    private final ChatModel chatModel;
    private final NutritionPlannerAgent nutritionPlannerAgent;
    private final AgentMonitor agentMonitor;
    private final Environment environment;
    private final ConcurrentHashMap<String, HitlHandler> hitlHandlers = new ConcurrentHashMap<>();

    NutritionPlannerUiController(ChatModel chatModel, NutritionPlannerAgent nutritionPlannerAgent, AgentMonitor agentMonitor,
                                 Environment environment, TemplateEngine templateEngine, AsyncTaskExecutor taskExecutor) {
        super(templateEngine, taskExecutor);
        this.chatModel = chatModel;
        this.nutritionPlannerAgent = nutritionPlannerAgent;
        this.agentMonitor = agentMonitor;
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
            var hitlHandler = new HitlHandler(responseType -> sendEvent(interactionId, "fragments/hitl",
                    Map.of("title", "A few questions to tailor your plan", "fields", formFields(responseType))));
            hitlHandlers.put(interactionId, hitlHandler);
            try {
                var result = nutritionPlannerAgent.createNutritionPlan(interactionId, username, request);

                // The agentic system suspends whenever a @HumanInTheLoop agent returns a SuspendedResponse
                while (result.suspended()) {
                    result = hitlHandler.handle(result);
                }

                log.info("Plan creation finished with agents invocations {}", agentMonitor.successfulExecutions().getFirst());
                sendEvent(interactionId, "fragments/plan", Map.of("plan", result.result()));
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
        var provider = environment.acceptsProfiles(Profiles.of("azure"))
                ? "Azure OpenAI"
                : chatModel.getClass().getSimpleName().replace("ChatModel", "");
        return "%s (%s)".formatted(provider, capitalize(chatModel.defaultRequestParameters().modelName()));
    }

    // Derives the form fields from the record components, e.g. maxCookingTime -> "Max cooking time",
    // using the @Description of the component as placeholder
    private static List<FormField> formFields(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> {
            var name = component.getName();
            var description = ReflectionUtils.findField(type, name).getAnnotation(Description.class);
            return new FormField(name, capitalize(name.replaceAll("([A-Z])", " $1").toLowerCase()),
                    description == null ? "" : String.join(" ", description.value()));
        }).toList();
    }

    record FormField(String id, String label, String placeholder) {}
}
