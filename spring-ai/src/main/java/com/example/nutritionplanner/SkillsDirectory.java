package com.example.nutritionplanner;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The model runs skill scripts through the shell, which needs files on the file system, not classpath entries inside
 * a packaged jar. Resources on the file system are used as they are, the ones inside a jar are copied to a temporary
 * directory and their scripts are made executable.
 */
final class SkillsDirectory {

    private SkillsDirectory() {
    }

    static Path of(Resource skillsResource) throws IOException {
        if (skillsResource.isFile()) {
            return skillsResource.getFile().toPath();
        }
        var rootUrl = skillsResource.getURL().toString().replaceAll("/+$", "") + "/";
        var directory = Files.createTempDirectory("skills-");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileSystemUtils.deleteRecursively(directory.toFile())));
        for (var resource : new PathMatchingResourcePatternResolver().getResources(rootUrl + "**")) {
            var relativePath = UriUtils.decode(resource.getURL().toString().substring(rootUrl.length()), UTF_8);
            if (!relativePath.isEmpty() && !relativePath.endsWith("/")) {
                var file = directory.resolve(relativePath);
                Files.createDirectories(file.getParent());
                try (var content = resource.getInputStream()) {
                    Files.copy(content, file);
                }
                if (relativePath.contains("/scripts/")) {
                    file.toFile().setExecutable(true);
                }
            }
        }
        return directory;
    }
}
