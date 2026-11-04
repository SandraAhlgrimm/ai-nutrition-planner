package com.example.nutritionplanner;

import com.embabel.agent.core.NonRetryable;
import com.embabel.common.ai.classification.FailureReason;
import org.jspecify.annotations.Nullable;

final class PatternFailure extends RuntimeException implements NonRetryable {

    enum Reason { LIMIT_EXCEEDED, INVALID_RESPONSE, MODEL_UNAVAILABLE, GOAL_NOT_REACHED }

    private final Reason reason;

    PatternFailure(Reason reason, String message) {
        this(reason, message, null);
    }

    private PatternFailure(Reason reason, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }

    static PatternFailure decision(FailureReason reason) {
        return reason == FailureReason.INVALID_RESPONSE
                ? new PatternFailure(Reason.INVALID_RESPONSE, "The model returned an invalid decision; no route was selected.")
                : new PatternFailure(Reason.MODEL_UNAVAILABLE, "The decision model was unavailable; no route was selected.");
    }

    static PatternFailure execution(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PatternFailure patternFailure) {
                return patternFailure;
            }
        }
        return new PatternFailure(Reason.MODEL_UNAVAILABLE, "The pattern example could not complete.", failure);
    }
}
