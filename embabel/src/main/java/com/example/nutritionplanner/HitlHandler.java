package com.example.nutritionplanner;

import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.hitl.FormBindingRequest;
import com.embabel.agent.core.hitl.FormResponse;
import com.embabel.ux.form.Form;
import com.embabel.ux.form.FormSubmission;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Answers a form request created by Embabel's {@code WaitFor.formSubmission(...)}: publishes the generated
 * form to the UI, blocks until the user submits answers, and hands them back so the process can resume.
 */
class HitlHandler {

    private final Consumer<Form> formPublisher;
    private volatile CompletableFuture<Map<String, String>> pendingAnswers = new CompletableFuture<>();

    HitlHandler(Consumer<Form> formPublisher) {
        this.formPublisher = formPublisher;
    }

    void handle(FormBindingRequest<?> formRequest, AgentProcess process) {
        var form = formRequest.getPayload();
        pendingAnswers = new CompletableFuture<>();
        formPublisher.accept(form);

        var submission = new FormSubmission(form.getId(), pendingAnswers.join(), UUID.randomUUID().toString(), Instant.now());
        var response = new FormResponse(UUID.randomUUID().toString(), formRequest.getId(), submission, false, Instant.now());
        formRequest.onResponse(response, process);
    }

    void provideAnswers(Map<String, String> answers) {
        pendingAnswers.complete(answers);
    }
}
