package com.example.nutritionplanner.patterns;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PatternProfileTests {
    @ParameterizedTest
    @ValueSource(strings = {"patterns", "patterns,openai,azure", "patterns,openai,ollama"})
    void labRejectsMissingOrAmbiguousProviders(String profiles) {
        new ApplicationContextRunner().withUserConfiguration(PatternLabConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Select patterns alongside exactly one provider profile: openai, azure, or ollama"));
    }
}
