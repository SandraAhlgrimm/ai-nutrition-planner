package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsResourceTests {
    @TempDir
    Path directory;

    @Test
    void skillLoadsFromClasspathWithoutShellOrFilesystemTools() {
        var skill = SkillsTool.builder().addSkillsResource(new ClassPathResource("skills")).build();
        assertThat(skill.call("{\"command\":\"current-month\"}")).contains("currentMonth", "UTC", "packaged");
    }

    @Test
    void skillLoadsFromJarIncludingBootClassesPrefixWithoutExplodingTheArchive() throws IOException {
        var jar = directory.resolve("application.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("BOOT-INF/classes/skills/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry("BOOT-INF/classes/skills/current-month/SKILL.md"));
            try (var input = new ClassPathResource("skills/current-month/SKILL.md").getInputStream()) {
                input.transferTo(output);
            }
            output.closeEntry();
        }
        var resource = new UrlResource("jar:" + jar.toUri() + "!/BOOT-INF/classes/skills/");
        assertThat(resource.isFile()).isFalse();
        var tool = SkillsTool.builder().addSkillsResource(resource).build();
        assertThat(tool.call("{\"command\":\"current-month\"}")).contains("currentMonth", "packaged");
        try (var paths = Files.list(directory)) {
            assertThat(paths.map(Path::getFileName)).containsExactly(Path.of("application.jar"));
        }
    }

    @Test
    void clockToolUsesCurrentEnglishMonthRatherThanModelKnowledge() {
        var clock = Clock.fixed(Instant.parse("2026-09-30T23:59:59Z"), ZoneOffset.UTC);
        assertThat(new CurrentMonthTool(clock).currentMonth()).isEqualTo("September");
        assertThat(new CurrentMonthTool(Clock.offset(clock, java.time.Duration.ofSeconds(1))).currentMonth()).isEqualTo("October");
    }
}
