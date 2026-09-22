package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

class ValidationRetryAdvisor<T> implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ValidationRetryAdvisor.class);

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final Function<T, ValidationResult> validator;
    private final BeanOutputConverter<T> converter;
    private final int maxRetries;
    private final Supplier<String> revisionContext;

    ValidationRetryAdvisor(Class<T> responseType, Function<T, ValidationResult> validator) {
        this(responseType, validator, DEFAULT_MAX_RETRIES);
    }

    ValidationRetryAdvisor(Class<T> responseType, Function<T, ValidationResult> validator, int maxRetries) {
        this(responseType, validator, maxRetries, () -> "");
    }

    ValidationRetryAdvisor(Class<T> responseType, Function<T, ValidationResult> validator, int maxRetries,
                           Supplier<String> revisionContext) {
        if (maxRetries < 0 || maxRetries > DEFAULT_MAX_RETRIES) {
            throw new IllegalArgumentException("At most three revisions are allowed");
        }
        this.validator = Objects.requireNonNull(validator);
        this.converter = new BeanOutputConverter<>(responseType);
        this.maxRetries = maxRetries;
        this.revisionContext = Objects.requireNonNull(revisionContext);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var response = chain.nextCall(request);
        for (int attempt = 0; ; attempt++) {
            var entity = toEntity(response);
            var validationResult = Objects.requireNonNull(validator.apply(entity), "Missing nutrition audit");

            if (validationResult.allPassed()) {
                log.info("ValidationRetryAdvisor: validation passed");
                return response;
            }

            if (attempt == maxRetries) {
                log.warn("Nutrition validation failed after {} audited candidates", attempt + 1);
                throw new NutritionPlanValidationException(attempt + 1, validationResult.feedback());
            }
            log.info("Validation failed; creating revision {}/{}", attempt + 1, maxRetries);
            response = chain.copy(this).nextCall(withValidationFeedback(request, entity, validationResult));
        }
    }

    private ChatClientRequest withValidationFeedback(ChatClientRequest originalRequest, T entity, ValidationResult validationResult) {
        var revisedPrompt = originalRequest.prompt().augmentUserMessage(m -> m.mutate().text("""
            %s

            # Revision
            Revise the candidate below using the audit feedback. Preserve all original
            requested days, meals, dietary profile, seasonal ingredients and instructions.
            Use the user's answers below; do not ask answered questions again.

            # User answers collected so far
            %s

            # Current response
            %s

            # Feedback
            %s
            """.formatted(m.getText(), revisionContext.get(), entity, validationResult.feedback())).build());
        return originalRequest.mutate().prompt(revisedPrompt).build();
    }

    private T toEntity(ChatClientResponse response) {
        var chatResponse = Objects.requireNonNull(response.chatResponse(), "Model returned no response");
        var result = Objects.requireNonNull(chatResponse.getResult(), "Model returned no generation");
        var text = result.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Model returned no structured plan");
        }
        return Objects.requireNonNull(converter.convert(text), "Model returned a null plan");
    }

    @Override
    public String getName() {
        return ValidationRetryAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        // Audit completed candidates, never intermediate tool-call responses.
        return ToolCallingAdvisor.DEFAULT_ORDER - 100;
    }

    interface ValidationResult {
        boolean allPassed();
        String feedback();
    }
}