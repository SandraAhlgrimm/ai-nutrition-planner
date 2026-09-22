package com.example.nutritionplanner;

import com.embabel.agent.api.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BundledSkillsTests {

    @Test
    void nativeSkillLoadsAndExecutesFromJarResourcesWithoutGetFile(@TempDir Path directory) throws IOException {
        var jar = directory.resolve("bundled-skills.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var name : List.of("skills/current-month/SKILL.md", "skills/current-month/scripts/current-month.sh")) {
                output.putNextEntry(new JarEntry(name));
                try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
                    assertNotNull(input);
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
        try (var loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            var resources = new DefaultResourceLoader(loader);
            assertEquals("jar", resources.getResource("classpath:skills/current-month/SKILL.md").getURL().getProtocol());
            assertThrows(IOException.class, () -> resources.getResource("classpath:skills/current-month/SKILL.md").getFile());
            Path materialized;
            try (var bundled = new BundledSkills(resources)) {
                var skills = bundled.forRequest();
                materialized = skills.getSkills().getFirst().getBasePath();
                assertNotSame(skills, bundled.forRequest());
                var tools = skills.asIndividualReferences().stream().flatMap(reference -> reference.tools().stream()).toList();
                var activate = tools.stream().filter(tool -> tool.getDefinition().getName().equals("current_month"))
                        .findFirst().orElseThrow();
                assertTrue(assertInstanceOf(Tool.Result.Text.class, activate.call("{}")).getContent().contains("current-month.sh"));
                var script = tools.stream().filter(tool -> tool.getDefinition().getDescription().contains("Execute the current-month.sh"))
                        .findFirst().orElseThrow();
                var before = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                var result = assertInstanceOf(Tool.Result.Text.class, script.call("{}")).getContent();
                var after = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                assertTrue(result.contains("exit code 0"), result);
                assertTrue(result.contains(before) || result.contains(after), result);
            }
            assertFalse(Files.exists(materialized), "Extracted skill files must be removed on shutdown");
        }
    }

    @Test
    void missingBundledResourcesFailExplicitly() {
        try (var loader = new URLClassLoader(new URL[0], null)) {
            assertThrows(IOException.class, () -> new BundledSkills(new DefaultResourceLoader(loader)));
        } catch (IOException failure) {
            fail(failure);
        }
    }
}
