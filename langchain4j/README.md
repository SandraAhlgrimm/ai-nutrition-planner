# LangChain4j Nutrition Planner — Agentic Patterns

## Overview

This module implements the AI Nutrition Planner using **LangChain4j 1.12.2-beta22** with the
[`langchain4j-agentic`](https://docs.langchain4j.dev/tutorials/agentic) module. Four `@Agent`
interfaces are composed into a sequence + loop workflow that generates, validates, and revises
weekly meal plans.

---

## Agentic Patterns Covered

### 1. `@Agent` — Declarative Agent Interfaces

> [LangChain4j docs — AI Services](https://docs.langchain4j.dev/tutorials/ai-services)
> · [Agentic AI Services](https://docs.langchain4j.dev/tutorials/agentic)

Each agent is a plain Java interface annotated with
[`@Agent`](https://docs.langchain4j.dev/tutorials/agentic#agent-annotation),
[`@SystemMessage`](https://docs.langchain4j.dev/tutorials/ai-services#systemmessage),
[`@UserMessage`](https://docs.langchain4j.dev/tutorials/ai-services#usermessage), and
[`@V`](https://docs.langchain4j.dev/tutorials/ai-services#usermessage) for prompt-template
parameter binding. LangChain4j generates the implementation at build time.

| Agent                      | File                                        | Role                                                    |
|----------------------------|---------------------------------------------|---------------------------------------------------------|
| `SeasonalIngredientAgent`  | `agent/SeasonalIngredientAgent.java`        | Fetches seasonal produce for a given month / country    |
| `MealPlanCreatorAgent`     | `agent/MealPlanCreatorAgent.java`           | Creates an initial `WeeklyPlan` from ingredients + profile |
| `NutritionGuardAgent`      | `agent/NutritionGuardAgent.java`            | Validates the plan against dietary rules                |
| `MealPlanReviserAgent`     | `agent/MealPlanReviserAgent.java`           | Revises failing recipes based on guard feedback         |

### 2. Sequence Composition (`sequenceBuilder`)

> [LangChain4j docs — Sequence](https://docs.langchain4j.dev/tutorials/agentic#sequence)

Agents are chained in a fixed order using `AgenticServices.sequenceBuilder()` in
`config/AgenticWorkflowConfig.java`:

```text
seasonal → creator → validation loop
```

Each agent's `outputKey` is written into the shared
[`AgenticScope`](https://docs.langchain4j.dev/tutorials/agentic#agenticscope); downstream
agents read from it by parameter name.

### 3. Loop Composition (`loopBuilder`)

> [LangChain4j docs — Loop](https://docs.langchain4j.dev/tutorials/agentic#loop)

A validation-revision loop is built with `AgenticServices.loopBuilder()`:

- **Sub-agents**: guard → reviser
- **Max iterations**: configurable (default 3)
- **Exit condition**: lambda checks `validationResult.allPassed()` from the scope

This implements the **evaluate-and-retry** pattern — the guard validates, and if violations
exist the reviser fixes them, looping until clean or max iterations are reached.

### 4. Tool Use (`@Tool`)

> [LangChain4j docs — Tools](https://docs.langchain4j.dev/tutorials/tools)

`agent/NutritionTools.java` exposes three `@Tool` methods to the `NutritionGuardAgent`:

| Tool Method              | Description                                      |
|--------------------------|--------------------------------------------------|
| `dailyNutritionTotals()` | Get daily nutrition totals for all days           |
| `nutritionTotalsForDay()`| Get nutrition totals for a specific day           |
| `totalMealCount()`       | Get total number of meals in the plan             |

The LLM decides when to call these tools to get precise nutrition data instead of guessing.

### 5. Structured Output (typed return values)

> [LangChain4j docs — Structured Outputs](https://docs.langchain4j.dev/tutorials/structured-outputs)

All agents return Java records (`SeasonalIngredients`, `WeeklyPlan`,
`NutritionAuditValidationResult`). LangChain4j automatically parses the LLM's JSON response
into the declared return type.

### 6. Agent Listeners / Observability Hooks (`AgentListener`)

> [LangChain4j docs — Agent Listener](https://docs.langchain4j.dev/tutorials/agentic#agentlistener)

Three listeners are registered on the workflow (all with `inheritedBySubagents = true`):

| Listener                    | File                                              | Purpose                                         |
|-----------------------------|---------------------------------------------------|-------------------------------------------------|
| `MicrometerAgentListener`   | `observability/MicrometerAgentListener.java`       | Bridges lifecycle events → Micrometer metrics   |
| SSE progress listener       | `orchestration/NutritionPlannerService.java`       | Pushes real-time progress to the browser via SSE |
| Debug logging listener      | `config/AgenticWorkflowConfig.java`                | Logs agent invocations and completions          |

### 7. Stateful Tool Injection via Listener

The guard agent's `beforeAgentInvocation` callback injects the current `WeeklyPlan` into
`NutritionTools` before each invocation — a pattern for passing runtime state to `@Tool`
objects that the LLM can call.

---

## Workflow Diagram

```text
sequence(NutritionPlannerWorkflow):
  fetchSeasonalIngredients  → SeasonalIngredientAgent
  createMealPlan            → MealPlanCreatorAgent
  loop(maxIterations=3):
    validate                → NutritionGuardAgent (@Tool)
    reviseMealPlan          → MealPlanReviserAgent
    exitCondition: validationResult.allPassed()
  outputKey: "weeklyPlan"
```

---

## Patterns Available in LangChain4j but Not Used

| Pattern | What it is | Why it's absent here |
|---|---|---|
| [**`routerBuilder`**](https://docs.langchain4j.dev/tutorials/agentic#router) (dynamic routing) | LLM or rule-based router picks which sub-agent to invoke next | The workflow is a fixed pipeline with no branching decisions |
| [**`parallelBuilder`**](https://docs.langchain4j.dev/tutorials/agentic#parallel) | Runs multiple agents concurrently and merges outputs | Creator depends on seasonal data, so the two cannot run in parallel |
| [**Handoff / Delegation**](https://docs.langchain4j.dev/tutorials/agentic#handoff) | One agent explicitly delegates to another during a conversation | Orchestrator-driven workflow, not a conversational multi-agent chat |
| [**`ChatMemory`**](https://docs.langchain4j.dev/tutorials/chat-memory) | Persists multi-turn conversation history across invocations | Each plan generation is a single-shot request |
| [**RAG (`ContentRetriever`)**](https://docs.langchain4j.dev/tutorials/rag) | Retrieves documents from a vector store to augment the prompt | All knowledge comes from the LLM or user config; no external knowledge base |
| [**`CodeExecutionTool`**](https://docs.langchain4j.dev/tutorials/tools#code-execution-tool) | LLM writes and executes code at runtime | Not relevant to meal planning |
| [**`@ToolMemoryId`**](https://docs.langchain4j.dev/tutorials/tools#tool-memory) | Scopes tool state per user/conversation in multi-user setups | Tools are short-lived per request; no persistent tool memory needed |
| [**Guardrails**](https://docs.langchain4j.dev/tutorials/ai-services#guardrails) (input/output filters) | Framework-level pre/post-processing for safety, PII removal, etc. | Domain-level guarding is done by `NutritionGuardAgent`, not a framework guardrail |
| [**MCP tool provider**](https://docs.langchain4j.dev/tutorials/MCP) | Dynamically discovers and calls tools from external MCP servers | All tools are statically defined in `NutritionTools` |

---

## Build & Run

```bash
mvn clean install -pl langchain4j          # build & test
mvn test -pl langchain4j                   # tests only (no live API needed)

# Run with OpenAI (default profile)
OPENAI_API_KEY=sk-... mvn spring-boot:run -pl langchain4j

# Run with Azure OpenAI
SPRING_PROFILES_ACTIVE=azure \
  AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/ \
  AZURE_OPENAI_API_KEY=your-api-key \
  AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o \
  mvn spring-boot:run -pl langchain4j
```

### Environment Variables

**OpenAI (default \u2014 `openai` profile):**

```bash
export OPENAI_API_KEY=sk-your-api-key
export OPENAI_MODEL_NAME=gpt-4o          # optional, defaults to gpt-4o
```

**Azure OpenAI (`azure` profile):**

```bash
export SPRING_PROFILES_ACTIVE=azure
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

## Further Reading

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Agentic AI Services Tutorial](https://docs.langchain4j.dev/tutorials/agentic)
- [Spring Boot Integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
