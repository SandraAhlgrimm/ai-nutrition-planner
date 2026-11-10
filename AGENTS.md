# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each implements the same use case: create a personalized weekly meal plan (parallel fetch of user profile and seasonal ingredients, human-in-the-loop preferences, recipe generation, validation/revision loop). See the pattern comparison table in [README.md](README.md) and keep it in sync when patterns change.

## Tech Stack

- **Java 25**
- **Spring Boot 4.1** / Spring Framework 7 / Jakarta EE 11
- **Build**: Maven — each module has its own wrapper (`./mvnw`); the root `pom.xml` is only an aggregator
- **AI Frameworks**: LangChain4j 1.23, Spring AI 2.0, Embabel 1.5 — all using the official OpenAI Java SDK with OpenAI or Microsoft Foundry
- **UI**: Thymeleaf + htmx with SSE; Spring Security (demo user `alice` / `123456`)
- **Observability**: OpenTelemetry via OTLP to the Grafana LGTM stack

Module `pom.xml` files are the source of truth for versions.

## Build & Test Commands

Run inside a module directory (`langchain4j/`, `spring-ai/`, `embabel/`):

```bash
./mvnw clean verify                            # build + test
./mvnw test -Dtest=MyTestClass#myMethod        # single test
./mvnw spring-boot:run                         # run on port 8080
```

```bash
mvn clean install                              # all modules, from repo root
docker compose --profile observability up -d   # Grafana LGTM stack
azd up                                         # deploy to Azure Container Apps
```

## Architecture

```
ai-nutrition-planner/
├── langchain4j/             # LangChain4j implementation + Dockerfile
├── spring-ai/               # Spring AI implementation + Dockerfile
├── embabel/                 # Embabel implementation + Dockerfile
├── infra/                   # Bicep IaC for Azure
├── grafana/                 # Grafana dashboard + provisioning
├── docker-compose.yaml      # LGTM observability stack
├── azure.yaml               # azd project manifest
└── pom.xml                  # aggregator POM
```

Each module is a self-contained Spring Boot app with its **own copy** of the domain model (no shared module) and a similar layout in `com.example.nutritionplanner`: domain records, `NutritionPlannerAgent` (workflow entry point), UI/SSE controllers, plus `skills/` and `templates/` resources. Keep the three implementations **functionally equivalent** — apply use case, domain, prompt, and UI changes to every module.

## Key Conventions

### Spring Boot 4 / Java 25

- Prefer **records** for DTOs, domain value objects, and AI model responses.
- Use **sealed interfaces**, **pattern matching with `switch`**, **scoped values** over `ThreadLocal`, and **flexible constructor bodies** where they fit.
- Use **JSpecify** `@Nullable` for null safety (Spring's `@Nullable` is deprecated).
- Use `jakarta.*` packages exclusively, never `javax.*`.

### AI Framework Patterns

- **LangChain4j**: declarative agentic API — `@Agent` interfaces composed with `@SequenceAgent` / `@ParallelAgent` / `@LoopAgent` and `@HumanInTheLoop`. Configure under `langchain4j.*`.
- **Spring AI**: `ChatClient.Builder` fluent API with advisors; orchestration in plain code. Configure under `spring.ai.*`.
- **Embabel**: `@Agent` / `@Action` / `@AchievesGoal` / `@State`; let the GOAP planner compose actions — avoid hardwiring sequences. Configure under `embabel.*`.

### Testing

- **JUnit 5**; `@SpringBootTest` for integration tests, `@WebMvcTest` for controller slices.
- Use **`@MockitoBean`** instead of `@MockBean`.
- Mock AI model responses — never call live APIs in tests.

### Configuration

- Profiles: `openai` (default), `azure`, and `observability` (combine with a provider, e.g. `openai,observability`).
- Never hardcode API keys — use `${ENV_VAR}` placeholders (see `.env.example`).
- Azure profiles use the official OpenAI Java SDK with the Foundry OpenAI-compatible `/openai/v1` endpoint and API-key authentication; do not add Azure-specific model or identity SDKs.
- The LangChain4j and Spring AI Azure profiles set `reasoning-effort: none` because their function tools use Chat Completions. Embabel's bundled model configuration routes its model through the Responses API.
- GPT-6 model deployments do not support custom temperature values. Spring AI applies a per-call, non-strict JSON Schema response format when creating a `WeeklyPlan`; strict output is incompatible with nullable meals.
- Jar-safe skill loading extracts classpath resources to a filesystem directory at runtime in the Spring AI and Embabel implementations.
