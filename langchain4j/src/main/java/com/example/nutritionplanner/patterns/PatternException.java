package com.example.nutritionplanner.patterns;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.Map;

final class PatternException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, ?> details;

    PatternException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    PatternException(HttpStatus status, String code, String message, Map<String, ?> details,
                     @Nullable Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = Map.copyOf(details);
    }

    static PatternException invalid(String message) {
        return new PatternException(HttpStatus.BAD_REQUEST, "invalid_pattern_request", message);
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    Map<String, ?> details() {
        return details;
    }
}
