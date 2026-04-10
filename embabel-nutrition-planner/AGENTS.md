# AGENTS.md — Embabel Nutrition Planner

## Framework

- **Embabel Agent Framework** (`embabel-agent-starter`) — agentic AI on the JVM using Goal-Oriented Action Planning (GOAP)
- Built on top of Spring AI; uses Spring Boot for configuration and DI

## Key Patterns

### Goal-Oriented Action Planning

Embabel uses GOAP: define **goals** (desired outcomes) and **actions** (steps that transform state). The planner dynamically chains actions to reach goals at runtime — do **not** hardwire workflow sequences.

### Agents, Goals, and Actions

```java
@Agent
public class NutritionPlannerAgent {

    @Action
    @Pre("hasUserPreferences")
    @Post("hasMealPlan")
    public MealPlan createMealPlan(UserPreferences prefs) {
        // LLM-backed or deterministic logic
    }

    @Action
    @Pre("hasMealPlan")
    @Post("hasNutritionalAnalysis")
    public NutritionalAnalysis analyzePlan(MealPlan plan) {
        // Analyze nutritional content
    }

    @Goal("hasNutritionalAnalysis")
    public void fullNutritionWorkflow() {}
}
```

### Strongly-Typed Domain Objects

All LLM inputs/outputs must be mapped to strongly-typed domain objects (Java records or Kotlin data classes). Never pass raw strings between actions:

```java
public record UserPreferences(int targetCalories, List<String> allergies, String dietType) {}
public record MealPlan(List<Meal> meals, int totalCalories) {}
public record NutritionalAnalysis(Map<String, Double> macros, List<String> warnings) {}
```

### Multi-Model Orchestration

Embabel supports routing different actions to different LLMs. Configure models in `application.yml`:

```yaml
embabel:
  models:
    default-llm: gpt-4o
    fast-llm: gpt-4o-mini
```

Use the fast model for simple transformations and the powerful model for complex reasoning.

### Pre/Post Conditions

- `@Pre` — conditions that must be true before an action can execute
- `@Post` — conditions that become true after an action completes
- The GOAP planner uses these to build the action chain. Be precise with condition names.

### Testing

- Test individual actions in isolation by providing typed inputs and asserting typed outputs.
- Test goal resolution by verifying the planner selects the correct action chain.
- Mock LLM responses — Embabel's strong typing makes this straightforward.
- Integration tests should use Testcontainers or a local model — never call live APIs in CI.

## Dependencies

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter</artifactId>
</dependency>
```
