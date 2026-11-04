package com.example.nutritionplanner;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nutrition-planner.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
class NutritionPlannerMcpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerMcpConfiguration.class);
    private static final String PRINCIPAL_NAME = "nutritionPlanner.principalName";
    private static final String REQUEST_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "days": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 7,
                  "items": {
                    "type": "object",
                    "properties": {
                      "day": {
                        "type": "string",
                        "enum": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
                      },
                      "meals": {
                        "type": "array",
                        "minItems": 1,
                        "uniqueItems": true,
                        "items": { "type": "string", "enum": ["BREAKFAST", "LUNCH", "DINNER"] }
                      }
                    },
                    "required": ["day", "meals"],
                    "additionalProperties": false
                  }
                },
                "countryCode": { "type": "string", "pattern": "^[A-Z]{2}$" },
                "additionalInstructions": { "type": ["string", "null"] }
              },
              "required": ["days", "countryCode"],
              "additionalProperties": false
            }
            """;

    @Bean
    @Order(0)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/mcp")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    HttpServletStatelessServerTransport mcpTransport(JsonMapper mapper) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .messageEndpoint("/mcp")
                .httpHeaderValidator(DefaultServerTransportSecurityValidator.builder().build())
                .contextExtractor(request -> {
                    var principal = request.getUserPrincipal();
                    if (principal == null) {
                        throw new AuthenticationCredentialsNotFoundException("MCP requires an authenticated principal");
                    }
                    return McpTransportContext.create(Map.of(PRINCIPAL_NAME, principal.getName()));
                })
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
            HttpServletStatelessServerTransport transport) {
        return new ServletRegistrationBean<>(transport, "/mcp");
    }

    @Bean(destroyMethod = "closeGracefully")
    McpStatelessSyncServer nutritionPlannerMcpServer(HttpServletStatelessServerTransport transport,
            NutritionPlannerAgent planner, JsonMapper mapper) {
        var tool = McpSchema.Tool.builder("createNutritionPlan", new JacksonMcpJsonMapper(mapper), REQUEST_SCHEMA)
                .description("Create a validated meal plan for the authenticated user, using the requested days, meals, "
                        + "country and instructions. Allows at most three revisions and fails if validation is exhausted.")
                .build();
        return McpServer.sync(transport)
                .serverInfo("langchain4j-nutrition-planner", "0.0.1")
                // This blocking servlet already runs on Boot's virtual request threads.
                .immediateExecution(true)
                .toolCall(tool, (context, call) -> createPlan(context, call, planner, mapper))
                .build();
    }

    private static McpSchema.CallToolResult createPlan(McpTransportContext context, McpSchema.CallToolRequest call,
                                                       NutritionPlannerAgent planner, JsonMapper mapper) {
        if (!(context.get(PRINCIPAL_NAME) instanceof String username)) {
            throw new AuthenticationCredentialsNotFoundException("MCP authenticated principal is missing");
        }
        try {
            var request = mapper.convertValue(call.arguments(), WeeklyPlanRequest.class);
            var plan = planner.createNutritionPlan(username, request);
            return McpSchema.CallToolResult.builder()
                    .structuredContent(plan)
                    .addTextContent(mapper.writeValueAsString(plan))
                    .build();
        } catch (InvalidPlanRequestException exception) {
            log.info("Invalid MCP meal plan request: {}", exception.getMessage());
            return error(exception.getMessage());
        } catch (PlanValidationException exception) {
            log.warn("MCP meal plan validation exhausted");
            return error(exception.getMessage() + " " + exception.audit().consolidatedFeedback());
        } catch (AgentInvocationException exception) {
            log.error("MCP meal plan generation failed", exception);
            return error("The AI workflow could not generate or audit the meal plan. No plan was returned.");
        } catch (JacksonException exception) {
            log.error("MCP meal plan serialization failed", exception);
            return error("The meal plan request or result could not be serialized. No plan was returned.");
        }
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
