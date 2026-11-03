package com.example.nutritionplanner;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
final class BundledSkills implements AutoCloseable {

    private static final List<String> RESOURCES = List.of(
            "current-month/SKILL.md", "current-month/scripts/current-month.sh");

    private final Path directory;
    private final Skills loadedSkills;

    BundledSkills(ResourceLoader resourceLoader) throws IOException {
        directory = Files.createTempDirectory("nutrition-planner-skills-");
        try {
            for (var name : RESOURCES) {
                var destination = directory.resolve(name);
                Files.createDirectories(destination.getParent());
                try (var source = resourceLoader.getResource("classpath:skills/" + name).getInputStream()) {
                    Files.copy(source, destination);
                }
            }
            loadedSkills = new Skills("nutrition-skills", "Trusted bundled nutrition skills")
                    .withLocalSkill(directory.resolve("current-month").toString())
                    .withScriptExecutionEngine(ProcessSkillScriptExecutionEngine.confinedTo(directory.toString()));
        } catch (IOException | RuntimeException failure) {
            try {
                close();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    Skills forRequest() {
        // Skills tracks activation; keep that state local to one model interaction.
        return loadedSkills.withActivatorDescriptionTail(
                "Call once with no arguments, then execute the script tool named in the skill instructions.");
    }

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
