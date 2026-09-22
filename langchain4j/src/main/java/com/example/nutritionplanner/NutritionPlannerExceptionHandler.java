package com.example.nutritionplanner;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NutritionPlannerController.class)
class NutritionPlannerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerExceptionHandler.class);

    @ExceptionHandler(PlanValidationException.class)
    ProblemDetail validationFailed(PlanValidationException exception) {
        log.warn("Meal plan validation exhausted");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Meal plan validation failed");
        problem.setProperty("feedback", exception.audit().consolidatedFeedback());
        problem.setProperty("violations", exception.audit().violations());
        return problem;
    }

    @ExceptionHandler(AgentInvocationException.class)
    ProblemDetail generationFailed(AgentInvocationException exception) {
        log.error("Meal plan generation failed", exception);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The AI workflow could not generate or audit the meal plan. No plan was returned.");
        problem.setTitle("Meal plan generation failed");
        return problem;
    }
}
