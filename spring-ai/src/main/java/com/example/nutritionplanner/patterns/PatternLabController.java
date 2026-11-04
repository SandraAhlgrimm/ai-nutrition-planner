package com.example.nutritionplanner.patterns;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@Profile("patterns")
@RequestMapping("/api/patterns")
class PatternLabController {
    private final PatternLabService lab;
    private final PatternMemoryService memory;
    private final PatternConfirmationService confirmations;

    PatternLabController(PatternLabService lab, PatternMemoryService memory, PatternConfirmationService confirmations) {
        this.lab = lab;
        this.memory = memory;
        this.confirmations = confirmations;
    }

    @GetMapping
    Catalog catalog() {
        return new Catalog("Culinary demonstrations only; not validated plans or medical advice.",
                List.of(new Pattern("routing", "/api/patterns/routing", "application composition", 2,
                                "Typed specialist selection, then one specialist answer."),
                        new Pattern("orchestrator-workers", "/api/patterns/orchestrator-workers", "application composition", 5,
                                "One planner, 1-3 concurrent workers, one synthesis; no native workflow engine."),
                        new Pattern("session-memory", "/api/patterns/memory/sessions", "native Spring AI 2.1 RC", 2,
                                "Create without a model call; POST /{id}/turns. Eight turns, one read-only tool per turn. "
                                        + "After more than three active turns, preserve the newest two complete turns."),
                        new Pattern("tool-confirmation", "/api/patterns/confirmations", "native Spring AI 2.1 RC", 2,
                                "Create a pending demo note; POST /{id}/decision with APPROVE or DENY to resume once. "
                                        + "No operation executes before approval.")));
    }

    @PostMapping("/routing")
    PatternLabService.RoutingResult route(@RequestBody PatternRequest request) {
        return lab.route(request);
    }

    @PostMapping("/orchestrator-workers")
    PatternLabService.WorkersResult workers(@RequestBody PatternRequest request) {
        return lab.orchestrate(request);
    }

    @PostMapping("/memory/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    PatternMemoryService.MemoryView createMemory(Principal principal) {
        return memory.create(principal.getName());
    }

    @GetMapping("/memory/sessions/{id}")
    PatternMemoryService.MemoryView memory(Principal principal, @PathVariable String id) {
        return memory.get(principal.getName(), id);
    }

    @PostMapping("/memory/sessions/{id}/turns")
    PatternMemoryService.MemoryReply turn(Principal principal, @PathVariable String id, @RequestBody PatternRequest request) {
        return memory.turn(principal.getName(), id, request);
    }

    @PostMapping("/confirmations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PatternConfirmationService.ConfirmationView confirm(Principal principal, @RequestBody PatternRequest request) {
        return confirmations.start(principal.getName(), request);
    }

    @GetMapping("/confirmations/{id}")
    PatternConfirmationService.ConfirmationView confirmation(Principal principal, @PathVariable String id) {
        return confirmations.get(principal.getName(), id);
    }

    @PostMapping("/confirmations/{id}/decision")
    PatternConfirmationService.ConfirmationView decision(Principal principal, @PathVariable String id,
                                                         @RequestBody PatternConfirmationService.DecisionRequest request) {
        return confirmations.decide(principal.getName(), id, request);
    }

    record Catalog(String notice, List<Pattern> patterns) {}
    record Pattern(String id, String path, String mechanism, int maxModelCalls, String description) {}
}
