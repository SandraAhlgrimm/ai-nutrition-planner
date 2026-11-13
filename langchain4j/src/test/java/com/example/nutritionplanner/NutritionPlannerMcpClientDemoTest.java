package com.example.nutritionplanner;

import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NutritionPlannerMcpClientDemoTest {

    private final McpClient client = mock(McpClient.class);
    private final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

    @Test
    void discoveryDoesNotGenerateAPlanAndClosesTheContext() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            when(event.getApplicationContext()).thenReturn(context);

            new NutritionPlannerMcpClientDemo(client, new DefaultApplicationArguments()).run(event);

            verify(client).listTools();
            verify(client, never()).executeTool(any());
            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    void forwardsJsonArgumentsToTheRemoteTool() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            when(event.getApplicationContext()).thenReturn(context);
            var result = mock(ToolExecutionResult.class);
            when(client.executeTool(any())).thenReturn(result);
            var json = "{\"request\":{\"meals\":{\"MONDAY\":[\"LUNCH\"]},\"countryCode\":\"DE\"}}";

            new NutritionPlannerMcpClientDemo(client, new DefaultApplicationArguments("--mcp-request=" + json))
                    .run(event);

            verify(client).executeTool(argThat(request ->
                    request.name().equals("createNutritionPlan") && request.arguments().equals(json)));
            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    void surfacesServerErrorsAndStillClosesTheContext() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            when(event.getApplicationContext()).thenReturn(context);
            when(client.executeTool(any())).thenThrow(new McpApplicationErrorException("Invalid meal request"));

            var demo = new NutritionPlannerMcpClientDemo(client, new DefaultApplicationArguments("--mcp-request={}"));
            assertThatThrownBy(() -> demo.run(event)).isInstanceOf(McpApplicationErrorException.class)
                    .hasMessageContaining("Invalid meal request");
            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    void rejectsARequestFlagWithoutJsonBeforeStartingTheServer() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            when(event.getApplicationContext()).thenReturn(context);

            var demo = new NutritionPlannerMcpClientDemo(client, new DefaultApplicationArguments("--mcp-request"));
            assertThatIllegalArgumentException().isThrownBy(() -> demo.run(event))
                    .withMessageContaining("exactly one --mcp-request");
            verifyNoInteractions(client);
            assertThat(context.isActive()).isFalse();
        }
    }
}
