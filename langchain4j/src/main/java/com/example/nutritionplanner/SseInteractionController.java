package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Streams rendered Thymeleaf fragments to the browser via Server-Sent Events while an interaction
 * (e.g. the creation of a weekly plan) runs in the background.
 */
abstract class SseInteractionController {

    private static final Logger log = LoggerFactory.getLogger(SseInteractionController.class);

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final TemplateEngine templateEngine;
    private final AsyncTaskExecutor taskExecutor;

    SseInteractionController(TemplateEngine templateEngine, AsyncTaskExecutor taskExecutor) {
        this.templateEngine = templateEngine;
        this.taskExecutor = taskExecutor;
    }

    @GetMapping(path = "/interactions/{interactionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter interactionEvents(@PathVariable String interactionId) {
        return emitters.get(interactionId);
    }

    String eventStream(Model model, Consumer<String> interaction) {
        var interactionId = UUID.randomUUID().toString();
        var emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(interactionId, emitter);
        emitter.onCompletion(() -> emitters.remove(interactionId));
        emitter.onTimeout(() -> emitters.remove(interactionId));

        CompletableFuture.runAsync(() -> interaction.accept(interactionId), taskExecutor).exceptionally(e -> {
            log.error("Interaction {} failed", interactionId, e.getCause());
            sendEvent(interactionId, "fragments/error", Map.of("message", e.getCause().toString()));
            completeInteraction(interactionId);
            return null;
        });

        model.addAttribute("interactionId", interactionId);
        return "fragments/events :: events";
    }

    void sendEvent(String interactionId, String template, Map<String, Object> data) {
        var emitter = emitters.get(interactionId);
        if (emitter == null) return;

        var context = new Context();
        context.setVariables(data);
        context.setVariable("interactionId", interactionId);
        try {
            emitter.send(SseEmitter.event().name("content-update").data(templateEngine.process(template, context)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    void completeInteraction(String interactionId) {
        var emitter = emitters.get(interactionId);
        if (emitter != null) emitter.complete();
    }
}
