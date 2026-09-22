package com.example;

import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.time.Clock;

@Configuration
class NutritionPlannerConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getServletPath().startsWith("/api/")
                                || request.getServletPath().equals("/mcp")))
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    LuceneToolIndex toolIndex() {
        return new LuceneToolIndex(0.4f);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(true);
    }
}
