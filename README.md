# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across multiple frameworks to compare their programming models, abstractions, and features.

## Use Case

The agent creates a personalised weekly meal plan for a user. It:

1. Fetches the user profile (dietary restrictions, allergies, calorie target, etc.) and seasonal ingredients in parallel
2. Generates recipes for the requested days and meals using seasonal produce
3. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
4. Revises failing recipes based on feedback, then re-validates — looping until the plan passes or a maximum number of iterations is reached

## Implementations

| Framework | Folder |
|-----------|--------|
| [Embabel](https://github.com/embabel/embabel-agent) | `embabel/` |
| [Spring AI](https://spring.io/projects/spring-ai) | `spring-ai/` |
| [LangChain4j](https://docs.langchain4j.dev) | `langchain4j/` |

## Setup

Export your API key before starting the application.

### OpenAI

```bash
export OPENAI_API_KEY=sk-...
```

## Run

Change into the implementation directory you want to run (e.g. `cd embabel`) and execute:

```bash
./mvnw spring-boot:run
```

The application starts on port `8080`. Basic auth is pre-configured with user `alice` / password `123456` (see `application.yaml`). A browser UI is available at `http://localhost:8080` and a REST API at `http://localhost:8080/api/nutrition-plan`.

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