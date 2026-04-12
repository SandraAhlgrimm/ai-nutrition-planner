package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.function.BiFunction;
import java.util.function.Function;

class ValidationRetryAdvisor<T> implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ValidationRetryAdvisor.class);

    private final Function<T, ValidationResult> validator;
    private final BeanOutputConverter<T> converter;

    ValidationRetryAdvisor(Class<T> responseType, Function<T, ValidationResult> validator) {
        this.validator = validator;
        this.converter = new BeanOutputConverter<>(responseType);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var response = chain.nextCall(request);
        while (true) {
            var entity = toEntity(response);
            var validationResult = validator.apply(entity);

            if (validationResult.allPassed()) {
                log.info("ValidationRetryAdvisor: validation passed");
                return response;
            }

            log.info("ValidationRetryAdvisor: validation failed, revising...");
            request = withValidationFeedback(request, entity, validationResult);
            response = chain.copy(this).nextCall(request);
        }
    }

    private ChatClientRequest withValidationFeedback(ChatClientRequest originalRequest, T entity, ValidationResult validationResult) {
        var revisedPrompt = originalRequest.prompt().augmentUserMessage(m -> m.mutate().text("""
            Revise the response based on the following feedback.

            # Current response
            %s

            # Feedback
            %s
            """.formatted(entity, validationResult.feedback())).build());
        return originalRequest.mutate().prompt(revisedPrompt).build();
    }

    private T toEntity(ChatClientResponse response) {
        return converter.convert(response.chatResponse().getResult().getOutput().getText());
    }

    @Override
    public String getName() {
        return ValidationRetryAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    interface ValidationResult {
        boolean allPassed();
        String feedback();
    }
}