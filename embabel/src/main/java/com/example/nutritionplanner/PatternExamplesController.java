package com.example.nutritionplanner;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

@RestController
@Profile("patterns")
@RequestMapping("/api/patterns")
class PatternExamplesController {

    private static final Logger log = LoggerFactory.getLogger(PatternExamplesController.class);
    static final String DISCLAIMER = "Culinary pattern demonstration only; not a validated nutrition plan or medical advice.";

    private final TypedRoutingExample routing;
    private final SupervisorExample supervisor;
    private final HybridExample hybrid;

    PatternExamplesController(TypedRoutingExample routing, SupervisorExample supervisor, HybridExample hybrid) {
        this.routing = routing;
        this.supervisor = supervisor;
        this.hybrid = hybrid;
    }

    @GetMapping
    List<PatternDescriptor> catalog() {
        return List.of(
                new PatternDescriptor("typed-routing", "Typed classification and decisions",
                        "Experimental 1.5.4 decision services; explicit selected/no-match/inconclusive/failure outcomes.",
                        "/api/patterns/typed-routing", 2),
                new PatternDescriptor("supervisor", "Native typed Supervisor",
                        "LLM-selected pantry and preparation workers in a request-local scope.",
                        "/api/patterns/supervisor", SupervisorExample.MAX_MODEL_CALLS),
                new PatternDescriptor("hybrid", "Native Hybrid research reducer",
                        "Value-ranked local evidence actions, NIRVANA plus a real terminal goal.",
                        "/api/patterns/hybrid", 2));
    }

    @PostMapping("/typed-routing")
    PatternResponse<TypedRoutingExample.RoutingResult> routing(@Valid @RequestBody PatternRequest request, Principal principal) {
        return run("typed-routing", principal, () -> routing.route(request));
    }

    @PostMapping("/supervisor")
    PatternResponse<SupervisorExample.SupervisorResult> supervisor(@Valid @RequestBody PatternRequest request, Principal principal) {
        return run("supervisor", principal, () -> supervisor.run(request));
    }

    @PostMapping("/hybrid")
    PatternResponse<HybridExample.HybridResult> hybrid(@Valid @RequestBody PatternRequest request, Principal principal) {
        return run("hybrid", principal, () -> hybrid.run(request));
    }

    private <T> PatternResponse<T> run(String pattern, Principal principal, Supplier<T> example) {
        return PlannerIdentity.callAs(principal, () -> {
            try {
                return new PatternResponse<>(pattern, PlannerIdentity.username(), example.get(), DISCLAIMER);
            } catch (RuntimeException failure) {
                throw PatternFailure.execution(failure);
            }
        });
    }

    @ExceptionHandler(PatternFailure.class)
    ProblemDetail failed(PatternFailure failure) {
        log.warn("Pattern example failed: {}", failure.reason());
        var status = switch (failure.reason()) {
            case LIMIT_EXCEEDED, GOAL_NOT_REACHED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case INVALID_RESPONSE, MODEL_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, failure.getMessage());
        problem.setTitle("Pattern example did not complete");
        problem.setProperty("reason", failure.reason());
        return problem;
    }

    record PatternDescriptor(String id, String name, String mechanism, String path, int maxModelCalls) {}
    record PatternResponse<T>(String pattern, String requestedBy, T result, String disclaimer) {}
}
