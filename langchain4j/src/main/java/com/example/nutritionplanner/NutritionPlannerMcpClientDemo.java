package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Profile("mcp-client")
class NutritionPlannerMcpClientDemo {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerMcpClientDemo.class);

    private final McpClient client;
    private final ApplicationArguments arguments;

    NutritionPlannerMcpClientDemo(@Qualifier("mcpClient.nutrition") McpClient client,
                                 ApplicationArguments arguments) {
        this.client = client;
        this.arguments = arguments;
    }

    @EventListener(ApplicationReadyEvent.class)
    void run(ApplicationReadyEvent event) {
        try {
            Assert.state(!event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of("mcp-server")),
                    "Run mcp-client and mcp-server in separate processes");
            var requests = arguments.getOptionValues("mcp-request");
            if (requests != null) {
                Assert.isTrue(requests.size() == 1 && !requests.getFirst().isBlank(),
                        "Supply exactly one --mcp-request with JSON tool arguments");
            }

            log.info("MCP tools from '{}': {}", client.key(), client.listTools());
            if (requests != null) {
                var result = client.executeTool(ToolExecutionRequest.builder()
                        .name("createNutritionPlan")
                        .arguments(requests.getFirst())
                        .build());
                if (result.isError()) {
                    throw new IllegalStateException("MCP createNutritionPlan failed: " + result.resultText());
                }
                log.info("MCP nutrition plan: {}", result.resultText());
            }
        } finally {
            event.getApplicationContext().close();
        }
    }
}
