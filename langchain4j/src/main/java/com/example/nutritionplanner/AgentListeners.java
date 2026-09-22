package com.example.nutritionplanner;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

interface AgentListeners {

    class LoggingListener implements AgentListener {

        private static final Logger log = LoggerFactory.getLogger(LoggingListener.class);

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            log.info("Agent '{}' invoked with inputs: {}", request.agentName(), request.inputs().keySet());
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            log.info("Agent '{}' completed", response.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            log.error("Agent '{}' failed", error.agentName(), error.error());
        }
    }

    class MicrometerAgentListener implements AgentListener {

        private final MeterRegistry registry;
        private final AtomicInteger activeAgents;
        private final ConcurrentHashMap<InvocationKey, Timer.Sample> activeSamples = new ConcurrentHashMap<>();

        public MicrometerAgentListener(MeterRegistry registry) {
            this.registry = registry;
            this.activeAgents = Objects.requireNonNull(registry.gauge("agent_active", new AtomicInteger(0)));
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            activeAgents.incrementAndGet();
            activeSamples.put(new InvocationKey(request.agenticScope().memoryId(), request.agentId()),
                    Timer.start(registry));

            Counter.builder("agent_invocations_total")
                    .tag("agent", request.agentName())
                    .description("Total agent invocations")
                    .register(registry)
                    .increment();
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            finish(new InvocationKey(response.agenticScope().memoryId(), response.agentId()), response.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            finish(new InvocationKey(error.agenticScope().memoryId(), error.agentId()), error.agentName());
            Counter.builder("agent_errors_total")
                    .tag("agent", error.agentName())
                    .description("Failed agent invocations")
                    .register(registry)
                    .increment();
        }

        private void finish(InvocationKey key, String agentName) {
            var sample = activeSamples.remove(key);
            if (sample != null) {
                activeAgents.decrementAndGet();
                sample.stop(Timer.builder("agent_duration")
                        .tag("agent", agentName)
                        .description("Agent execution duration")
                        .register(registry));
            }
        }

        private record InvocationKey(Object scopeId, String agentId) {}
    }
}
