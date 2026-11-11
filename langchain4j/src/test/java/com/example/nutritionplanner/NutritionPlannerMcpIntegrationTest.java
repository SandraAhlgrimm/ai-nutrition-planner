package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.spring.McpAutoConfiguration;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NutritionPlannerMcpIntegrationTest {

    @Test
    void autoConfiguredClientLaunchesServerAndCallsTheRealWorkflowWithMockedAi() throws Exception {
        var serverProcess = new AtomicReference<ProcessHandle>();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
                .withPropertyValues("spring.profiles.active=mcp-client",
                        "langchain4j.mcp.clients.nutrition.command[0]=" + System.getProperty("java.home") + "/bin/java",
                        "langchain4j.mcp.clients.nutrition.command[1]=-cp",
                        "langchain4j.mcp.clients.nutrition.command[2]=" + System.getProperty("surefire.test.class.path"),
                        "langchain4j.mcp.clients.nutrition.command[3]=" + getClass().getName(),
                        "langchain4j.mcp.clients.nutrition.command[4]=--spring.profiles.active=mcp-server,mcp-test-server",
                        "langchain4j.mcp.clients.nutrition.tool-execution-timeout=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(McpToolProvider.class);
                    var client = context.getBean("mcpClient.nutrition", McpClient.class);
                    assertThat(serverProcesses()).isEmpty();
                    assertThat(client.listTools()).extracting(ToolSpecification::name)
                            .containsExactly("createNutritionPlan");
                    var children = serverProcesses();
                    assertThat(children).hasSize(1);
                    serverProcess.set(children.getFirst());

                    assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                            .name("createNutritionPlan")
                            .arguments("""
                                    {"request":{"meals":{},"countryCode":"DE","additionalInstructions":""}}
                                    """)
                            .build()))
                            .isInstanceOf(McpApplicationErrorException.class)
                            .hasMessageContaining("at least one day");

                    var result = client.executeTool(ToolExecutionRequest.builder()
                            .name("createNutritionPlan")
                            .arguments("""
                                    {"request":{"meals":{"MONDAY":["LUNCH"]},"countryCode":"DE",
                                                "additionalInstructions":"Quick meals"}}
                                    """)
                            .build());
                    assertThat(result.isError()).isFalse();
                    var plan = new JsonMapper().readTree(result.resultText());
                    assertThat(plan.path("days").size()).isEqualTo(1);
                    assertThat(plan.at("/days/0/day").asText()).isEqualTo("MONDAY");
                    assertThat(plan.at("/days/0/lunch/name").asText()).isEqualTo("Cabbage soup");
                });

        assertThat(serverProcess.get()).isNotNull();
        serverProcess.get().onExit().get(15, TimeUnit.SECONDS);
        assertThat(serverProcess.get().isAlive()).isFalse();
    }

    private List<ProcessHandle> serverProcesses() {
        return ProcessHandle.current().children()
                .filter(process -> process.info().commandLine().orElse("").contains(getClass().getName()))
                .toList();
    }

    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{NutritionPlannerApplication.class, MockModelConfiguration.class}, args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("mcp-test-server")
    static class MockModelConfiguration {

        @Bean
        ChatModel chatModel() {
            var responses = new ConcurrentLinkedQueue<>(List.of(
                    """
                    {"ingredients":["cabbage","carrots"]}
                    """,
                    """
                    {"days":[{"day":"MONDAY","breakfast":null,"dinner":null,
                      "lunch":{"name":"Cabbage soup","ingredients":[{"name":"cabbage","quantity":"200","unit":"g"}],
                        "nutrition":{"calories":350,"proteinGrams":12.0,"carbGrams":40.0,
                                     "fatGrams":15.0,"sodiumMg":400},
                        "instructions":"Simmer the vegetables.","prepTimeMinutes":20}}]}
                    """,
                    """
                    {"allPassed":true,"violations":[],"consolidatedFeedback":""}
                    """));
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(AiMessage.from(responses.remove())).build();
                }
            };
        }
    }
}
