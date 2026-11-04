package com.example.nutritionplanner.patterns;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.FinishReason;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class PatternRun implements AutoCloseable {

    static final Duration TIMEOUT = Duration.ofMinutes(2);
    static final int MAX_OUTPUT_CHARACTERS = 4_000;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger chatCalls = new AtomicInteger();
    private final AtomicInteger decisionCalls = new AtomicInteger();
    private final Set<Thread> modelThreads = ConcurrentHashMap.newKeySet();
    private final int maxChatCalls;
    private final int maxDecisionCalls;
    private final Duration timeout;

    PatternRun(int maxChatCalls, int maxDecisionCalls) {
        this(maxChatCalls, maxDecisionCalls, TIMEOUT);
    }

    PatternRun(int maxChatCalls, int maxDecisionCalls, Duration timeout) {
        this.maxChatCalls = maxChatCalls;
        this.maxDecisionCalls = maxDecisionCalls;
        this.timeout = timeout;
    }

    <T> T invoke(Callable<T> work) {
        checkActive();
        var task = executor.submit(work);
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            close();
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new PatternException(HttpStatus.SERVICE_UNAVAILABLE, "pattern_cancelled",
                    "The pattern invocation was cancelled. No result was returned.");
        } catch (TimeoutException exception) {
            close();
            task.cancel(true);
            throw new PatternException(HttpStatus.GATEWAY_TIMEOUT, "pattern_timeout",
                    "The pattern invocation exceeded its two-minute deadline. No result was returned.");
        } catch (ExecutionException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof PatternException failure) {
                    throw failure;
                }
            }
            throw new PatternException(HttpStatus.BAD_GATEWAY, "pattern_execution_failed",
                    "The model or native workflow failed. No advisory result was returned.",
                    Map.of(), exception.getCause());
        }
    }

    ChatModel chatModel(ChatModel delegate) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return modelCall(chatCalls, maxChatCalls, () -> {
                    var parameters = request.parameters().overrideWith(DefaultChatRequestParameters.builder()
                            .responseFormat(ResponseFormat.TEXT).maxOutputTokens(700).build());
                    var response = delegate.chat(request.toBuilder().parameters(parameters).build());
                    if ((response.finishReason() != null && response.finishReason() != FinishReason.STOP)
                            || response.aiMessage().hasToolExecutionRequests()
                            || response.aiMessage().text() == null || response.aiMessage().text().isBlank()
                            || response.aiMessage().text().length() > MAX_OUTPUT_CHARACTERS) {
                        throw new PatternException(HttpStatus.BAD_GATEWAY, "invalid_pattern_output",
                                "The model returned empty, incomplete, filtered, oversized or unexpected tool output.");
                    }
                    return response;
                });
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return delegate.defaultRequestParameters();
            }

            @Override
            public Set<Capability> supportedCapabilities() {
                return delegate.supportedCapabilities();
            }
        };
    }

    DecisionModel decisionModel(DecisionModel delegate, BiConsumer<DecisionRequest, DecisionResponse> onResponse) {
        return request -> modelCall(decisionCalls, maxDecisionCalls, () -> {
            var response = delegate.decide(request);
            for (String name : request.questions().keySet()) {
                if (response.isRefused(name)) {
                    throw new PatternException(HttpStatus.UNPROCESSABLE_CONTENT, "decision_inconclusive",
                            "The decision model refused a required judgment. No result was accepted.");
                }
                double probability = response.yesNo(name).probability();
                if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
                    throw new PatternException(HttpStatus.BAD_GATEWAY, "invalid_pattern_output",
                            "The decision model returned an invalid probability.");
                }
            }
            onResponse.accept(request, response);
            return response;
        });
    }

    ExecutorService executor() {
        return executor;
    }

    PatternModels.Calls calls() {
        return new PatternModels.Calls(chatCalls.get(), decisionCalls.get());
    }

    private <T> T modelCall(AtomicInteger calls, int maximum, Supplier<T> work) {
        var thread = Thread.currentThread();
        modelThreads.add(thread);
        try {
            checkActive();
            if (calls.incrementAndGet() > maximum) {
                throw new PatternException(HttpStatus.BAD_GATEWAY, "pattern_budget_exceeded",
                        "The pattern exceeded its model-call budget. No result was returned.");
            }
            T result = work.get();
            checkActive();
            return result;
        } finally {
            modelThreads.remove(thread);
        }
    }

    private void checkActive() {
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new PatternException(HttpStatus.SERVICE_UNAVAILABLE, "pattern_cancelled",
                    "The pattern invocation was cancelled. No result was returned.");
        }
    }

    @Override
    public void close() {
        closed.set(true);
        modelThreads.forEach(Thread::interrupt);
        executor.shutdownNow();
    }
}
