# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across three frameworks to compare their programming models, abstractions, and features.

[Slides: Comparing Agentic AI Frameworks for Java](slides.pdf)

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel
2. Asks the user for meal preferences to tailor the plan (human-in-the-loop)
3. Generates recipes for the requested days and meals using seasonal produce
4. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
5. Revises recipes based on feedback and re-validates — looping until the plan passes or a maximum number of iterations is reached

## Implementations

| Framework | Folder | Key Pattern |
|-----------|--------|-------------|
| [Embabel](https://github.com/embabel/embabel-agent) | [`embabel/`](embabel/) | GOAP planner over `@Agent` / `@Action` / `@State` with a goal-driven state machine |
| [LangChain4j](https://docs.langchain4j.dev) | [`langchain4j/`](langchain4j/) | `langchain4j-agentic` declarative API — `@SequenceAgent` / `@ParallelAgent` / `@LoopAgent` composing `@Agent` sub-agents |
| [Spring AI](https://spring.io/projects/spring-ai) | [`spring-ai/`](spring-ai/) | Fluent `ChatClient` API with custom advisors and code-orchestrated workflow |

## Agentic AI Patterns Implemented

| Pattern | Embabel | LangChain4j | Spring AI |
|---------|:-------:|:-----------:|:---------:|
| **Parallel Execution** — independent steps run concurrently | ✅ framework-managed (GOAP planner) | ✅ `@ParallelAgent` | ✅ plain `CompletableFuture` — no direct framework support |
| **Validation / Reflection Loop** — iterate until output passes quality checks | ✅ `@State` machine (NutritionAudit → ReviseWeeklyPlan) | ✅ `@LoopAgent` + `@ExitCondition` | ✅ `ValidationRetryAdvisor` |
| **Tool Use** — agent calls external functions | ✅ `withToolObject()` | ✅ `@ToolsSupplier` | ✅ `.tools()` on `ChatClient` |
| **Tool Search** — dynamic tool discovery at runtime | ✅ `@UnfoldingTools` — a facade tool progressively discloses its inner tools on invocation | ✅ `@ToolSearchStrategySupplier` (`SimpleToolSearchStrategy`) — only the tool-search tool is sent to the LLM, which discovers matching tools by keywords | ✅ `tool-search-advisor` — a separate search step discovers tools via metadata before each call |
| **Human-in-the-Loop** — pause workflow for user input | ✅ `WaitFor.formSubmission()` | ✅ `@HumanInTheLoop` + `SuspendedResponse` | ✅ `AskUserQuestionTool` |
| **Agent Skills** — invoke pre-packaged executable skills | ✅ `Skills` with script execution engine | ✅ `Skills.from()` via `CustomSkillLoader` | ✅ `SkillsTool` |
| **Multi-Agent Orchestration** — specialized sub-agents collaborate | ✅ goal-driven action graph | ✅ `@SequenceAgent` with sub-agents | ✅ custom orchestration in code — no direct framework support |
| **Persona** — role-based system prompts per agent | ✅ first-class `Persona` object | ✅ custom implementation via `@SystemMessage` | ✅ custom implementation via `.system()` on `ChatClient` |
| **MCP Server** — expose agent as a Model Context Protocol tool | ✅ `@Export(remote = true)` | ❌ | ✅ `@McpTool` |

## Prerequisites

- **Java 25**
- **Maven** (wrapper included in each module)
- An **OpenAI API key**
- **Docker Desktop** (optional, for the Grafana observability stack)

## Setup

### Codespaces (recommended)

Open this repo in a GitHub Codespace. The dev container installs Java 25, Maven, and Docker automatically.
Add your OpenAI API key as a Codespaces secret named `OPENAI_API_KEY` (or export it in the terminal).

### LLM Providers

**OpenAI** (default `openai` profile):

```bash
export OPENAI_API_KEY=sk-...
```

**Azure OpenAI** (`azure` profile):

```bash
export SPRING_PROFILES_ACTIVE=azure
export AZURE_OPENAI_BASEURL=https://your-resource.openai.azure.com
export AZURE_OPENAI_API_KEY=your-api-key
```

The model name configured in each module's `application-azure.yaml` must match the name of a deployment in your Azure OpenAI resource.

Alternatively copy [`.env.example`](.env.example) to `.env`, fill in the values and `source` it.

## Run

Change into the implementation directory and start the app:

```bash
cd langchain4j   # or embabel, spring-ai
./mvnw spring-boot:run
```

The app starts on port `8080` with the `openai` profile active unless `SPRING_PROFILES_ACTIVE` says otherwise. Log in as `alice` / `123456`, pick the days and meals for your plan, and answer the follow-up questions the agent asks (human-in-the-loop) at [http://localhost:8080](http://localhost:8080).

### MCP Server

The Spring AI and Embabel implementations additionally expose the agent as an MCP tool (`createNutritionPlan`) via Streamable HTTP at `http://localhost:8080/mcp`, protected by HTTP Basic auth (`alice` / `123456`). When called through MCP, the agent skips the human-in-the-loop step.

## Tests

Each module can be tested on its own; AI model responses are mocked, no API key is required:

```bash
cd spring-ai     # or embabel
./mvnw test
```

## Observability

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose:

```bash
docker compose up -d
```

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

To export traces and metrics from any module, activate the `observability` profile in addition to the LLM provider profile:

```bash
SPRING_PROFILES_ACTIVE=openai,observability ./mvnw spring-boot:run
```

The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.
