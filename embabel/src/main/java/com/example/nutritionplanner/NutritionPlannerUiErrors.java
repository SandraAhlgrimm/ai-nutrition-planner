package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@ControllerAdvice(assignableTypes = NutritionPlannerUiController.class)
class NutritionPlannerUiErrors {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerUiErrors.class);

    @ExceptionHandler(NutritionPlanRejectedException.class)
    ModelAndView rejected(NutritionPlanRejectedException failure) {
        log.warn("Nutrition plan rejected after {} audits", failure.audits());
        return error(failure.getMessage(), HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ExceptionHandler(NutritionPlanningException.class)
    ModelAndView failed(NutritionPlanningException failure) {
        log.error("Nutrition planning failed", failure);
        return error(failure.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ModelAndView invalidRequest(IllegalArgumentException failure) {
        log.warn("Invalid nutrition planning request: {}", failure.getMessage());
        return error(failure.getMessage(), HttpStatus.BAD_REQUEST);
    }

    private ModelAndView error(String message, HttpStatus status) {
        return new ModelAndView("fragments/plan :: error", Map.of("error", message), status);
    }
}
