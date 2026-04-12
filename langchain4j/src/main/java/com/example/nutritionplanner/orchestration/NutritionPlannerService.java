package com.nutritionplanner.orchestration;

import com.nutritionplanner.agent.NutritionPlannerWorkflow;
import com.nutritionplanner.model.UserProfile;
import com.nutritionplanner.model.UserProfileProperties;
import com.nutritionplanner.model.WeeklyPlan;
import com.nutritionplanner.model.WeeklyPlanRequest;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single service owning all nutrition plan creation — both synchronous (REST API)
 * and SSE-streaming (browser UI) paths.
 * <p>
 * Uses a ThreadLocal to bridge per-request SseEmitter state into the
 * singleton workflow's AgentListener (listeners run in the calling thread).
 */
@Service
public class NutritionPlannerService {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    static final ThreadLocal<SseEmitter> CURRENT_EMITTER = new ThreadLocal<>();

    private static final Map<String, String> AGENT_LABELS = Map.of(
            "fetchSeasonalIngredients", "🔍 Fetching seasonal ingredients…",
            "createMealPlan", "🍳 Creating meal plan…",
            "validate", "✅ Validating nutrition…",
            "reviseMealPlan", "🔄 Revising meal plan…"
    );

    private final NutritionPlannerWorkflow workflow;
    private final UserProfileProperties userProfileProperties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, WeeklyPlan> resultCache = new ConcurrentHashMap<>();

    public NutritionPlannerService(NutritionPlannerWorkflow workflow,
                                   UserProfileProperties userProfileProperties) {
        this.workflow = workflow;
        this.userProfileProperties = userProfileProperties;
    }

    // ---- Shared prep logic ----

    private record WorkflowInputs(UserProfile userProfile, String month,
                                   String country, String additionalInstructions) {}

    private WorkflowInputs prepareInputs(WeeklyPlanRequest request, String username) {
        var userProfile = userProfileProperties.getUserProfile(username);
        var month = LocalDate.now().getMonth().toString();
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);
        var additionalInstructions = request.additionalInstructions() != null
                ? request.additionalInstructions() : "";
        return new WorkflowInputs(userProfile, month, country, additionalInstructions);
    }

    // ---- Synchronous path (REST API) ----

    public WeeklyPlan createPlan(WeeklyPlanRequest request, String username) {
        log.info("Starting meal plan creation for user: {}", username);
        var inputs = prepareInputs(request, username);
        log.info("Delegating to agentic workflow — month: {}, country: {}", inputs.month(), inputs.country());
        return workflow.createNutritionPlan(
                inputs.userProfile(), request, inputs.month(), inputs.country(), inputs.additionalInstructions());
    }

    // ---- SSE streaming path (browser UI) ----

    public SseEmitter streamPlan(WeeklyPlanRequest request, String username) {
        var emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            CURRENT_EMITTER.set(emitter);
            try {
                var inputs = prepareInputs(request, username);
                sendProgress(emitter, "⏳ Starting plan generation…");

                WeeklyPlan plan = workflow.createNutritionPlan(
                        inputs.userProfile(), request, inputs.month(), inputs.country(), inputs.additionalInstructions());

                var resultId = UUID.randomUUID().toString();
                resultCache.put(resultId, plan);

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(resultId));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming workflow failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage() != null ? e.getMessage() : "Unknown error"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                CURRENT_EMITTER.remove();
            }
        });

        return emitter;
    }

    /**
     * Retrieves and removes a cached plan result. Returns null if the ID is unknown.
     */
    public WeeklyPlan consumeResult(String resultId) {
        return resultCache.remove(resultId);
    }

    // ---- SSE progress listener (registered on the singleton workflow bean) ----

    private static void sendProgress(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("progress").data(message));
        } catch (IOException e) {
            log.warn("Failed to send SSE progress event", e);
        }
    }

    /**
     * AgentListener registered once on the singleton workflow bean.
     * Reads the per-request SseEmitter from ThreadLocal to push progress events.
     */
    public static AgentListener sseProgressListener() {
        return new AgentListener() {
            @Override
            public boolean inheritedBySubagents() {
                return true;
            }

            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                var emitter = CURRENT_EMITTER.get();
                if (emitter == null) return;
                var label = AGENT_LABELS.getOrDefault(request.agentName(),
                        "⏳ Running " + request.agentName() + "…");
                sendProgress(emitter, label);
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                var emitter = CURRENT_EMITTER.get();
                if (emitter == null) return;
                if ("validate".equals(response.agentName())) {
                    sendProgress(emitter, "✅ Validation step complete");
                }
            }
        };
    }
}
