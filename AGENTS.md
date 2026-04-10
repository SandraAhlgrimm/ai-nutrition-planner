# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each framework solves the same nutrition-planning domain to evaluate agentic patterns on the JVM.

## Tech Stack

- **Java 25** (LTS) — use modern language features: flexible constructor bodies (JEP 513), primitive types in pattern matching (JEP 507), scoped values (JEP 506), compact source files (JEP 512)
- **Spring Boot 4.0.3** / Spring Framework 7 — Jakarta EE 11 (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- **Build**: Maven with `spring-boot-starter-parent` 4.0.3
- **AI Frameworks**:
  - LangChain4j (`dev.langchain4j:langchain4j-spring-boot-starter`)
  - Spring AI 2.0 (`org.springframework.ai:spring-ai-*-spring-boot-starter`)
  - Embabel (`com.embabel.agent:embabel-agent-starter`)

## Build & Test Commands

```bash
./mvnw clean install                           # full build
./mvnw test                                    # run all tests
./mvnw test -pl <module-name>                  # tests for one module
./mvnw test -Dtest=MyTestClass                 # single test class
./mvnw test -Dtest=MyTestClass#myMethod        # single test method
./mvnw spring-boot:run -pl <module-name>       # run a specific module
```

## Architecture

The project is a multi-module Maven structure. Each AI framework gets its own module sharing a common domain model:

```
ai-nutrition-planner/
├── common/              # shared domain objects, DTOs, interfaces
├── langchain4j-impl/    # LangChain4j agentic implementation
├── spring-ai-impl/      # Spring AI agentic implementation
├── embabel-impl/        # Embabel agentic implementation
└── pom.xml              # parent POM
```

## Key Conventions

### Spring Boot 4.0.3 Specifics

- Use **modular autoconfigure** — import only the specific Spring Boot autoconfigure modules needed, not the monolithic jar.
- Apply **JSpecify `@Nullable`/`@NonNull` annotations** for null safety across all public APIs.
- Use **virtual threads** as the default execution model (enabled by default in Boot 4).
- Use **declarative HTTP service clients** (`@HttpExchange`) instead of `RestTemplate` or `WebClient` for external API calls.
- Target **Jakarta EE 11** APIs — use `jakarta.*` packages exclusively, never `javax.*`.

### Java 25 Specifics

- Prefer **records** for DTOs, domain value objects, and AI model responses.
- Use **sealed interfaces** to model domain hierarchies (meal types, nutrient categories).
- Use **pattern matching with `switch`** (including primitives) instead of if-else chains.
- Use **scoped values** (`ScopedValue`) over `ThreadLocal` for request-scoped context.
- Use **flexible constructor bodies** — validate inputs before `super()`/`this()` calls.

### AI Framework Patterns

- **LangChain4j**: define AI services with `@AiService`-annotated interfaces. Configure models via `application.yml` properties under `langchain4j.*`.
- **Spring AI**: use autoconfigured `ChatClient` beans. Configure under `spring.ai.*`. Prefer the `ChatClient.Builder` fluent API.
- **Embabel**: define goals and actions using `@Agent`, `@Goal`, `@Action` annotations. Let the GOAP planner compose action chains — avoid hardwiring workflow sequences.

### Testing

- **JUnit 5** exclusively — JUnit 4 is removed in Spring Boot 4.
- Use **`@SpringBootTest`** for integration tests, **`@WebMvcTest`** (from `org.springframework.boot.webmvc.test.autoconfigure`) for controller slices — requires `spring-boot-starter-webmvc-test` dependency.
- Use **`@MockitoBean`** (from `org.springframework.test.context.bean.override.mockito`) instead of the removed `@MockBean`.
- Use **Testcontainers 2.0** for any external service dependencies.
- Mock AI model responses in unit tests — never call live APIs in CI.
- Each module should have tests validating its agentic workflow independently.

### Configuration

- Externalize all API keys and model endpoints via environment variables or Spring config profiles.
- Never hardcode API keys — use `${ENV_VAR}` placeholders in `application.yml`.
- Use Spring profiles (`dev`, `test`, `prod`) to switch between AI providers or models.
