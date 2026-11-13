package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillsDirectoryTests {

    private static final String SKILL = "---\nname: current-month\n---\nReturns the current month.\n";
    private static final String SCRIPT = "#!/bin/sh\nLC_ALL=C date +%B\n";

    @TempDir
    Path tempDir;

    @Test
    void of_usesDirectoryOnFileSystemAsItIs() throws IOException {
        var skillsResource = new ClassPathResource("skills");

        var directory = SkillsDirectory.of(skillsResource);

        assertEquals(skillsResource.getFile().toPath(), directory);
        assertTrue(Files.isRegularFile(directory.resolve("current-month/SKILL.md")));
        assertTrue(Files.isRegularFile(directory.resolve("current-month/scripts/current-month.sh")));
    }

    @Test
    void of_copiesSkillsFromJarToDirectory() throws IOException {
        var jar = tempDir.resolve("app.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            addDirectory(out, "skills/");
            addDirectory(out, "skills/current-month/");
            addDirectory(out, "skills/current-month/scripts/");
            addFile(out, "skills/current-month/SKILL.md", SKILL);
            addFile(out, "skills/current-month/scripts/current-month.sh", SCRIPT);
        }

        try (var classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            var skillsResource = new ClassPathResource("skills", classLoader);
            assertTrue(skillsResource.getURL().toString().startsWith("jar:"));

            var directory = SkillsDirectory.of(skillsResource);

            assertNotEquals(tempDir, directory);
            assertEquals(SKILL, Files.readString(directory.resolve("current-month/SKILL.md")));
            assertEquals(SCRIPT, Files.readString(directory.resolve("current-month/scripts/current-month.sh")));
        }
    }

    private static void addDirectory(JarOutputStream out, String name) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.closeEntry();
    }

    private static void addFile(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(UTF_8));
        out.closeEntry();
    }
}
