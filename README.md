# AI Nutrition Planner

The same AI nutrition planner built with **Embabel**, **LangChain4j**, and **Spring AI**, comparing their programming models and agentic patterns.

[Slides: Comparing Agentic AI Frameworks for Java](slides.pdf)

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel.
2. Asks for meal preferences (human-in-the-loop).
3. Generates recipes for the requested days and meals.
4. Checks allergens, calories, and dietary restrictions; revises until valid or the iteration limit is reached.

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
| **Tool Search** — dynamic tool discovery at runtime | ✅ `@UnfoldingTools` — progressive disclosure | ✅ `@ToolSearchStrategySupplier` — keyword-based discovery via `SimpleToolSearchStrategy` | ✅ `tool-search-advisor` — metadata search before each call |
| **Human-in-the-Loop** — pause workflow for user input | ✅ `WaitFor.formSubmission()` | ✅ `@HumanInTheLoop` + `SuspendedResponse` | ✅ `AskUserQuestionTool` |
| **Agent Skills** — invoke pre-packaged executable skills | ✅ `Skills` with script execution engine | ✅ `Skills.from()` via `CustomSkillLoader` | ✅ `SkillsTool` |
| **Multi-Agent Orchestration** — specialized sub-agents collaborate | ✅ goal-driven action graph | ✅ `@SequenceAgent` with sub-agents | ✅ custom orchestration in code — no direct framework support |
| **Persona** — role-based system prompts per agent | ✅ first-class `Persona` object | ✅ custom implementation via `@SystemMessage` | ✅ custom implementation via `.system()` on `ChatClient` |
| **MCP Server** — expose agent as a Model Context Protocol tool | ✅ `@Export(remote = true)` (HTTP) | ✅ community starter + `@Tool` (STDIO) | ✅ `@McpTool` (HTTP) |
| **MCP Client demo** — discover and call the remote planner | — | ✅ auto-configured STDIO client | — |

## Setup

Requires **Java 25** and an **OpenAI or Azure OpenAI key**. Each module includes a Maven wrapper; Docker is optional for observability.

Alternatively, open a **GitHub Codespace** for preinstalled tools and add your API key as a Codespaces secret.

LangChain4j uses Maven snapshots configured in its [POM](langchain4j/pom.xml).

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

Set `model-name` in the module's `application-azure.yaml` to your Azure deployment name. LangChain4j sets `reasoning-effort: none` for function-tool compatibility with its configured model.

Alternatively copy [`.env.example`](.env.example) to `.env`, fill in the values and `source` it.

## Run

```bash
cd langchain4j   # or embabel, spring-ai
./mvnw spring-boot:run
```

Open [localhost:8080](http://localhost:8080), log in as `alice` / `123456`, select meals, and answer the agent's follow-up questions. The default provider is `openai`; `SPRING_PROFILES_ACTIVE` overrides it.

## MCP

**Spring AI and Embabel** expose `createNutritionPlan` over Streamable HTTP at `http://localhost:8080/mcp`, with HTTP Basic auth (`alice` / `123456`) and no interactive follow-up.

### LangChain4j: STDIO server and client

The community server starter exposes the planner through `@Tool`. The
[client profile](langchain4j/src/main/resources/application-mcp-client.yaml)
auto-configures `mcpClient.nutrition` and `McpToolProvider`. The CLI starts the
server subprocess, discovers and calls the tool, then shuts down both processes.

With provider credentials set:

```bash
cd langchain4j
./mvnw clean package
export NUTRITION_MCP_SERVER_JAR="$PWD/target/nutrition-planner-langchain4j-0.0.1-SNAPSHOT.jar"

# For Azure: export NUTRITION_MCP_PROVIDER=azure
java -jar "$NUTRITION_MCP_SERVER_JAR" --spring.profiles.active=mcp-client \
  --mcp-request='{"request":{"meals":{"MONDAY":["LUNCH"]},"countryCode":"DE","additionalInstructions":"Quick Mediterranean meals"}}'
```

Omit `--mcp-request` to only list tools. Credentials are inherited by the server
and required even for discovery. Pass preferences in `additionalInstructions`;
the adapter completes the interactive step with empty preferences.

To use another STDIO-capable MCP client, configure it to launch:

```bash
java -jar "$NUTRITION_MCP_SERVER_JAR" --spring.profiles.active=openai,mcp-server
```

Use `azure,mcp-server` for Azure. STDIO runs separately from the web UI, with no
HTTP endpoint or login, as `spring.security.user.name` (default `alice`). Use only
trusted local clients. Logs go to stderr; stdout is reserved for MCP messages.

## Tests

Run inside any module. AI responses are mocked, including the LangChain4j STDIO
round-trip test; no API key is required.

```bash
./mvnw test
```

## Observability

Start the Grafana LGTM stack from the repository root:

```bash
docker compose up -d
```

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

Inside a module, add `observability` to the provider profile (`openai` or `azure`):

```bash
SPRING_PROFILES_ACTIVE=openai,observability ./mvnw spring-boot:run
```

The dashboard includes agent timings, HTTP latency, JVM metrics, and distributed traces.
