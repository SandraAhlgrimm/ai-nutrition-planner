package com.example.nutritionplanner.patterns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PatternLabController.class)
@Profile("patterns")
class PatternLabErrors {
    private static final Logger log = LoggerFactory.getLogger(PatternLabErrors.class);

    @ExceptionHandler(PatternFailure.class)
    ProblemDetail rejected(PatternFailure exception) {
        log.warn("Pattern request stopped: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail failed(RuntimeException exception) {
        log.warn("Pattern execution failed", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The pattern could not be completed. No validated nutrition plan was produced.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail invalidJson(HttpMessageNotReadableException exception) {
        log.info("Invalid pattern request JSON");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid pattern JSON. Supply a non-blank prompt of at most 1000 characters, or an APPROVE/DENY decision.");
    }
}
