package com.example.nutritionplanner.patterns;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.nutritionplanner.patterns.PatternModels.*;

@RestController
@Profile("patterns")
@RequestMapping("/api/patterns")
class PatternController {

    private final PatternLab lab;

    PatternController(PatternLab lab) {
        this.lab = lab;
    }

    @GetMapping
    Catalog catalog() {
        return lab.catalog();
    }

    @PostMapping("/decision-routing")
    RoutingResult route(@RequestBody PromptRequest request) {
        return lab.route(request);
    }

    @PostMapping("/confidence-cascade")
    CascadeResult cascade(@RequestBody PromptRequest request) {
        return lab.cascade(request);
    }

    @PostMapping("/parallel-mapper")
    MapperResult map(@RequestBody MapperRequest request) {
        return lab.map(request);
    }
}
