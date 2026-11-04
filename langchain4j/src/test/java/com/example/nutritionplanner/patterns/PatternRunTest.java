package com.example.nutritionplanner.patterns;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.example.nutritionplanner.patterns.PatternTestModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternRunTest {

    @Test
    void boundedPlainTextRequestsDoNotMutateTheSelectedModelsDefaults() {
        var defaults = DefaultChatRequestParameters.builder().modelName("selected-model")
                .maxOutputTokens(2_048).responseFormat(ResponseFormat.JSON).build();
        var captured = new AtomicReference<ChatRequest>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return defaults;
            }

            @Override
            public ChatResponse doChat(ChatRequest request) {
                captured.set(request);
                return ChatResponse.builder().aiMessage(AiMessage.from("advice")).build();
            }
        };
        try (var run = new PatternRun(1, 0)) {
            assertThat(run.invoke(() -> run.chatModel(model).chat("cooking advice"))).isEqualTo("advice");
            assertThat(captured.get().parameters().modelName()).isEqualTo("selected-model");
            assertThat(captured.get().parameters().responseFormat()).isEqualTo(ResponseFormat.TEXT);
            assertThat(captured.get().parameters().maxOutputTokens()).isEqualTo(700);
            assertThat(model.defaultRequestParameters().responseFormat()).isEqualTo(ResponseFormat.JSON);
            assertThat(model.defaultRequestParameters().maxOutputTokens()).isEqualTo(2_048);
        }
    }

    @Test
    void modelBudgetIsEnforcedBeforeAnExtraModelCall() {
        var chat = new Chat(request -> "advice");
        try (var run = new PatternRun(1, 0)) {
            var bounded = run.chatModel(chat);
            assertThatThrownBy(() -> run.invoke(() -> {
                bounded.chat("first");
                return bounded.chat("not permitted");
            })).isInstanceOfSatisfying(PatternException.class,
                    failure -> assertThat(failure.code()).isEqualTo("pattern_budget_exceeded"));
            assertThat(chat.requests).hasSize(1);
        }
    }

    @ParameterizedTest
    @MethodSource("unusableOutputs")
    void refusesEmptyOversizedTruncatedAndToolOutputs(ChatResponse response) {
        ChatModel chat = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return response;
            }
        };
        try (var run = new PatternRun(1, 0)) {
            assertThatThrownBy(() -> run.invoke(() -> run.chatModel(chat).chat("some cooking advice")))
                    .isInstanceOfSatisfying(PatternException.class,
                            failure -> assertThat(failure.code()).isEqualTo("invalid_pattern_output"));
        }
    }

    static Stream<ChatResponse> unusableOutputs() {
        return Stream.of(
                ChatResponse.builder().aiMessage(AiMessage.from(" ")).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("x".repeat(PatternRun.MAX_OUTPUT_CHARACTERS + 1))).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("incomplete advice")).finishReason(FinishReason.LENGTH).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("filtered advice")).finishReason(FinishReason.CONTENT_FILTER).build(),
                ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("unexpected").name("not_an_available_tool").arguments("{}").build())).build());
    }

    @Test
    void deadlineInterruptsAnActiveModelAndReturnsAnExplicitTimeout() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var chat = blockingModel(started, interrupted);
        try (var run = new PatternRun(1, 0, Duration.ofSeconds(1))) {
            assertThatThrownBy(() -> run.invoke(() -> run.chatModel(chat).chat("cooking advice")))
                    .isInstanceOfSatisfying(PatternException.class,
                            failure -> assertThat(failure.code()).isEqualTo("pattern_timeout"));
            assertThat(started.getCount()).isZero();
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void callerCancellationPropagatesToModelsAndPreservesTheInterruptFlag() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var failure = new AtomicReference<PatternException>();
        var interruptPreserved = new AtomicBoolean();
        try (var run = new PatternRun(1, 0)) {
            var chat = run.chatModel(blockingModel(started, interrupted));
            var caller = Thread.ofVirtual().start(() -> {
                try {
                    run.invoke(() -> chat.chat("cooking advice"));
                } catch (PatternException exception) {
                    failure.set(exception);
                    interruptPreserved.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                caller.join(Duration.ofSeconds(5));
                assertThat(caller.isAlive()).isFalse();
                assertThat(failure.get()).isNotNull().extracting(PatternException::code).isEqualTo("pattern_cancelled");
                assertThat(interruptPreserved).isTrue();
                assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                caller.interrupt();
            }
        }
    }

    @Test
    void cancellationInterruptsModelsOnTheNativeRoutersExecutorToo() throws Exception {
        var started = new CountDownLatch(2);
        var interrupted = new CountDownLatch(2);
        var decision = new Decision(request -> probabilities(java.util.Map.of(
                "mediterranean", 0.9, "eastAsian", 0.9, "baking", 0.0)));
        var lab = lab(blockingModel(started, interrupted), decision);
        var failure = new AtomicReference<PatternException>();
        var caller = Thread.ofVirtual().start(() -> {
            try {
                lab.route(new PatternModels.PromptRequest("Mediterranean and East Asian ideas"));
            } catch (PatternException exception) {
                failure.set(exception);
            }
        });
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(Duration.ofSeconds(5));
            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get()).isNotNull().extracting(PatternException::code).isEqualTo("pattern_cancelled");
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            caller.interrupt();
        }
    }

    @Test
    void closedRunCannotStartMoreWork() {
        var calls = new AtomicInteger();
        var run = new PatternRun(1, 0);
        run.close();
        assertThatThrownBy(() -> run.invoke(calls::incrementAndGet))
                .isInstanceOfSatisfying(PatternException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pattern_cancelled"));
        assertThat(calls).hasValue(0);
    }

    private static Chat blockingModel(CountDownLatch started, CountDownLatch interrupted) {
        return new Chat(request -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("The test model may only exit through interruption");
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Model interrupted", exception);
            }
        });
    }
}
