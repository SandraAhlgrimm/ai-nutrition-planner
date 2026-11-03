package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NutritionPlannerController.class)
class NutritionPlannerRestErrors {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerRestErrors.class);

    @ExceptionHandler(NutritionPlanRejectedException.class)
    ProblemDetail rejected(NutritionPlanRejectedException failure) {
        log.warn("Nutrition plan rejected after {} audits", failure.audits());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, failure.getMessage());
        problem.setTitle("Nutrition plan failed its final audit");
        problem.setProperty("audits", failure.audits());
        problem.setProperty("revisions", failure.revisions());
        problem.setProperty("violations", failure.validationResult().violations());
        return problem;
    }

    @ExceptionHandler(NutritionPlanningException.class)
    ProblemDetail failed(NutritionPlanningException failure) {
        log.error("Nutrition planning failed", failure);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, failure.getMessage());
    }
}
