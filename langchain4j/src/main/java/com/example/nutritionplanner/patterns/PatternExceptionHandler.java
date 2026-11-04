package com.example.nutritionplanner.patterns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PatternController.class)
@Profile("patterns")
class PatternExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PatternExceptionHandler.class);

    @ExceptionHandler(PatternException.class)
    ProblemDetail patternFailed(PatternException exception) {
        if (exception.status().is5xxServerError()) {
            log.error("Pattern invocation failed: {}", exception.code(), exception);
        } else {
            log.warn("Pattern request rejected: {}", exception.code());
        }
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Pattern invocation failed");
        problem.setProperty("code", exception.code());
        exception.details().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail invalidBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof PatternException failure) {
                return patternFailed(failure);
            }
        }
        return patternFailed(PatternException.invalid("Provide a JSON object with the documented prompt/items fields."));
    }
}
