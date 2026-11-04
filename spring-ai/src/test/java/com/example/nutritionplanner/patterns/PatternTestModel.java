package com.example.nutritionplanner.patterns;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class PatternTestModel implements ChatModel {
    final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final Queue<ChatResponse> replies = new ConcurrentLinkedQueue<>();
    private final AtomicInteger ids = new AtomicInteger();
    private Function<Prompt, ChatResponse> responder = _ -> {
        var reply = replies.poll();
        if (reply == null) throw new IllegalStateException("No scripted model response");
        return reply;
    };

    PatternModels models() {
        return new PatternModels(ChatClient.builder(this), this, new DefaultToolExecutionExceptionProcessor(true),
                ObservationRegistry.NOOP);
    }

    PatternTestModel text(String content) {
        replies.add(answer(content));
        return this;
    }

    PatternTestModel tool(String name, String arguments) {
        replies.add(toolResponse("tool-" + ids.incrementAndGet(), name, arguments));
        return this;
    }

    void respond(Function<Prompt, ChatResponse> responder) {
        this.responder = responder;
    }

    void clear() {
        prompts.clear();
        replies.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return responder.apply(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().model("offline-pattern-model").build();
    }

    static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static ChatResponse toolResponse(String id, String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments))).build())));
    }
}
