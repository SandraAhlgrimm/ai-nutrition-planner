package com.example.nutritionplanner;

import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

abstract class SseInteractionController {

    private static final Logger log = LoggerFactory.getLogger(SseInteractionController.class);
    private static final Duration INTERACTION_TIMEOUT = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Interaction> interactions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("nutrition-interaction-timeouts").factory());
    private final TemplateEngine templateEngine;

    SseInteractionController(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @GetMapping(path = "/interactions/{interactionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter interactionEvents(@PathVariable String interactionId, Principal principal) {
        var interaction = requireOwner(interactionId, principal);
        if (!interaction.started.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interaction already connected");
        }
        interaction.worker.start();
        return interaction.emitter;
    }

    String eventStream(Model model, String owner, Consumer<String> publisher, Consumer<String> cleanup) {
        var interactionId = UUID.randomUUID().toString();
        var context = ContextSnapshotFactory.builder().build().captureAll();
        // Send the application deadline error before the container makes the emitter unwritable.
        var emitter = new SseEmitter(INTERACTION_TIMEOUT.plusSeconds(30).toMillis());
        var worker = Thread.ofVirtual().unstarted(context.wrap(() -> {
            try {
                publisher.accept(interactionId);
                completeInteraction(interactionId);
            } catch (Exception e) {
                log.error("Nutrition interaction {} failed", interactionId, e);
                failInteraction(interactionId, e instanceof NutritionPlanValidationException rejected
                        ? rejected.getMessage() + ". " + rejected.feedback()
                        : "Nutrition planning failed. Please try again.");
            } finally {
                cleanup.accept(interactionId);
            }
        }));
        var interaction = new Interaction(owner, emitter, worker, () -> cleanup.accept(interactionId));
        interactions.put(interactionId, interaction);
        emitter.onCompletion(() -> removeInteraction(interactionId));
        emitter.onTimeout(() -> {
            log.warn("SSE transport timed out for interaction {}", interactionId);
            removeInteraction(interactionId);
        });
        emitter.onError(error -> removeInteraction(interactionId));
        interaction.timeout = timeouts.schedule(() -> expireInteraction(interactionId),
                INTERACTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        model.addAttribute("interactionId", interactionId);
        return "fragments/events :: events";
    }

    protected Interaction requireOwner(String interactionId, Principal principal) {
        var interaction = interactions.get(interactionId);
        if (interaction == null || !interaction.owner.equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interaction not found");
        }
        return interaction;
    }

    protected void sendEvent(String interactionId, String template, Map<String, Object> data) {
        var interaction = interactions.get(interactionId);
        if (interaction == null) throw new CancellationException("Interaction closed");

        var context = new Context();
        context.setVariables(data);
        context.setVariable("interactionId", interactionId);
        var content = templateEngine.process(template, context);
        try {
            interaction.emitter.send(SseEmitter.event().name("content-update").data(content));
        } catch (IOException e) {
            interaction.emitter.completeWithError(e);
            removeInteraction(interactionId);
            throw new IllegalStateException("Could not send interaction event", e);
        }
    }

    protected void completeInteraction(String interactionId) {
        var interaction = interactions.get(interactionId);
        if (interaction != null) {
            try {
                interaction.emitter.send(SseEmitter.event().name("done").data(""));
                interaction.emitter.complete();
            } catch (IOException e) {
                interaction.emitter.completeWithError(e);
                removeInteraction(interactionId);
                throw new IllegalStateException("Could not complete interaction", e);
            }
        }
    }

    private void failInteraction(String interactionId, String message) {
        var interaction = interactions.get(interactionId);
        if (interaction == null) return;
        try {
            sendEvent(interactionId, "fragments/error", Map.of("message", message));
            completeInteraction(interactionId);
        } catch (RuntimeException e) {
            log.warn("Could not deliver interaction failure", e);
            interaction.emitter.completeWithError(e);
            removeInteraction(interactionId);
        }
    }

    private void expireInteraction(String interactionId) {
        failInteraction(interactionId, "Nutrition planning timed out. Please try again.");
        removeInteraction(interactionId);
    }

    private void removeInteraction(String interactionId) {
        var interaction = interactions.remove(interactionId);
        if (interaction != null) {
            if (interaction.timeout != null) interaction.timeout.cancel(false);
            interaction.cleanup.run();
            if (interaction.worker != Thread.currentThread()) interaction.worker.interrupt();
        }
    }

    @PreDestroy
    void closeInteractions() {
        interactions.forEach((id, interaction) -> {
            interaction.emitter.complete();
            removeInteraction(id);
        });
        timeouts.shutdownNow();
    }

    protected static final class Interaction {
        private final String owner;
        private final SseEmitter emitter;
        private final Thread worker;
        private final Runnable cleanup;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile @Nullable ScheduledFuture<?> timeout;

        private Interaction(String owner, SseEmitter emitter, Thread worker, Runnable cleanup) {
            this.owner = owner;
            this.emitter = emitter;
            this.worker = worker;
            this.cleanup = cleanup;
        }
    }
}
