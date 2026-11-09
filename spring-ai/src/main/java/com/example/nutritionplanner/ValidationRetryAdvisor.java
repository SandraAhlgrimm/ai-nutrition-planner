package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.function.Function;

// More advanced example by the Spring AI team available here: https://github.com/spring-projects/spring-ai-examples/blob/main/advisors/evaluation-recursive-advisor-demo/src/main/java/com/example/advisor/SelfRefineEvaluationAdvisor.java
class ValidationRetryAdvisor<T> implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ValidationRetryAdvisor.class);

    private static final int MAX_RETRIES = 3;

    private final Function<T, ValidationResult> validator;
    private final BeanOutputConverter<T> converter;

    ValidationRetryAdvisor(Class<T> responseType, Function<T, ValidationResult> validator) {
        this.validator = validator;
        this.converter = new BeanOutputConverter<>(responseType);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var response = chain.nextCall(request);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Tool calls are executed by an outer advisor (e.g. Tool Search Advisor), only validate final answers
            if (response.chatResponse() == null || response.chatResponse().hasToolCalls()) {
                return response;
            }

            var entity = toEntity(response);
            var validationResult = validator.apply(entity);
            if (validationResult.allPassed()) {
                log.info("Validation passed");
                return response;
            }

            log.info("Validation failed (attempt {}/{}), revising...", attempt, MAX_RETRIES);
            request = withValidationFeedback(request, entity, validationResult);
            response = chain.copy(this).nextCall(request);
        }
        log.warn("Max retries ({}) reached, returning last response", MAX_RETRIES);
        return response;
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