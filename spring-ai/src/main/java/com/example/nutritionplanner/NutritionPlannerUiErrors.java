package com.example.nutritionplanner;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(assignableTypes = NutritionPlannerUiController.class)
class NutritionPlannerUiErrors {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerUiErrors.class);

    @ExceptionHandler({InvalidPlanRequestException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalidRequest(Exception exception, Model model, HttpServletResponse response) {
        var message = exception instanceof InvalidPlanRequestException
                ? exception.getMessage() : "Select valid days and meals and provide the requested answers";
        log.info("Invalid browser nutrition request: {}", message);
        model.addAttribute("message", message);
        response.setHeader("HX-Retarget", "#request-errors");
        response.setHeader("HX-Reswap", "innerHTML");
        return "fragments/error :: error";
    }
}
