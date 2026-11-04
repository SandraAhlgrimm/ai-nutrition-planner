package com.example.nutritionplanner.patterns;

import org.springframework.http.HttpStatus;

final class PatternFailure extends RuntimeException {
    private final HttpStatus status;

    PatternFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }

    static PatternFailure invalid(String message) {
        return new PatternFailure(HttpStatus.BAD_REQUEST, message);
    }

    static PatternFailure output(String message) {
        return new PatternFailure(HttpStatus.BAD_GATEWAY, message);
    }

    static PatternFailure limit(String message) {
        return new PatternFailure(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
