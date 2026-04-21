# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across three frameworks to compare their programming models, abstractions, and features.

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel
2. Generates recipes for the requested days and meals using seasonal produce
3. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
4. Revises recipes based on feedback and re-validates — looping until the plan passes or a maximum number of iterations is reached

## Implementations

| Framework | Folder | Key Pattern |
|-----------|--------|-------------|
| [Embabel](https://github.com/embabel/embabel-agent) | [`embabel/`](embabel/) | Declarative agent DSL with goals and actions |
| [LangChain4j](https://docs.langchain4j.dev) | [`langchain4j/`](langchain4j/) | `langchain4j-agentic` module — `@Agent` interfaces composed with `sequenceBuilder` / `loopBuilder` |
| [Spring AI](https://spring.io/projects/spring-ai) | [`spring-ai/`](spring-ai/) | `ChatClient` fluent API with `.entity()` structured output |

## Prerequisites

- **Java 25**
- **Maven** (Maven wrapper included)
- **Docker Desktop** (for Grafana observability stack and Ollama)
- An LLM provider: **Azure OpenAI**, **OpenAI**, or **Ollama** (local)

## Setup

### Codespaces (recommended)

Open this repo in a GitHub Codespace. The dev container installs Java 25, Maven, Docker, and Ollama automatically. After the container starts, Ollama is ready at `localhost:11434` with the `qwen2.5` model pre-pulled.

### LLM Providers

**Ollama** (local, no API key required):

```bash
docker compose --profile ollama up -d
```

This starts Ollama and automatically pulls `qwen2.5`. 

**Azure OpenAI**:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

**OpenAI**:

```bash
export OPENAI_API_KEY=sk-...
```

## Run

Change into the implementation directory, set the Spring profile related to an LLM provider and start the app:

```bash
cd langchain4j   # or embabel, spring-ai
export SPRING_PROFILES_ACTIVE=ollama # or openai, azure
./mvnw spring-boot:run
```

The app starts on port `8080`. Basic auth: `alice` / `123456`. UI at [http://localhost:8080](http://localhost:8080), REST API at `http://localhost:8080/api/nutrition-plan`.


### Deploy to Azure
The project supports [Azure Developer CLI](https://learn.microsoft.com/azure/developer/azure-developer-cli/) for one-command deployment to Azure Container Apps:

```bash
azd auth login
azd up
```

This provisions a Container Apps Environment, Azure Container Registry, Azure OpenAI (gpt-4o), and one Container App per framework. See [`infra/`](infra/) for Bicep templates and [`azure.yaml`](azure.yaml) for the service manifest.

## Observability

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose:
```bash
docker compose --profile observability up -d
```

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

To enable tracing and metrics export from any module, activate the `observability` profile 
in addition to the profile for the LLM provider of choice:

```bash
SPRING_PROFILES_ACTIVE=ollama,observability mvn spring-boot:run
```
The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.

## Example Request

```bash
curl -s -X POST http://localhost:8080/api/nutrition-plan \
  -u alice:123456 \
  -H "Content-Type: application/json" \
  -d '{
    "days": [
      { "day": "MONDAY",    "meals": ["BREAKFAST", "LUNCH", "DINNER"] },
      { "day": "TUESDAY",   "meals": ["BREAKFAST", "LUNCH", "DINNER"] },
      { "day": "WEDNESDAY", "meals": ["LUNCH", "DINNER"] }
    ],
    "countryCode": "DE",
    "additionalInstructions": "Prefer quick recipes with less than 30 minutes prep time."
  }' | jq .
```
