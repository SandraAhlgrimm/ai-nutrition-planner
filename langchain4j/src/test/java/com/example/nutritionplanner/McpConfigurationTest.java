package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.spring.McpAutoConfiguration;
import dev.langchain4j.mcp.spring.McpProperties;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpConfigurationTest {

    @Test
    void normalWebStartupKeepsTheUiAndSecurityAndDoesNotStartMcp() {
        new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=test")
                .withUserConfiguration(NutritionPlannerApplication.class)
                .withBean(ChatModel.class, () -> mock(ChatModel.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(NutritionPlannerUiController.class)
                        .hasSingleBean(SecurityFilterChain.class)
                        .doesNotHaveBean(McpServer.class)
                        .doesNotHaveBean(McpClient.class)
                        .doesNotHaveBean(NutritionPlannerMcpTools.class)
                        .doesNotHaveBean(NutritionPlannerMcpClientDemo.class));
    }

    @Test
    void serverProfileExposesOnlyThePlannerFacadeWithoutWebComponentsOrAClient() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=mcp-server")
                .withUserConfiguration(NutritionPlannerApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(McpServer.class)
                            .hasSingleBean(NutritionPlannerMcpTools.class)
                            .doesNotHaveBean(SecurityFilterChain.class)
                            .doesNotHaveBean(NutritionPlannerUiController.class)
                            .doesNotHaveBean(McpClient.class);
                    assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
                            .isEqualTo("none");
                    assertThat(context.getEnvironment().getProperty("spring.main.banner-mode")).isEqualTo("off");
                });
    }

    @Test
    void clientProfileCreatesNamedLazyClientAndOptInToolProvider() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
                .withPropertyValues("spring.profiles.active=mcp-client",
                        "NUTRITION_MCP_SERVER_JAR=/not-a-real-server.jar")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(McpClient.class)
                            .hasSingleBean(McpToolProvider.class)
                            .doesNotHaveBean(McpServer.class);
                    assertThat(context.getBean("mcpClient.nutrition", McpClient.class).key()).isEqualTo("nutrition");
                    var properties = context.getBean(McpProperties.class).getClients().get("nutrition");
                    assertThat(properties.getTransport()).isEqualTo(McpProperties.Transport.STDIO);
                    assertThat(properties.getInitializationTimeout()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.getToolExecutionTimeout()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getCommand()).containsExactly(
                            System.getProperty("java.home") + "/bin/java", "-jar", "/not-a-real-server.jar",
                            "--spring.profiles.active=openai,mcp-server");
                });
    }
}
