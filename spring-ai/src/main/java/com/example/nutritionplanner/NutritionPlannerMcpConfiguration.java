package com.example.nutritionplanner;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
class NutritionPlannerMcpConfiguration {

    static final String PRINCIPAL_NAME = "nutritionPlanner.principalName";

    @Bean
    WebMvcStreamableServerTransportProvider authenticatedMcpTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper mapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    var principal = request.principal().orElseThrow(() ->
                            new AuthenticationCredentialsNotFoundException("An authenticated MCP principal is required"));
                    // MCP handlers may run outside the servlet's security-context thread.
                    return McpTransportContext.create(Map.of(PRINCIPAL_NAME, principal.getName()));
                })
                .build();
    }
}
