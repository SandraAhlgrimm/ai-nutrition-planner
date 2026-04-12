# Spring AI Nutrition Planner — Agentic Patterns

## Overview

This module implements the AI Nutrition Planner using **Spring AI 1.0.0** with the
[`ChatClient` fluent API](https://docs.spring.io/spring-ai/reference/api/chatclient.html).
It demonstrates the **Orchestrator-Workers** agentic pattern: an `OrchestratorAgent` autonomously
decomposes the meal-planning task and delegates to specialized worker agents via `@Tool` methods,
using `PromptChatMemoryAdvisor` to maintain context across interactions.

> Reference implementation: [Christian Tzolov's VoxxedDays 2026 demo](https://github.com/tzolov/voxxeddays2026-demo)

---

## Agentic Patterns Covered

### 1. ChatClient with Structured Output (`.entity()`)

> [Spring AI docs — ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
> · [Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)

Every agent and service returns typed Java records by calling `.entity(Class<T>)`, which
automatically injects a JSON schema into the prompt and deserializes the LLM's response:

```java
WeeklyPlan plan = chatClient.prompt()
        .user(prompt)
        .call()
        .entity(WeeklyPlan.class);
```

Domain records used as structured output: `WeeklyPlan`, `SeasonalIngredients`,
`NutritionAuditValidationResult`. Spring AI uses Jackson for JSON binding, which correctly
handles nullable fields.

**Files:**
- `agent/RecipeCuratorAgent.java` — returns `WeeklyPlan`
- `agent/SeasonalIngredientAgent.java` — returns `SeasonalIngredients`
- `agent/NutritionGuardAgent.java` — returns `NutritionAuditValidationResult`

### 2. Orchestrator-Workers Pattern (Tool-Driven Agentic Workflow)

> [Spring AI docs — Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
> · [VoxxedDays demo — Orchestrator pattern](https://github.com/tzolov/voxxeddays2026-demo)

The `OrchestratorAgent` receives a detailed prompt with workflow instructions and autonomously
decides which tools to invoke and in what order. The workflow logic is **prompt-driven**, not
hardcoded — the LLM follows the instructions to:

1. Fetch seasonal ingredients (via tool)
2. Create an initial meal plan (via tool → `RecipeCuratorAgent`)
3. Validate against user constraints (via tool)
4. Revise if validation fails (via tool → `RecipeCuratorAgent`)
5. Iterate up to 3 times

**Files:**
- `agent/OrchestratorAgent.java` — the orchestrator with tools + memory advisor
- `agent/tools/OrchestratorTools.java` — exposes worker agents as `@Tool` methods
- `orchestration/NutritionPlannerOrchestrator.java` — builds the prompt and delegates

### 3. Tool Definitions (`@Tool`)

> [Spring AI docs — Tools](https://docs.spring.io/spring-ai/reference/api/tools.html)

Two tool classes expose capabilities to the LLM via `@Tool(description = "...")`:

**`agent/tools/NutritionPlannerTools.java`** — low-level tools:

| Tool Method                | Description                                         |
|---------------------------|-----------------------------------------------------|
| `fetchUserProfile()`      | Reads user dietary profile from YAML config         |
| `fetchSeasonalIngredients()` | Asks the LLM for seasonal produce (month + country) |
| `generateMealPlan()`      | Creates a `WeeklyPlan` via ChatClient               |
| `validateMealPlan()`      | Validates plan against user constraints             |
| `reviseMealPlan()`        | Revises plan based on validation feedback           |

**`agent/tools/OrchestratorTools.java`** — delegates to worker agents:

| Tool Method                | Delegates To                        |
|---------------------------|-------------------------------------|
| `fetchUserProfile()`      | `NutritionPlannerTools`             |
| `getSeasonalIngredients()` | `NutritionPlannerTools`            |
| `createMealPlan()`        | `RecipeCuratorAgent`                |
| `validateMealPlan()`      | `NutritionPlannerTools`             |
| `reviseMealPlan()`        | `RecipeCuratorAgent`                |

Tools are registered on the `ChatClient` via `.defaultTools(toolsBean)` — Spring AI automatically
detects `@Tool`-annotated methods and makes them available for the LLM to call.

### 4. Advisors: `PromptChatMemoryAdvisor`

> [Spring AI docs — Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html)
> · [Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)

All four agents use `PromptChatMemoryAdvisor` to maintain conversation context:

```java
this.chatClient = builder
        .defaultSystem("...")
        .defaultTools(tools)
        .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
        .build();
```

The advisor automatically injects previous conversation messages into the prompt, enabling
the orchestrator to reason about prior tool results (e.g., remembering seasonal ingredients
when creating or revising a plan).

**Backing store:** `MessageWindowChatMemory` (in-memory, windowed) — configured as a singleton
bean in `config/AgentConfiguration.java`.

### 5. `ChatClient.Builder` — Per-Agent Configuration

> [Spring AI docs — ChatClient Builder](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_chatclient_builder)

Each agent builds its own `ChatClient` from the auto-configured `ChatClient.Builder`, customizing:
- **System prompt** (`.defaultSystem(...)`) — defines the agent's persona and behavior
- **Tools** (`.defaultTools(...)`) — registers available tool methods
- **Advisors** (`.defaultAdvisors(...)`) — adds memory and other cross-cutting concerns

This "one builder, many clients" pattern avoids manual model bean management.

### 6. Dual Architecture: Services vs Agents

The module contains two layers that coexist:

| Layer | Classes | Pattern | Advisors/Tools |
|-------|---------|---------|---------------|
| **Services** | `SeasonalIngredientService`, `RecipeCuratorService`, `NutritionGuardService` | Simple ChatClient → `.entity()` | None |
| **Agents** | `SeasonalIngredientAgent`, `RecipeCuratorAgent`, `NutritionGuardAgent`, `OrchestratorAgent` | ChatClient + Tools + Memory Advisor | Yes |

The **active path** uses `OrchestratorAgent` (called from `NutritionPlannerOrchestrator`), which
autonomously invokes worker agents via tools. The Services exist as simpler alternatives for
non-agentic use or testing.

### 7. Azure OpenAI with JDK HTTP Client

> [Spring AI docs — Azure OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/azure-openai-chat.html)

Configured via `application.yaml` properties:

```yaml
spring.ai.azure.openai:
  endpoint: ${AZURE_OPENAI_ENDPOINT}
  api-key: ${AZURE_OPENAI_API_KEY}
  chat.options:
    deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME}
    temperature: 0.7
```

**Important:** The default Azure SDK Netty HTTP client is incompatible with Spring Boot's virtual
threads (`channel not registered to an event loop`). This module excludes `azure-core-http-netty`
and uses `azure-core-http-jdk-httpclient` instead.

---

## Workflow Diagram

```text
User → NutritionPlannerUiController → NutritionPlannerOrchestrator
           │
           ▼
    OrchestratorAgent (prompt-driven workflow + memory + tools)
           │
           ├── @Tool fetchUserProfile()        → YAML config
           ├── @Tool getSeasonalIngredients()   → LLM call via NutritionPlannerTools
           ├── @Tool createMealPlan()           → RecipeCuratorAgent → LLM
           ├── @Tool validateMealPlan()         → LLM call via NutritionPlannerTools
           └── @Tool reviseMealPlan()           → RecipeCuratorAgent → LLM
                                                  (loop up to 3×)
           │
           ▼
    WeeklyPlan → Thymeleaf template → browser
```

---

## Patterns Available in Spring AI but Not Used

| Pattern | What it is | Why it's absent here | Reference |
|---|---|---|---|
| **`MessageChatMemoryAdvisor`** | Injects history as chat messages (not prompt text) — preserves message roles | `PromptChatMemoryAdvisor` is simpler for this use case | [Advisors docs](https://docs.spring.io/spring-ai/reference/api/advisors.html) |
| **`VectorStoreChatMemoryAdvisor`** | Retrieves semantically relevant past messages from a vector store | No long-term memory needed across sessions | [Advisors docs](https://docs.spring.io/spring-ai/reference/api/advisors.html) |
| **RAG (`QuestionAnswerAdvisor`)** | Retrieves documents from a vector store to augment prompts | All knowledge comes from the LLM or user config | [RAG docs](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) |
| **Streaming (`.stream()`)** | Returns `Flux<ChatResponse>` for token-by-token streaming | UI uses synchronous HTMX POST, not SSE streaming | [Streaming docs](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_streaming) |
| **`FunctionCallback` / programmatic tools** | Register tools programmatically instead of annotation-based | `@Tool` annotation is sufficient here | [Tools docs](https://docs.spring.io/spring-ai/reference/api/tools.html) |
| **Multi-model / model routing** | Route requests to different models based on task complexity | Single gpt-4o deployment handles all tasks | [Chat Model docs](https://docs.spring.io/spring-ai/reference/api/chat-model.html) |
| **Image / multimodal input** | Send images alongside text prompts | Not relevant to meal planning text workflow | [Multimodal docs](https://docs.spring.io/spring-ai/reference/api/multimodality.html) |
| **Embedding Model + Vector Store** | Generate embeddings and store/query them for similarity search | No semantic search needed in this workflow | [Embeddings docs](https://docs.spring.io/spring-ai/reference/api/embeddings.html) |
| **Output Guardrails / Content Filtering** | Pre/post-process LLM responses for safety or compliance | Domain validation is done by `NutritionGuardAgent`, not a framework guardrail | [Advisors docs](https://docs.spring.io/spring-ai/reference/api/advisors.html) |
| **`@Description` on records** | Annotate record fields to improve structured output schema | Records work well without it via Jackson | [Structured Output docs](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) |
| **MCP (Model Context Protocol)** | Dynamically discover and call tools from external MCP servers | All tools are statically defined | [MCP docs](https://docs.spring.io/spring-ai/reference/api/mcp.html), [VoxxedDays demo](https://github.com/tzolov/voxxeddays2026-demo) |
| **Evaluation / testing framework** | Spring AI's built-in evaluation for response quality testing | Tests mock AI responses rather than evaluating quality | [Evaluation docs](https://docs.spring.io/spring-ai/reference/api/testing.html) |

---

## Build & Run

```bash
mvn clean install -pl spring-ai          # build & test
mvn test -pl spring-ai                   # tests only (no live API needed)
mvn spring-boot:run -pl spring-ai        # run (requires Azure OpenAI env vars)
```

Required environment variables:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

Login: `alice` / `123456`

## Further Reading

- [Spring AI Reference Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Christian Tzolov's VoxxedDays 2026 Demo](https://github.com/tzolov/voxxeddays2026-demo) — advanced agentic patterns with Spring AI
- [Azure OpenAI + Spring AI Guide](https://docs.spring.io/spring-ai/reference/api/chat/azure-openai-chat.html)
