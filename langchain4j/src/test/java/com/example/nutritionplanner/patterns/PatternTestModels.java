package com.example.nutritionplanner.patterns;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.FinishReason;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

final class PatternTestModels {

    private PatternTestModels() {
    }

    static final class Chat implements ChatModel {
        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        private final Function<ChatRequest, String> answer;

        Chat(Function<ChatRequest, String> answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from(answer.apply(request)))
                    .modelName("scripted-chat").finishReason(FinishReason.STOP).build();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("scripted-chat").build();
        }
    }

    static final class Decision implements DecisionModel {
        final List<DecisionRequest> requests = new CopyOnWriteArrayList<>();
        private final Function<DecisionRequest, DecisionResponse> answer;

        Decision(Function<DecisionRequest, DecisionResponse> answer) {
            this.answer = answer;
        }

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            requests.add(request);
            return answer.apply(request);
        }
    }

    static DecisionResponse probabilities(Map<String, Double> probabilities) {
        var response = DecisionResponse.builder();
        probabilities.forEach((name, probability) -> response.answer(name, YesNoAnswer.from(probability)));
        return response.build();
    }

    static String userText(ChatRequest request) {
        return request.messages().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).map(UserMessage::singleText).reduce("", (first, next) -> first + next);
    }

    static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).as("Concurrent work reached the barrier").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test model interrupted", exception);
        }
    }

    static PatternLab lab(ChatModel chat, DecisionModel decision) {
        return new PatternLab(chat, decision, "ollama", "An explicitly selected decision model is required.");
    }
}
