# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across three frameworks to compare their programming models, abstractions, and features.

## Use Case

The agent creates a personalised weekly meal plan for a user. It:

1. Fetches the user profile (dietary restrictions, allergies, calorie target, etc.) and seasonal ingredients in parallel
2. Generates recipes for the requested days and meals using seasonal produce
3. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
4. Revises failing recipes based on feedback, then re-validates — looping until the plan passes or a maximum number of iterations is reached

## Implementations

| Framework | Folder | Key Pattern |
|-----------|--------|-------------|
| [Embabel](https://github.com/embabel/embabel-agent) | [`embabel/`](embabel/) | Declarative agent DSL with goals and actions |
| [LangChain4j](https://docs.langchain4j.dev) | [`langchain4j/`](langchain4j/) | `langchain4j-agentic` module — `@Agent` interfaces composed with `sequenceBuilder` / `loopBuilder` |
| [Spring AI](https://spring.io/projects/spring-ai) | [`spring-ai/`](spring-ai/) | `ChatClient` fluent API with `.entity()` structured output |

Each folder contains its own `AGENTS.md` with framework-specific architecture details.

## Prerequisites

- **Java 25**
- **Maven 3.9+** (no Maven wrapper — use system Maven)
- **Docker Desktop** (for Grafana observability stack and Ollama)
- An LLM provider: **Azure OpenAI**, **OpenAI**, or **Ollama** (local)

## Setup

### Codespaces (recommended)

Open this repo in a GitHub Codespace. The dev container installs Java 25, Maven, Docker, and Ollama automatically. After the container starts, Ollama is ready at `localhost:11434` with the `qwen2.5` model pre-pulled.

### Ollama (local LLM — no API key required)

Install Ollama and pull a model:

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5
```

Or start Ollama via Docker Compose (included in this repo):

```bash
docker compose up ollama -d
docker compose exec ollama ollama pull qwen2.5
```

Then run any module with the `ollama` profile:

```bash
SPRING_PROFILES_ACTIVE=ollama mvn spring-boot:run
```

You can override the model and base URL with environment variables:

```bash
export OLLAMA_MODEL_NAME=qwen2.5      # default
export OLLAMA_BASE_URL=http://localhost:11434  # default
```

### Azure OpenAI

Export your Azure OpenAI credentials before starting any implementation:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

Or create a `.env` file at the repo root (used by the run scripts):

```
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key
AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

### OpenAI (Embabel only)

```bash
export OPENAI_API_KEY=sk-...
```

## Build & Test

```bash
# Build all modules
mvn clean install

# Build a single module
mvn clean install -pl langchain4j

# Run tests for a single module
mvn test -pl langchain4j
```

## Run

Change into the implementation directory you want to run (e.g. `cd langchain4j`) and execute:

```bash
mvn spring-boot:run
```

The application starts on port `8080`. Basic auth is pre-configured with user `alice` / password `123456` (see `application.yaml`). A browser UI is available at [http://localhost:8080](http://localhost:8080) and a REST API at `http://localhost:8080/api/nutrition-plan`.

## Observability (Grafana)

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose:

```bash
docker compose up -d
```

- **Grafana dashboard**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.

<details>
<summary><strong>Deploy to Azure (azd)</strong></summary>

The project supports [Azure Developer CLI](https://learn.microsoft.com/azure/developer/azure-developer-cli/) for one-command deployment to Azure Container Apps:

```bash
azd auth login
azd up
```

This provisions a Container Apps Environment, Azure Container Registry, Azure OpenAI (gpt-4o), and one Container App per framework (`langchain4j`, `spring-ai`, `embabel`). Each app is built from its own `Dockerfile` (multi-stage, Java 25) and scales 0–3 replicas. See [`infra/`](infra/) for the Bicep templates and [`azure.yaml`](azure.yaml) for the service manifest.
</details>

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

The response is a `WeeklyPlan` containing a recipe (name, ingredients, nutrition info, instructions, prep time) for each requested meal slot.

## Further Reading

- [Embabel Agent Framework](https://github.com/embabel/embabel-agent)
- [LangChain4j Documentation](https://docs.langchain4j.dev)
- [LangChain4j Agentic Tutorial](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [Azure Developer CLI](https://learn.microsoft.com/azure/developer/azure-developer-cli/)