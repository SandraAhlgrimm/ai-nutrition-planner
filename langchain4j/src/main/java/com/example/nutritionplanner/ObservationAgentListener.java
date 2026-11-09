package com.example.nutritionplanner;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records a Micrometer observation per agent invocation, from which Spring Boot derives a span as well as the
 * {@code agent.duration} timer (invocations, durations, errors) and the {@code agent.duration.active} long task timer.
 */
@Component
class ObservationAgentListener implements AgentListener {

    private static final Logger log = LoggerFactory.getLogger(ObservationAgentListener.class);

    private final ObservationRegistry observationRegistry;

    ObservationAgentListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        log.info("Agent '{}' invoked with inputs: {}", request.agentName(), request.inputs().keySet());
        Observation.createNotStarted("agent.duration", observationRegistry)
                .contextualName("agent " + request.agentName())
                .lowCardinalityKeyValue("agent", request.agentName())
                .start()
                .openScope();
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        log.info("Agent '{}' completed", response.agentName());
        stopCurrentObservation(null);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        stopCurrentObservation(error.error());
    }

    // The agent waiting for the user's input doesn't complete regularly
    @Override
    public void onAgenticSystemSuspended(AgenticScope agenticScope) {
        stopCurrentObservation(null);
    }

    private void stopCurrentObservation(@Nullable Throwable error) {
        var scope = observationRegistry.getCurrentObservationScope();
        if (scope == null) return;

        var observation = scope.getCurrentObservation();
        scope.close();
        if (error != null) observation.error(error);
        observation.stop();
    }
}
