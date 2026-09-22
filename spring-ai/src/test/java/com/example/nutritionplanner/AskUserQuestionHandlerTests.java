package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(5)
class AskUserQuestionHandlerTests {
    static final List<Question> QUESTIONS = List.of(new Question(TestPlans.QUESTION, "Cooking",
            List.of(new Option("Quick", "Fast cooking"), new Option("Slow", "Slow cooking")), false));

    @Test
    void missingDuplicateAndMismatchedAnswersDoNotReleaseTheWaitingModel() throws Exception {
        var published = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var handler = new AskUserQuestionHandler(_ -> published.countDown())) {
            var result = executor.submit(() -> handler.handle(QUESTIONS));
            published.await();
            assertThatThrownBy(() -> handler.provideAnswers(List.of())).isInstanceOf(InvalidPlanRequestException.class);
            assertThatThrownBy(() -> handler.provideAnswers(List.of(new AskUserQuestionHandler.Answer("Unknown", "Quick"))))
                    .isInstanceOf(InvalidPlanRequestException.class);
            var answer = new AskUserQuestionHandler.Answer(TestPlans.QUESTION, "Quick");
            assertThatThrownBy(() -> handler.provideAnswers(List.of(answer, answer)))
                    .isInstanceOf(InvalidPlanRequestException.class);
            assertThat(result.isDone()).isFalse();
            handler.provideAnswers(List.of(answer));
            assertThat(result.get()).containsEntry(TestPlans.QUESTION, "Quick");
            assertThatThrownBy(() -> handler.provideAnswers(List.of(answer))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void closeUnblocksPendingQuestionAndPreventsNewQuestions() throws Exception {
        var published = new CountDownLatch(1);
        var handler = new AskUserQuestionHandler(_ -> published.countDown());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> handler.handle(QUESTIONS));
            published.await();
            handler.close();
            assertThatThrownBy(result::get).hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> handler.handle(QUESTIONS)).hasMessage("Interaction is closed");
        }
    }

    @Test
    void timeoutClearsPendingAnswers() {
        try (var handler = new AskUserQuestionHandler(_ -> {}, Duration.ofMillis(1))) {
            assertThatThrownBy(() -> handler.handle(QUESTIONS)).hasMessageContaining("Timed out");
            assertThatThrownBy(() -> handler.provideAnswers(List.of(new AskUserQuestionHandler.Answer("question", "answer"))))
                    .hasMessage("No question is waiting for an answer");
        }
    }

    @Test
    void publicationFailureAlsoClearsPendingAnswers() {
        try (var handler = new AskUserQuestionHandler(_ -> { throw new IllegalStateException("Disconnected"); })) {
            assertThatThrownBy(() -> handler.handle(QUESTIONS)).hasMessage("Disconnected");
            assertThatThrownBy(() -> handler.provideAnswers(List.of(new AskUserQuestionHandler.Answer("question", "answer"))))
                    .hasMessage("No question is waiting for an answer");
        }
    }

    @Test
    void formSupportsSingleMultiSelectAndFreeTextAnswers() {
        var form = new NutritionPlannerUiController.AnswersForm(List.of(
                new NutritionPlannerUiController.AnswerForm("first", List.of("A"), null),
                new NutritionPlannerUiController.AnswerForm("second", List.of("B", "C"), null),
                new NutritionPlannerUiController.AnswerForm("third", List.of("ignored"), "Custom")));
        assertThat(form.toAnswers()).extracting(AskUserQuestionHandler.Answer::answer)
                .containsExactly("A", "B, C", "Custom");
    }
}
