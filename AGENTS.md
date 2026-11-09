# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each implements the same use case: create a personalized weekly meal plan (parallel fetch of user profile and seasonal ingredients, human-in-the-loop preferences, recipe generation, validation/revision loop). See the pattern comparison table in [README.md](README.md) and keep it in sync when patterns change.

## Tech Stack

- **Java 25**
- **Spring Boot 4.1** / Spring Framework 7 / Jakarta EE 11
- **Build**: Maven — each module has its own wrapper (`./mvnw`); the root `pom.xml` is only an aggregator
- **AI Frameworks**: LangChain4j 1.23, Spring AI 2.0, Embabel 1.5 — all using OpenAI (or Azure OpenAI)
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
├── langchain4j/             # LangChain4j implementation
├── spring-ai/               # Spring AI implementation
├── embabel/                 # Embabel implementation
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
