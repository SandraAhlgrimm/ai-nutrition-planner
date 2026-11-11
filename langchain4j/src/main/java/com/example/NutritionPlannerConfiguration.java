package com.example;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class NutritionPlannerConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .httpBasic(Customizer.withDefaults()).build();
    }

    // Picked up by the auto-configured ChatModel to record an observation (span and metrics) per LLM call
    @Bean
    ChatModelListener observationChatModelListener(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        return new ObservationChatModelListener(observationRegistry, meterRegistry);
    }

    // Resuming after a human-in-the-loop suspension invokes the root agent again, which the AgentMonitor
    // would wrongly register as a nested invocation of its own still ongoing execution
    @Bean
    AgentMonitor agentMonitor() {
        return new AgentMonitor() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                var isResumedRootAgent = agentRequest.agent().parent() == null
                        && ongoingExecutionFor(agentRequest.agenticScope()) != null;
                if (!isResumedRootAgent) super.beforeAgentInvocation(agentRequest);
            }
        };
    }
}
