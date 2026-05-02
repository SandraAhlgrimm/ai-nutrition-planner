package com.example.nutritionplanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class AskUserQuestionHandler implements AskUserQuestionTool.QuestionHandler {

    private final AtomicReference<CompletableFuture<List<Answer>>> pendingResponse = new AtomicReference<>();
    private final Consumer<List<AskUserQuestionTool.Question>> questionHandler;

    AskUserQuestionHandler(Consumer<List<AskUserQuestionTool.Question>> questionHandler) {
        this.questionHandler = questionHandler;
    }

    @Override
    public Map<String, String> handle(List<AskUserQuestionTool.Question> questions) {
        var future = new CompletableFuture<List<Answer>>();
        pendingResponse.set(future);
        questionHandler.accept(questions);
        try {
            return future.get(5, TimeUnit.MINUTES).stream()
                    .collect(Collectors.toMap(Answer::question, Answer::answer));
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout waiting for user response", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void provideAnswers(List<Answer> answers) {
        var future = pendingResponse.getAndSet(null);
        if (future != null) future.complete(answers);
    }

    record Answer(String question, String answer) {}
}