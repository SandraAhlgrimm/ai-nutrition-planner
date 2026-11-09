package com.example.nutritionplanner;

import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.springframework.util.ClassUtils;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Resumes an agentic system suspended by a {@code @HumanInTheLoop} agent: publishes the expected response
 * type to the UI, blocks until the user submits answers, and completes the pending response with them.
 */
class HitlHandler {

    private final Consumer<Class<? extends Record>> formPublisher;
    private volatile CompletableFuture<Map<String, String>> pendingAnswers = new CompletableFuture<>();

    HitlHandler(Consumer<Class<? extends Record>> formPublisher) {
        this.formPublisher = formPublisher;
    }

    <T> ResultWithAgenticScope<T> handle(ResultWithAgenticScope<T> suspendedResult) {
        var responseId = suspendedResult.agenticScope().pendingResponseIds().iterator().next();
        var responseType = ClassUtils.resolveClassName(responseId, null).asSubclass(Record.class);

        pendingAnswers = new CompletableFuture<>();
        formPublisher.accept(responseType);
        return suspendedResult.completePendingResponse(responseId, toRecord(responseType, pendingAnswers.join()));
    }

    void provideAnswers(Map<String, String> answers) {
        pendingAnswers.complete(answers);
    }

    private static Record toRecord(Class<? extends Record> type, Map<String, String> answers) {
        var components = type.getRecordComponents();
        var parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);
        var values = Arrays.stream(components).map(component -> answers.get(component.getName())).toArray();
        try {
            return type.getDeclaredConstructor(parameterTypes).newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind answers to " + type.getSimpleName(), e);
        }
    }
}
