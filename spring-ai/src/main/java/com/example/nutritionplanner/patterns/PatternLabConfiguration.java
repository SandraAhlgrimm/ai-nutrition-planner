package com.example.nutritionplanner.patterns;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("patterns")
@EnableScheduling
class PatternLabConfiguration {
    PatternLabConfiguration(Environment environment) {
        long providers = Arrays.stream(environment.getActiveProfiles())
                .filter(Set.of("openai", "azure", "ollama")::contains).count();
        if (providers != 1) {
            throw new IllegalStateException("Select patterns alongside exactly one provider profile: openai, azure, or ollama");
        }
    }
}
