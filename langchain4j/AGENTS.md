# AGENTS.md — LangChain4j Nutrition Planner

> **Goal**: Replicate the embabel nutrition planner (`../embabel/`) using LangChain4j.
> Same domain model, same UI, same agent workflow — different AI framework.
> **Start from scratch** — delete all existing Java source files, templates, static resources, and tests before generating new code.

---

## Framework & Dependencies

- **LangChain4j 1.12.2-beta22** with `langchain4j-spring-boot-starter` + `langchain4j-azure-open-ai-spring-boot-starter`
- **Spring Boot 3.5.13** / **Java 25** (match the embabel module's Spring Boot version)
- **Azure OpenAI** as the LLM provider
- **Thymeleaf** + **HTMX 2.0.3** (via `htmx-spring-boot-thymeleaf` 4.0.3) + **Tailwind CSS** (CDN) for UI
- **Spring Security** with form login + HTTP Basic
- **Jackson** with `jackson-datatype-jdk8` for `Optional` serialization
- **Actuator** + **OpenTelemetry** (micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp, micrometer-registry-otlp)
- **Playwright** (Java) for end-to-end UI tests

## Azure OpenAI Configuration

All three environment variables are **required** at runtime. Never hardcode them.

```
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key-here
AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

In `application.yaml`:

```yaml
langchain4j:
  azure-open-ai:
    chat-model:
      endpoint: ${AZURE_OPENAI_ENDPOINT}
      api-key: ${AZURE_OPENAI_API_KEY}
      deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME:gpt-4o}
      temperature: 0.3
      log-requests: true
      log-responses: true
```

## Build & Run

```bash
cd langchain4j-nutrition-planner
../mvnw clean install                       # build
../mvnw test                                # all tests (unit + integration, no live API)
../mvnw spring-boot:run                     # run (requires Azure OpenAI env vars)
```

## Starting Fresh

**Delete everything** under `src/` before generating code. The old multi-agent orchestrator, inventory/fridge-image agents, shopping list, budget, and prep coach agents are **not needed**. Replace with the simpler embabel-style workflow described below.

---

## Domain Model (match embabel exactly)

All records live in `com.nutritionplanner.model`. Copy the **exact same field names and types** as the embabel module:

| Record | Fields |
|--------|--------|
| `UserProfile` | `String name, List<String> dietaryRestrictions, List<String> healthGoals, int dailyCalorieTarget, List<String> allergies, List<String> dislikedIngredients` |
| `UserProfileProperties` | `@ConfigurationProperties(prefix = "nutrition-planner")` — `List<UserProfile> userProfiles` with method `getUserProfile(String name)` |
| `WeeklyPlanRequest` | `List<DayPlanRequest> days, String countryCode, String additionalInstructions` |
| `WeeklyPlanRequest.DayPlanRequest` | `DayOfWeek day, List<MealType> meals` |
| `WeeklyPlanRequest.MealType` | enum: `BREAKFAST, LUNCH, DINNER` |
| `Recipe` | `String name, List<Ingredient> ingredients, NutritionInfo nutrition, String instructions, int prepTimeMinutes` |
| `Recipe.Ingredient` | `String name, String quantity, String unit` |
| `NutritionInfo` | `int calories, double proteinGrams, double carbGrams, double fatGrams, int sodiumMg` — plus aggregate constructor `NutritionInfo(List<Recipe>)` |
| `SeasonalIngredients` | `List<Recipe.Ingredient> items` |
| `WeeklyPlan` | `List<DailyPlan> days` — plus utility methods `dailyNutritionTotals()` and `totalMealCount()` |
| `WeeklyPlan.DailyPlan` | `DayOfWeek day, Optional<Recipe> breakfast, Optional<Recipe> lunch, Optional<Recipe> dinner` |
| `NutritionAuditValidationResult` | `boolean allPassed, List<NutritionAuditRecipeViolation> violations, String consolidatedFeedback` |
| `NutritionAuditRecipeViolation` | `DayOfWeek dayOfWeek, String recipeName, String explanation, String suggestedFix` |

---

## Agent Workflow (match embabel's flow)

```
sequential:
  parallel:
    fetchUserProfile          (config lookup — no LLM)
    fetchSeasonalIngredients  (LLM call)
  createMealPlan              (LLM call — Recipe Curator persona)
  validate                    (LLM call — Nutrition Guard persona)
  optional loop (max 3 iterations):
    reviseMealPlan            (LLM call — Recipe Curator persona)
    validate                  (LLM call — Nutrition Guard persona)
  return WeeklyPlan
```

### Implementation with LangChain4j

Use `@AiService`-annotated interfaces for the LLM calls. The orchestrator is a Spring `@Service` that coordinates the flow.

**AI Services** (interfaces in `com.nutritionplanner.agent`):

| Service | Method signature | System prompt persona |
|---------|------------------|-----------------------|
| `SeasonalIngredientService` | `SeasonalIngredients fetchSeasonalIngredients(String prompt)` | Nutrition expert — seasonal produce |
| `RecipeCuratorService` | `WeeklyPlan createMealPlan(String prompt)` | Recipe Curator (see persona below) |
| `RecipeCuratorService` | `WeeklyPlan reviseMealPlan(String prompt)` | Recipe Curator (see persona below) |
| `NutritionGuardService` | `NutritionAuditValidationResult validate(String prompt)` | Nutrition Guard (see persona below) |

### Personas (copy from embabel)

**Recipe Curator** system prompt:
```
You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
ingredients and always provide accurate nutrition information for each dish.
Instructions: Draft recipes in English based on the user requested meals and days.
Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
```

**Nutrition Guard** system prompt:
```
You are a Nutrition Guard — a strict dietary compliance validator specialized in ensuring
meal plans meet user health requirements and dietary restrictions.
Tone: Thorough, precise, and uncompromising. You apply dietary rules consistently and
flag every violation without exception. Be concise and factual in your assessments.
Instructions: Validate a list of recipes against a user profile and flag any violations.
Check each recipe for:
1. NUTRITION_INFO: Nutrition information is available for each recipe
2. CALORIE_OVERFLOW: calories exceed daily calorie target
3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
```

### Orchestrator

`NutritionPlannerOrchestrator` (`@Service`) coordinates the flow:

1. **Parallel phase**: fetch `UserProfile` from config + call `SeasonalIngredientService` concurrently (use virtual threads / `StructuredTaskScope`)
2. **Create plan**: call `RecipeCuratorService.createMealPlan()` with the weekly request, seasonal ingredients, and additional instructions
3. **Validate loop**: call `NutritionGuardService.validate()` — if `allPassed == false`, call `RecipeCuratorService.reviseMealPlan()` with feedback, then validate again. Max 3 iterations.
4. **Return** final `WeeklyPlan`

---

## UI (replicate embabel's Thymeleaf + HTMX UI exactly)

### Pages

| Route | Template | Description |
|-------|----------|-------------|
| `GET /login` | `login.html` | Spring Security form login page |
| `GET /` | `index.html` | Main form — day/meal checkboxes, country code, additional instructions |
| `POST /plan` | `fragments/plan :: plan` | HTMX partial — renders the weekly plan result |

### UI Controller

`NutritionPlannerUiController` (`@Controller`):
- `GET /login` → renders login page with AI model name
- `GET /` → renders index page with AI model name
- `POST /plan` → accepts day checkboxes (monday..sunday as `List<String>`), countryCode, additionalInstructions, `Principal` for username. Builds `WeeklyPlanRequest`, calls orchestrator, returns plan fragment.

### REST Controller

`NutritionPlannerController` (`@RestController`):
- `POST /api/nutrition-plan` → accepts `@RequestBody WeeklyPlanRequest`, uses `Principal` for username, returns `WeeklyPlan` JSON.

### Template Details

**`login.html`**: Tailwind CSS, centered sign-in card, navbar with app name + AI model, username/password fields, error message on bad credentials.

**`index.html`**: 
- Navbar: "AI Nutrition Planner" + "powered by {model}"
- 7-column grid of day cards (Mon–Sun), each with All/None toggle + BREAKFAST/LUNCH/DINNER checkboxes
- Country code input (auto-detected via geolocation + OpenStreetMap Nominatim reverse geocoding)
- Additional instructions text input
- "Generate Plan" button submitting via `hx-post="/plan" hx-target="#result" hx-swap="innerHTML"`
- Loading spinner with `htmx-indicator`

**`fragments/plan.html`**:
- "Your Weekly Plan" heading
- Each day: slate-800 header bar with day name, then breakfast/lunch/dinner sections (only shown if present via `Optional`)
- Recipe card: name + prep time, ingredients list (bullet points: "200 g asparagus"), instructions paragraph
- Nutrition panel sidebar: Calories (kcal), Protein (g), Carbs (g), Fat (g), Sodium (mg)

---

## Configuration (`application.yaml`)

```yaml
spring:
  application.name: langchain4j-nutrition-planner
  security.user:
    password: 123456
    name: alice

langchain4j:
  azure-open-ai:
    chat-model:
      endpoint: ${AZURE_OPENAI_ENDPOINT}
      api-key: ${AZURE_OPENAI_API_KEY}
      deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME:gpt-4o}
      temperature: 0.3
      log-requests: true
      log-responses: true

logging:
  level:
    com.nutritionplanner: DEBUG

management:
  endpoints.web.exposure.include: "*"
  endpoint.env.show-values: ALWAYS
  tracing:
    sampling.probability: 1.0
    enabled: true
  otlp:
    metrics.export.step: 5s
    tracing.endpoint: http://localhost:4318/v1/traces

nutrition-planner:
  max-validation-iterations: 3
  user-profiles:
    - name: alice
      dietary-restrictions:
        - vegetarian
      health-goals:
        - weight-loss
        - improve-energy
      daily-calorie-target: 1800
      allergies:
        - nuts
      disliked-ingredients:
        - cilantro
        - olives
```

## Security

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(form -> form.loginPage("/login").permitAll())
        .httpBasic(Customizer.withDefaults()).build();
}
```

---

## Testing

### Unit Tests

- **`WeeklyPlanTest`**: Test `dailyNutritionTotals()` and `totalMealCount()` utility methods on `WeeklyPlan` with hand-built data. No LLM needed.

### Integration Tests (mocked LLM)

- **`NutritionPlannerIntegrationTest`**: Use `@SpringBootTest` with a mocked `ChatLanguageModel` bean (`@MockitoBean`). Test the full orchestrator flow:
  1. Mock the seasonal ingredients LLM call → return fixed `SeasonalIngredients`
  2. Mock the create meal plan LLM call → return fixed `WeeklyPlan`
  3. Mock the validate LLM call → first return FAIL with allergen violation, then return PASS
  4. Mock the revise LLM call → return revised `WeeklyPlan`
  5. Verify the orchestrator produces a valid `WeeklyPlan` after one revision loop
- **Never call live Azure OpenAI APIs in tests.**

### Playwright End-to-End UI Tests

Add **Playwright for Java** (`com.microsoft.playwright:playwright`) as a test dependency.

Create `NutritionPlannerPlaywrightTest` that:

1. Starts the Spring Boot app on a random port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) with a **mocked `ChatLanguageModel`** (no live API calls)
2. Launches a headless Chromium browser via Playwright
3. Tests the following scenarios:

| Test | Steps | Assertions |
|------|-------|------------|
| `loginPageLoads` | Navigate to `/login` | Page title contains "Sign in", username and password fields visible |
| `loginAndRedirect` | Fill username `alice`, password `123456`, submit | Redirected to `/`, "AI Nutrition Planner" heading visible |
| `generatePlanFlow` | Login → check Monday BREAKFAST+LUNCH+DINNER → set country "DE" → click "Generate Plan" → wait for HTMX response | "Your Weekly Plan" heading appears, at least one recipe name visible, nutrition panel with "kcal" visible |
| `toggleAllSelectsAllMeals` | Login → click "All" button on Monday card | All 3 checkboxes for Monday are checked |
| `emptySelectionShowsGracefulError` | Login → click "Generate Plan" with no days selected | App handles gracefully (no crash, shows message or empty result) |

**Playwright setup notes**:
- Use `@BeforeAll` to install browsers: `Playwright.create()` then `browser.launch(new BrowserType.LaunchOptions().setHeadless(true))`
- Use `@AfterAll` to close browser and playwright
- Each test creates a new `BrowserContext` and `Page` for isolation
- Mock the `ChatLanguageModel` to return valid structured JSON responses so the UI renders correctly without live API calls
- Use Playwright's `page.waitForSelector()` or `page.locator().waitFor()` to handle HTMX async loading

---

## Package Structure

```
com.nutritionplanner/
├── NutritionPlannerApplication.java          # @SpringBootApplication
├── SecurityConfig.java                       # SecurityFilterChain bean
├── agent/
│   ├── SeasonalIngredientService.java        # @AiService interface
│   ├── RecipeCuratorService.java             # @AiService interface
│   └── NutritionGuardService.java            # @AiService interface
├── controller/
│   ├── NutritionPlannerController.java       # @RestController — /api/nutrition-plan
│   └── NutritionPlannerUiController.java     # @Controller — /, /login, /plan
├── model/
│   ├── UserProfile.java
│   ├── UserProfileProperties.java
│   ├── WeeklyPlanRequest.java
│   ├── Recipe.java
│   ├── NutritionInfo.java
│   ├── SeasonalIngredients.java
│   ├── WeeklyPlan.java
│   └── NutritionAuditValidationResult.java
└── orchestration/
    └── NutritionPlannerOrchestrator.java     # @Service — coordinates agent flow
```

---

## Key Differences from Embabel

| Aspect | Embabel | LangChain4j |
|--------|---------|-------------|
| Agent definition | `@Agent` + `@Action` + `@State` annotations with GOAP planner | `@AiService` interfaces + manual orchestrator `@Service` |
| LLM invocation | `ai.withLlm(...).createObject(prompt, Class)` | LangChain4j structured output via `@AiService` return types |
| Personas | `Persona` objects passed as prompt elements | System messages in `@SystemMessage` annotations on AI service methods |
| Tool calling | `@UnfoldingTools` + `@LlmTool` on records | Not needed — nutrition totals computed in Java code, not via LLM tool calls |
| State machine | `@State` records with `implements Stage` | Explicit loop in orchestrator `@Service` |
| Agent invocation | `AgentInvocation.create(platform, target).invoke(inputs)` | Direct method calls through orchestrator |
