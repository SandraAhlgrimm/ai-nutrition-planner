package com.example.nutritionplanner;

import com.embabel.agent.mcpserver.McpExportToolCallbackPublisher;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true", matchIfMissing = true)
class McpIdentityConfiguration {

    private static final String PRINCIPAL = McpIdentityConfiguration.class.getName() + ".principal";

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper mapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> McpTransportContext.create(Map.of(PRINCIPAL,
                        request.principal().orElseThrow(() ->
                                new AuthenticationCredentialsNotFoundException("MCP requires an authenticated principal")))))
                .build();
    }

    @Bean
    static BeanPostProcessor authenticatedMcpExports() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof McpExportToolCallbackPublisher publisher)) {
                    return bean;
                }
                // Keep Embabel's discovered @AchievesGoal exports; decorate only their transport boundary.
                return new McpExportToolCallbackPublisher() {
                    @Override
                    public List<ToolCallback> getToolCallbacks() {
                        return publisher.getToolCallbacks().stream()
                                .<ToolCallback>map(AuthenticatedToolCallback::new).toList();
                    }

                    @Override
                    public String infoString(@Nullable Boolean verbose, int indent) {
                        return publisher.infoString(verbose, indent);
                    }
                };
            }
        };
    }

    private record AuthenticatedToolCallback(ToolCallback delegate) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String input) {
            throw new AuthenticationCredentialsNotFoundException("MCP request context is required");
        }

        @Override
        public String call(String input, @Nullable ToolContext context) {
            var exchange = McpToolUtils.getMcpExchange(context).orElseThrow(() ->
                    new AuthenticationCredentialsNotFoundException("MCP request context is required"));
            if (!(exchange.transportContext().get(PRINCIPAL) instanceof Principal principal)) {
                throw new AuthenticationCredentialsNotFoundException("MCP authenticated principal is missing");
            }
            return PlannerIdentity.callAs(principal, () -> delegate.call(input, context));
        }
    }
}
