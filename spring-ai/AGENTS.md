# AGENTS.md — Spring AI Nutrition Planner

## Overview

Spring AI implementation of the AI Nutrition Planner. Implements the same agentic workflow as
the **embabel** and **langchain4j** modules, using Spring AI's `ChatClient` fluent API with
`.entity()` for structured output and Azure OpenAI as the backing model.

## Architecture

### Agent Services

| Service                      | Role                          | Spring AI Pattern                                          |
|------------------------------|-------------------------------|------------------------------------------------------------|
| `SeasonalIngredientService`  | Fetch seasonal ingredients    | `ChatClient` → `.entity(SeasonalIngredients.class)`        |
| `RecipeCuratorService`       | Create / revise meal plans    | `ChatClient` → `.entity(WeeklyPlan.class)`                 |
| `NutritionGuardService`      | Validate nutrition & allergens| `ChatClient` → `.entity(NutritionAuditValidationResult.class)` |

### Orchestration

`NutritionPlannerOrchestrator` coordinates the three-phase workflow:

1. **Parallel fetch** — Seasonal ingredients + user profile loaded concurrently (virtual threads)
2. **Create plan** — `RecipeCuratorService` generates a `WeeklyPlan` from a detailed prompt
3. **Validate / revise loop** — `NutritionGuardService` audits the plan; if violations exist,
   `RecipeCuratorService` revises it. Loop repeats up to `max-validation-iterations` times.

### Domain Model

Java records shared across all modules:

- `Recipe`, `Recipe.Ingredient`, `NutritionInfo` — core recipe data
- `WeeklyPlan`, `WeeklyPlan.DailyPlan` — weekly structure with nullable meals
- `WeeklyPlanRequest`, `WeeklyPlanRequest.DayPlanRequest` — user request from UI
- `SeasonalIngredients` — LLM-generated seasonal produce list
- `NutritionAuditValidationResult` — validation result with per-recipe violations
- `UserProfile`, `UserProfileProperties` — YAML-configured user dietary preferences

### UI

Thymeleaf + HTMX + Tailwind CSS. Same templates as langchain4j:

- `/login` — form login (Spring Security)
- `/` — main form with day/meal selectors, HTMX submission
- `/plan` — returns `fragments/plan.html` fragment via HTMX

## Key Spring AI Patterns

### ChatClient Usage

```java
// Build once per service
this.chatClient = chatClientBuilder
        .defaultSystem("You are a nutritionist…")
        .build();

// Call with structured output
WeeklyPlan plan = chatClient.prompt()
        .user(prompt)
        .call()
        .entity(WeeklyPlan.class);
```

Spring AI uses Jackson for JSON binding — nullable fields work correctly (unlike LangChain4j's Gson).

### Configuration

```yaml
spring.ai.azure.openai:
  endpoint: ${AZURE_OPENAI_ENDPOINT}
  api-key: ${AZURE_OPENAI_API_KEY}
  chat.options.deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME}
```

## Build & Run

```bash
# Build & test
./mvnw clean install -pl spring-ai

# Run (requires Azure OpenAI credentials in environment or .env)
./mvnw spring-boot:run -pl spring-ai

# Run tests only
./mvnw test -pl spring-ai
```

## Testing Strategy

| Test Class                             | Type          | What it covers                                |
|----------------------------------------|---------------|-----------------------------------------------|
| `NutritionPlannerApplicationTest`      | Context       | Spring context loads with mocked ChatClient   |
| `WeeklyPlanTest`                       | Unit          | Nutrition totals, meal counting, null handling |
| `NutritionPlannerIntegrationTest`      | Integration   | Full orchestration with mocked services       |
| `NutritionPlannerPlaywrightTest`       | E2E (browser) | Login, form interaction, plan generation      |

### Mocking in Tests

Services are mocked with `@MockitoBean` on the concrete service classes (not interfaces).
The `ChatClient.Builder` auto-configuration is overridden in the test profile.

## Playwright E2E Tests

5 browser tests using headless Chromium:

1. **loginPageLoads** — login form renders correctly
2. **loginAndRedirect** — successful login redirects to `/`
3. **generatePlanFlow** — select meals → submit → verify plan renders
4. **toggleAllSelectsAllMeals** — "All" button toggles day checkboxes
5. **emptySelectionHandledGracefully** — submit without selection doesn't crash
