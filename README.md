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
| [LangChain4j](https://docs.langchain4j.dev) | [`langchain4j/`](langchain4j/) | `@AiService` interfaces with `AiServices.builder()` |
| [Spring AI](https://spring.io/projects/spring-ai) | [`spring-ai/`](spring-ai/) | `ChatClient` fluent API with `.entity()` structured output |

Each folder contains its own `AGENTS.md` with framework-specific architecture details.

## Setup

### Azure OpenAI

Export your Azure OpenAI credentials before starting any implementation:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

### OpenAI (Embabel only)

```bash
export OPENAI_API_KEY=sk-...
```

## Build & Test

```bash
# Build all modules
./mvnw clean install

# Build a single module
./mvnw clean install -pl spring-ai

# Run tests for a single module
./mvnw test -pl langchain4j
```

## Run

Change into the implementation directory you want to run (e.g. `cd spring-ai`) and execute:

```bash
./mvnw spring-boot:run
```

The application starts on port `8080`. Basic auth is pre-configured with user `alice` / password `123456` (see `application.yaml`). A browser UI is available at [http://localhost:8080](http://localhost:8080) and a REST API at `http://localhost:8080/api/nutrition-plan`.

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
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [Azure OpenAI Chat — Spring AI](https://docs.spring.io/spring-ai/reference/api/chat/azure-openai-chat.html)