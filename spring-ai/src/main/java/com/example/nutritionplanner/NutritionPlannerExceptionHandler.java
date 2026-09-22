package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class NutritionPlannerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerExceptionHandler.class);

    @ExceptionHandler(NutritionPlanValidationException.class)
    ProblemDetail validationFailed(NutritionPlanValidationException exception) {
        log.warn("Nutrition plan rejected after {} audits", exception.audits());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Nutrition plan validation failed");
        problem.setProperty("audits", exception.audits());
        problem.setProperty("feedback", exception.feedback());
        return problem;
    }

    @ExceptionHandler(InvalidPlanRequestException.class)
    ProblemDetail invalidRequest(InvalidPlanRequestException exception) {
        log.info("Invalid nutrition plan request: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
