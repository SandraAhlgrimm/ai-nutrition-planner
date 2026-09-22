package com.example.nutritionplanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class AskUserQuestionHandler implements AskUserQuestionTool.QuestionHandler, AutoCloseable {

    private final AtomicReference<PendingQuestions> pendingResponse = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<List<AskUserQuestionTool.Question>> questionHandler;
    private final Duration timeout;

    AskUserQuestionHandler(Consumer<List<AskUserQuestionTool.Question>> questionHandler) {
        this(questionHandler, Duration.ofMinutes(5));
    }

    AskUserQuestionHandler(Consumer<List<AskUserQuestionTool.Question>> questionHandler, Duration timeout) {
        this.questionHandler = questionHandler;
        this.timeout = timeout;
    }

    @Override
    public Map<String, String> handle(List<AskUserQuestionTool.Question> questions) {
        if (closed.get()) throw new IllegalStateException("Interaction is closed");
        var pending = new PendingQuestions(List.copyOf(questions), new CompletableFuture<>());
        if (!pendingResponse.compareAndSet(null, pending)) {
            throw new IllegalStateException("Another question is already waiting for an answer");
        }
        try {
            if (closed.get()) throw new IllegalStateException("Interaction is closed");
            questionHandler.accept(questions);
            return pending.answer().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for user answers", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for user answers", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("User interaction was cancelled", e.getCause());
        } finally {
            pendingResponse.compareAndSet(pending, null);
        }
    }

    void provideAnswers(List<Answer> answers) {
        var pending = pendingResponse.get();
        if (pending == null) throw new IllegalStateException("No question is waiting for an answer");
        var response = new LinkedHashMap<String, String>();
        if (answers == null || answers.isEmpty()) throw new InvalidPlanRequestException("Answers are required");
        for (var answer : answers) {
            if (answer == null || answer.question() == null || answer.answer() == null || answer.answer().isBlank()
                    || response.putIfAbsent(answer.question(), answer.answer()) != null) {
                throw new InvalidPlanRequestException("Every question requires exactly one non-blank answer");
            }
        }
        if (response.size() != pending.questions().size()
                || pending.questions().stream().anyMatch(q -> !response.containsKey(q.question()))) {
            throw new InvalidPlanRequestException("Answers must match all pending questions");
        }
        if (!pending.answer().complete(Map.copyOf(response))) {
            throw new IllegalStateException("Questions were already answered");
        }
    }

    @Override
    public void close() {
        closed.set(true);
        var pending = pendingResponse.getAndSet(null);
        if (pending != null) pending.answer().completeExceptionally(new IllegalStateException("Interaction closed"));
    }

    record Answer(String question, String answer) {}

    private record PendingQuestions(List<AskUserQuestionTool.Question> questions,
                                    CompletableFuture<Map<String, String>> answer) {}
}