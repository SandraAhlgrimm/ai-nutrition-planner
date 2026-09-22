# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each framework solves the same nutrition-planning domain to evaluate agentic patterns on the JVM.

## Tech Stack

- **Java 25** — prefer standard language features, records, and scoped values; preview features require explicit compiler and runtime configuration.
- **Spring Boot 4.1.1** / Spring Framework 7
- **Build**: Maven — each module independently inherits `spring-boot-starter-parent` 4.1.1. Use system Maven at the root; wrappers are also available inside each module.
- **AI Frameworks**:
  - LangChain4j BOM 1.20.0, agentic and Spring Boot 4 starters 1.20.0-beta30 (`langchain4j-spring-boot4-starter` + `langchain4j-agentic`)
  - Spring AI 2.0.1 (`org.springframework.ai:spring-ai-starter-model-*`); community agent utilities 0.12.0 provide skills and human interaction.
  - Embabel 1.5.2 (`com.embabel.agent:embabel-agent-starter-*`), built on Spring AI 2.0.1

## Build & Test Commands

```bash
mvn clean install                              # full build
mvn test                                       # run all tests
mvn test -pl <module-name>                     # tests for one module
mvn test -Dtest=MyTestClass                    # single test class
mvn test -Dtest=MyTestClass#myMethod           # single test method
mvn spring-boot:run -pl <module-name>          # run a specific module
```

## Architecture

The root POM aggregates three independently runnable Maven modules. Each framework implements the same domain with its own DTOs and has no runtime dependency on another sample. Keep the successful REST request/response contract comparable without introducing a shared framework abstraction.

```
ai-nutrition-planner/
├── langchain4j/             # LangChain4j agentic implementation
├── spring-ai/               # Spring AI implementation
├── embabel/                 # Embabel implementation
├── infra/                   # Bicep IaC for Azure (ACA + OpenAI)
├── grafana/                 # Grafana dashboard + provisioning
├── docker-compose.yaml      # LGTM observability stack
├── azure.yaml               # azd project manifest
└── pom.xml                  # reactor aggregator, not the modules' parent
```

## Key Conventions

### Spring Boot 4 Specifics

- Use **virtual threads** as the default execution model.
- Use **JSpecify null-safety annotations** where required by public APIs and framework contracts.
- Use **declarative HTTP service clients** (`@HttpExchange`) instead of `RestTemplate` or `WebClient` for external API calls.
- Use **Jakarta APIs** (`jakarta.*`), not legacy `javax.*` equivalents.
- Account for **Jackson 3** and Boot 4's modular starters and test dependencies when migrating configuration, serialization, or controller tests.

### Java 25 Specifics

- Prefer **records** for DTOs, domain value objects, and AI model responses.
- Use **sealed interfaces** to model domain hierarchies (meal types, nutrient categories).
- Use **pattern matching with `switch`** where it improves clarity; do not introduce primitive-pattern preview features without configuring and testing preview support.
- Use **scoped values** (`ScopedValue`) over `ThreadLocal` for request-scoped context.
- Use **flexible constructor bodies** — validate inputs before `super()`/`this()` calls.

### AI Framework Patterns

- **LangChain4j**: define agents with `@Agent`-annotated interfaces. Compose with native `AgenticServices.sequenceBuilder()`, `parallelBuilder()`, and `loopBuilder()`. Use the dedicated `*-spring-boot4-starter` artifacts and configure models under `langchain4j.*`.
- **Spring AI**: use the autoconfigured `ChatClient.Builder` fluent API and current native tool-calling/search advisors. Configure under `spring.ai.*`. Application-managed concurrency and retry policy are not a native multi-agent orchestration framework.
- **Embabel**: define typed actions and goals using `@Agent`, `@Action`, and `@AchievesGoal`, with `@State` for the audit/revision lifecycle. Let the GOAP planner compose action chains rather than hardwiring their execution order.
- **Shared retry contract**: generate an initial plan and allow at most three revisions. Audit every candidate, including the final revision. Stop on success; expose exhaustion as an explicit error (HTTP 422 for REST), never as a successful invalid or unaudited plan.

### Testing

- Use **Boot-managed JUnit Jupiter 6**. Spring Framework 7's `SpringExtension` requires Jupiter 6 or newer; do not force the old JUnit 5 version or downgrade `spring-test`.
- Use **`@SpringBootTest`** for integration tests, **`@WebMvcTest`** for controller slices.
- Use **`@MockitoBean`** (from `org.springframework.test.context.bean.override.mockito`) instead of `@MockBean`.
- Mock AI model responses in unit tests — never call live APIs in CI.
- Each module should have tests validating its agentic workflow independently.
- Cover first-pass success, revision success, success on the final permitted revision, and exhaustion with exact generation/audit counts.
- Embabel workflow integration tests use the `offline` profile; the framework disables automatic agent registration under the literal `test` profile.

### Configuration

- Externalize all API keys and model endpoints via environment variables or Spring config profiles.
- Never hardcode API keys — use `${ENV_VAR}` placeholders in `application.yml`.
- Select exactly one provider profile: `openai`, `azure`, or `ollama`. Add `observability` independently when exporting telemetry.
- Preserve the environment variable names documented in `.env.example`; Spring Boot does not automatically import that file.
- Skills and other bundled resources must work from a packaged JAR, not only from an exploded source-tree classpath.
