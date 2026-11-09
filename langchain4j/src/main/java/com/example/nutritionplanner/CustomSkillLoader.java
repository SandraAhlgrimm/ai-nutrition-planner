package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.SkillResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Skill loaded from the classpath including its scripts, which ClassPathSkillLoader skips.
 * Each script is provided as a skill-scoped tool, so the LLM can only execute it after activating the skill.
 */
record CustomSkillLoader(Skill skill, Map<String, String> scripts) implements Skill {

    static List<Skill> loadSkills(String path) {
        return ClassPathSkillLoader.loadSkills(path).stream()
                .<Skill>map(skill -> new CustomSkillLoader(skill, loadScripts(path + "/" + skill.name())))
                .toList();
    }

    private static Map<String, String> loadScripts(String skillPath) {
        try {
            var scripts = new HashMap<String, String>();
            for (var script : new PathMatchingResourcePatternResolver().getResources("classpath:%s/scripts/*".formatted(skillPath))) {
                scripts.put("scripts/" + script.getFilename(), script.getContentAsString(StandardCharsets.UTF_8));
            }
            return scripts;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String name() {
        return skill.name();
    }

    @Override
    public String description() {
        return skill.description();
    }

    @Override
    public String content() {
        return skill.content();
    }

    @Override
    public List<SkillResource> resources() {
        return skill.resources();
    }

    @Override
    public List<ToolProvider> toolProviders() {
        var tools = new HashMap<ToolSpecification, ToolExecutor>();
        scripts.forEach((scriptPath, script) -> tools.put(ToolSpecification.builder()
                        .name("run_" + scriptPath.replaceAll("\\W", "_"))
                        .description("Executes %s of the %s skill and returns its output".formatted(scriptPath, name()))
                        .build(),
                new ShellScriptExecutor(scriptPath, script)));
        return List.of(request -> new ToolProviderResult(tools));
    }

    /**
     * Executes the script content by piping it into the shell, so no file on the file system is required
     */
    record ShellScriptExecutor(String scriptPath, String script) implements ToolExecutor {

        private static final Logger log = LoggerFactory.getLogger(ShellScriptExecutor.class);

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            try {
                var process = new ProcessBuilder("sh", "-s").redirectErrorStream(true).start();
                try (var stdin = process.getOutputStream()) {
                    stdin.write(script.getBytes(StandardCharsets.UTF_8));
                }
                var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                var exitCode = process.waitFor();
                log.info("ShellScriptExecutor finished {} with exit code {} and output {}", scriptPath, exitCode, output);
                return exitCode == 0 ? output : "Script failed with exit code %d: %s".formatted(exitCode, output);
            } catch (IOException | InterruptedException e) {
                throw new ToolExecutionException(e);
            }
        }
    }
}
