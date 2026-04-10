# AGENTS.md — LangChain4j Nutrition Planner

> **Framework**: LangChain4j 1.12.2-beta22 with the `langchain4j-agentic` module
> **Pattern**: `@Agent` interfaces composed via `sequenceBuilder` / `loopBuilder`

---

## Framework & Dependencies

- **LangChain4j 1.12.2-beta22** with `langchain4j-spring-boot-starter` + `langchain4j-azure-open-ai-spring-boot-starter` + `langchain4j-agentic`
- **Spring Boot 3.5.13** / **Java 25**
- **Azure OpenAI** as the LLM provider
- **Thymeleaf** + **HTMX 2.0.3** (via `htmx-spring-boot-thymeleaf` 4.0.3) + **Tailwind CSS** (CDN) for UI
- **Spring Security** with form login + HTTP Basic
- **Jackson** with `jackson-datatype-jdk8` for `Optional` serialization
- **Actuator** + **OpenTelemetry** (micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp, micrometer-registry-otlp)
- **Playwright** (Java) for end-to-end UI tests

## Azure OpenAI Configuration

All three environment variables are **required** at runtime. Never hardcode them.

```
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key-here
AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

## Build & Run

```bash
cd langchain4j
mvn clean install          # build
mvn test                   # all tests (unit + integration, no live API)
mvn spring-boot:run        # run (requires Azure OpenAI env vars)
```

---

## Agentic Workflow

The nutrition planning workflow uses the `langchain4j-agentic` module's composable agent patterns:

```
sequence(NutritionPlannerWorkflow):
  fetchSeasonalIngredients  → SeasonalIngredientAgent (@Agent)
  createMealPlan            → MealPlanCreatorAgent (@Agent)
  loop(maxIterations=3):
    validate                → NutritionGuardAgent (@Agent + @Tool)
    reviseMealPlan          → MealPlanReviserAgent (@Agent)
    exitCondition: validationResult.allPassed()
  outputKey: "weeklyPlan"
```

### Agents

| Agent | Interface | Output Key | Description |
|-------|-----------|------------|-------------|
| `SeasonalIngredientAgent` | `@Agent` with `@V` params | `seasonalIngredients` | Fetches seasonal produce for the given country/month |
| `MealPlanCreatorAgent` | `@Agent` with `@V` params | `weeklyPlan` | Creates initial meal plan from ingredients + user profile |
| `NutritionGuardAgent` | `@Agent` with `@Tool` support | `validationResult` | Validates plan against dietary rules using tool-based nutrition calculations |
| `MealPlanReviserAgent` | `@Agent` with `@V` params | `weeklyPlan` | Revises failing recipes based on validation feedback |

### Composition (`AgenticWorkflowConfig`)

Agents are composed in a Spring `@Configuration` class using the builder API:

```java
var validationLoop = AgenticServices.loopBuilder()
    .subAgents(guardAgent, reviserAgent)
    .maxIterations(maxIterations)
    .exitCondition(scope -> {
        var result = (NutritionAuditValidationResult) scope.readState("validationResult", null);
        return result != null && result.allPassed();
    })
    .build();

return AgenticServices.sequenceBuilder(NutritionPlannerWorkflow.class)
    .subAgents(seasonalAgent, creatorAgent, validationLoop)
    .outputKey("weeklyPlan")
    .listener(new MicrometerAgentListener(meterRegistry))
    .listener(StreamingPlannerService.sseProgressListener())
    .build();
```

### Key Design Decision: Parameter Names

> **CRITICAL**: The `langchain4j-agentic` framework resolves sub-agent arguments from `AgenticScope` by **Java parameter name**, NOT by `@V` annotation value. Parameter names in `@Agent` interfaces MUST match the scope keys exactly.

### NutritionTools (`@Tool`)

The `NutritionGuardAgent` uses `NutritionTools` — a stateful `@Tool` class that provides nutrition calculation methods. The current plan is injected via an `AgentListener.beforeAgentInvocation()` callback before each guard invocation.

---

## SSE Streaming

The UI shows real-time agent progress via Server-Sent Events instead of a blank spinner:

- **`StreamingPlannerService`** — runs the workflow async on a virtual thread, using a `ThreadLocal<SseEmitter>` to bridge per-request state into the singleton workflow's `AgentListener`
- **`SseProgressController`** — `GET /plan/stream` endpoint returning `SseEmitter`
- **Result caching** — on completion, the plan is cached server-side with a UUID key. The `complete` SSE event sends only the ID; the JS fetches `/plan/result?id={uuid}` for the Thymeleaf-rendered HTML (avoids running the workflow twice)

### SSE Event Flow

1. JS intercepts form submit → opens `EventSource` to `/plan/stream?params`
2. Server sends `progress` events as each agent starts/completes
3. On workflow completion → sends `complete` event with result UUID
4. JS stops spinner, fetches `/plan/result?id=<uuid>` for rendered HTML
5. On error → `error` event with message, spinner stops

---

## Observability

### Micrometer Metrics (`MicrometerAgentListener`)

A custom `AgentListener` bridges agent lifecycle events to Micrometer:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `agent_active` | Gauge | — | Currently executing agents |
| `agent_invocations_total` | Counter | `agent` | Total invocations per agent name |
| `agent_duration` | Timer | `agent` | Execution duration per agent |

Metrics are exported via OTLP to the Grafana stack every 5 seconds.

### Grafana Dashboard

The provisioned dashboard (`grafana/nutrition-planner.json`) shows:
- Agent overview stats (active, plans generated, p95 duration)
- Agent duration time series per agent
- HTTP endpoint latency (POST /plan, GET /plan/stream, GET /plan/result)
- JVM memory and threads
- Distributed traces from Tempo

---

## UI

### Pages

| Route | Template | Description |
|-------|----------|-------------|
| `GET /login` | `login.html` | Spring Security form login page |
| `GET /` | `index.html` | Main form — day/meal checkboxes, country code, additional instructions + SSE progress panel |
| `POST /plan` | `fragments/plan :: plan` | HTMX fallback — renders the weekly plan result |
| `GET /plan/stream` | SSE | Real-time agent progress events |
| `GET /plan/result?id=` | `fragments/plan :: plan` | Serves cached result as Thymeleaf HTML |

---

## Package Structure

```
com.nutritionplanner/
├── NutritionPlannerApplication.java
├── SecurityConfig.java
├── agent/
│   ├── SeasonalIngredientAgent.java        # @Agent — seasonal produce
│   ├── MealPlanCreatorAgent.java           # @Agent — initial meal plan
│   ├── MealPlanReviserAgent.java           # @Agent — revise based on feedback
│   ├── NutritionGuardAgent.java            # @Agent + @Tool — validate nutrition
│   ├── NutritionTools.java                 # @Tool class for nutrition calculations
│   └── NutritionPlannerWorkflow.java       # Typed workflow interface
├── config/
│   └── AgenticWorkflowConfig.java          # Composes agents into sequence + loop
├── controller/
│   ├── NutritionPlannerController.java     # @RestController — /api/nutrition-plan
│   ├── NutritionPlannerUiController.java   # @Controller — /, /login, /plan, /plan/result
│   └── SseProgressController.java          # @RestController — /plan/stream (SSE)
├── model/
│   ├── UserProfile.java, UserProfileProperties.java
│   ├── WeeklyPlanRequest.java, WeeklyPlan.java
│   ├── Recipe.java, NutritionInfo.java, SeasonalIngredients.java
│   └── NutritionAuditValidationResult.java
├── observability/
│   └── MicrometerAgentListener.java        # Agent → Micrometer metrics bridge
└── orchestration/
    ├── NutritionPlannerOrchestrator.java    # Thin wrapper delegating to workflow
    └── StreamingPlannerService.java         # SSE + ThreadLocal emitter bridge
```

---

## Testing (12 tests)

| Test Class | Tests | Description |
|------------|-------|-------------|
| `WeeklyPlanTest` | 4 | Unit tests for `dailyNutritionTotals()` and `totalMealCount()` |
| `NutritionPlannerApplicationTest` | 1 | Context loads with mocked workflow |
| `NutritionPlannerPlaywrightTest` | 5 | E2E browser tests (login, plan generation, toggle, error) |
| `NutritionPlannerIntegrationTest` | 2 | Full orchestrator flow with mocked workflow |

All tests use mocked AI services — **no live Azure OpenAI calls in CI**.

---

## Deployment

### Docker

```bash
cd langchain4j
docker build -t langchain4j-nutrition-planner .
docker run -p 8080:8080 \
  -e AZURE_OPENAI_ENDPOINT=... \
  -e AZURE_OPENAI_API_KEY=... \
  -e AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o \
  langchain4j-nutrition-planner
```

### Azure Container Apps (azd)

```bash
azd auth login
azd up
```

See [README.md](../README.md) for full azd deployment details.
