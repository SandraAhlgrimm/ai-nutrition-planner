# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each framework solves the same nutrition-planning domain to evaluate agentic patterns on the JVM.

## Tech Stack

- **Java 25** — use modern language features: flexible constructor bodies (JEP 513), primitive types in pattern matching (JEP 507), scoped values (JEP 506), compact source files (JEP 512)
- **Spring Boot 3.5.13** / Spring Framework 6.2
- **Build**: Maven (system install, no wrapper) — modules use `spring-boot-starter-parent` 3.5.13
- **AI Frameworks**:
  - LangChain4j 1.12.2-beta22 (`langchain4j-spring-boot-starter` + `langchain4j-agentic`)
  - Spring AI 2.0 (`org.springframework.ai:spring-ai-*-spring-boot-starter`)
  - Embabel (`com.embabel.agent:embabel-agent-starter`)

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

The project is a multi-module Maven structure. Each AI framework gets its own module sharing a common domain model:

```
ai-nutrition-planner/
├── langchain4j/             # LangChain4j agentic implementation
├── spring-ai/               # Spring AI implementation
├── embabel/                 # Embabel implementation
├── infra/                   # Bicep IaC for Azure (ACA + OpenAI)
├── grafana/                 # Grafana dashboard + provisioning
├── docker-compose.yaml      # LGTM observability stack
├── azure.yaml               # azd project manifest
└── pom.xml                  # parent POM
```

## Key Conventions

### Spring Boot 3.5 Specifics

- Use **virtual threads** as the default execution model.
- Apply **`@Nullable`/`@NonNull` annotations** for null safety across public APIs.
- Use **declarative HTTP service clients** (`@HttpExchange`) instead of `RestTemplate` or `WebClient` for external API calls.
- Target **Jakarta EE 10** APIs — use `jakarta.*` packages exclusively, never `javax.*`.

### Java 25 Specifics

- Prefer **records** for DTOs, domain value objects, and AI model responses.
- Use **sealed interfaces** to model domain hierarchies (meal types, nutrient categories).
- Use **pattern matching with `switch`** (including primitives) instead of if-else chains.
- Use **scoped values** (`ScopedValue`) over `ThreadLocal` for request-scoped context.
- Use **flexible constructor bodies** — validate inputs before `super()`/`this()` calls.

### AI Framework Patterns

- **LangChain4j**: define agents with `@Agent`-annotated interfaces. Compose with `AgenticServices.sequenceBuilder()` / `loopBuilder()`. Configure models via `application.yml` properties under `langchain4j.*`.
- **Spring AI**: use autoconfigured `ChatClient` beans. Configure under `spring.ai.*`. Prefer the `ChatClient.Builder` fluent API.
- **Embabel**: define goals and actions using `@Agent`, `@Goal`, `@Action` annotations. Let the GOAP planner compose action chains — avoid hardwiring workflow sequences.

### Testing

- **JUnit 5** exclusively.
- Use **`@SpringBootTest`** for integration tests, **`@WebMvcTest`** for controller slices.
- Use **`@MockitoBean`** (from `org.springframework.test.context.bean.override.mockito`) instead of `@MockBean`.
- Mock AI model responses in unit tests — never call live APIs in CI.
- Each module should have tests validating its agentic workflow independently.

### Configuration

- Externalize all API keys and model endpoints via environment variables or Spring config profiles.
- Never hardcode API keys — use `${ENV_VAR}` placeholders in `application.yml`.
- Use Spring profiles (`dev`, `test`, `prod`) to switch between AI providers or models.
