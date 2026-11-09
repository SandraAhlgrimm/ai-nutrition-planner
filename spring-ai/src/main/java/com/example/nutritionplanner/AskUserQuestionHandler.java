package com.example.nutritionplanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toMap;

/**
 * Bridges the {@link AskUserQuestionTool} to the UI: publishes the questions and blocks the agent
 * until the user submits answers.
 */
class AskUserQuestionHandler implements AskUserQuestionTool.QuestionHandler {

    private final Consumer<List<AskUserQuestionTool.Question>> questionPublisher;
    private volatile CompletableFuture<Map<String, String>> pendingAnswers = new CompletableFuture<>();

    AskUserQuestionHandler(Consumer<List<AskUserQuestionTool.Question>> questionPublisher) {
        this.questionPublisher = questionPublisher;
    }

    @Override
    public Map<String, String> handle(List<AskUserQuestionTool.Question> questions) {
        pendingAnswers = new CompletableFuture<>();
        questionPublisher.accept(questions);
        return pendingAnswers.join();
    }

    void provideAnswers(List<Answer> answers) {
        pendingAnswers.complete(answers.stream().collect(toMap(Answer::question, Answer::answer)));
    }

    record Answer(String question, String answer) {}
}
