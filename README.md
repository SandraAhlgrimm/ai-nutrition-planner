# AI Nutrition Planner

Three independently runnable Java/Spring samples implement the same meal-planning
use case with **LangChain4j**, **Spring AI**, and **Embabel**. The comparison is
about their programming models and the features actually exercised here, not a
ranking of model quality or a performance benchmark.

[Presentation slides](slides.pdf) describe the earlier iteration; the code and
comparison below reflect the September 2026 refresh.

## Versions and implementation entry points

Dependency versions were checked against published artifacts on **2026-09-22**.
All three samples use **Java 25**, **Spring Boot 4.1.1**, **Spring Framework 7**,
and **Jackson 3**. Tests use Boot-managed **JUnit Jupiter 6.0.3**: Spring 7's
`SpringExtension` requires Jupiter 6, so forcing the old JUnit 5 version is not
compatible with this platform.

| Sample | Framework dependencies | Start reading |
| --- | --- | --- |
| [LangChain4j](langchain4j/) | BOM/core **1.20.0**; agentic and dedicated Boot 4 starters **1.20.0-beta30** | [Native workflow composition](langchain4j/src/main/java/com/example/nutritionplanner/AgentConfiguration.java), [typed agents](langchain4j/src/main/java/com/example/nutritionplanner/Agents.java) |
| [Spring AI](spring-ai/README.md) | **2.0.1**; community agent utilities **0.12.0** | [ChatClient orchestration](spring-ai/src/main/java/com/example/nutritionplanner/NutritionPlannerAgent.java), [validation advisor](spring-ai/src/main/java/com/example/nutritionplanner/ValidationRetryAdvisor.java) |
| [Embabel](embabel/README.md) | Agent and released skills **1.5.2**, on Spring AI **2.0.1** | [Typed actions, states and goal](embabel/src/main/java/com/example/nutritionplanner/NutritionPlannerAgent.java), [revision budget](embabel/src/main/java/com/example/nutritionplanner/RevisionBudget.java) |

LangChain4j's agentic module remains upstream experimental. Embabel no longer
needs the old experimental snapshot BOM. These versions provide the current APIs
used by the samples without taking an unrelated Spring Boot milestone.

The root POM is a **reactor aggregator**, not the modules' inherited parent.
Each module owns its dependencies, domain DTOs, application, and tests; none
depends on another sample at runtime.

## Shared use case and failure contract

1. Obtain the authenticated user's profile and seasonal ingredients in parallel.
2. Generate recipes for exactly the requested days and meals, retaining dietary
   restrictions, allergies, preferences, and additional instructions.
3. Audit the candidate using nutrition tools and check that its days/meals match
   the request.
4. If needed, revise with the feedback and audit again. Return immediately on
   success; allow **at most three revisions after the initial candidate**.

That means **at most four candidates and four audits**, including an audit of the
last permitted revision. Exhaustion is an explicit failure, never a successful
invalid plan: REST returns **HTTP 422 ProblemDetail**, and the UI displays an
error. Missing/extra meals or duplicated/missing days cannot be accepted just
because the model claims its audit passed. Model/tool/parse failures are not
replaced with empty successful responses.

This limit counts logical candidates and audits, not individual provider HTTP
attempts or tool-calling turns. Embabel's rejection is explicitly `NonRetryable`
so its action-retry machinery cannot turn exhaustion into a fifth audit.

Nutrition totals sum **model-generated estimates**. Structural checks are
deterministic; judgments about ingredients, allergens and dietary suitability
still rely on the model. This is a framework demonstration, not a clinically
validated nutrition service.

## What each sample demonstrates

**Native** means supplied by the named framework. **Application** means code in
this repository. **Community** denotes a separate Spring AI community library.
**Not demonstrated** does not mean the framework cannot support the feature.

| Pattern | LangChain4j | Spring AI | Embabel |
| --- | --- | --- | --- |
| **Parallel preparation** | Native `parallelBuilder()` with virtual-thread execution | Application `Workflow.parallel()` with typed results and virtual threads | Native concurrent process schedules independent typed actions |
| **Orchestration and state** | Native typed `@Agent` interfaces, sequence/conditional/loop builders and per-invocation `AgenticScope` | Application orchestration around native `ChatClient`; not a native multi-agent planner | Native GOAP action graph, typed blackboard, `@State` and `@AchievesGoal` |
| **Audit/revision loop** | Native loop, end-of-iteration audit check, application shape audit and final acceptance gate | Application `ValidationRetryAdvisor` outside native tool-calling loop | Native state transitions with application `RevisionBudget` and non-retryable rejection |
| **Nutrition tools** | Native `@Tool`; current plan supplied through hidden, invocation-local `AgenticScope` | Native `@Tool` and `ToolCallingAdvisor` | Native `@LlmTool` via `withToolObject()` |
| **Tool discovery** | Fixed nutrition tools; search not demonstrated | Native `ToolSearchToolCallingAdvisor` searches tool metadata using `LuceneToolIndex` | Native category-based `@UnfoldingTools` facade progressively reveals tools |
| **Human-in-the-loop** | Not demonstrated | Community `AskUserQuestionTool` in browser only; application owns SSE, answers, cancellation and timeouts | Not demonstrated in the nutrition graph |
| **Agent skills** | Not demonstrated; application supplies the current month | Community `SkillsTool` plus a narrow native `currentMonth` tool; no shell tools | Native `Skills` references and process execution of the bundled Bash script |
| **Persona** | Application role prompts via `@SystemMessage` | Application role prompts via `.system()` | Native `Persona` prompt contributors |
| **MCP server** | Not demonstrated | Native `@McpTool`, Streamable HTTP `/mcp` | Native remote goal export, Streamable HTTP `/mcp` |

Tool discovery is intentionally not collapsed into a single checkbox: Spring AI
searches an index of tool metadata; Embabel exposes a known facade whose category
selection reveals tools. Progressive disclosure is not semantic search.
Likewise, exposing a REST endpoint is not an MCP implementation.

### What the differences mean

**LangChain4j** makes the execution graph explicit. Specialized interfaces remain
small while framework builders express fan-out, sequencing and refinement.
The application still supplies the acceptance policy, shape checks and resource
configuration. This sample demonstrates a composed workflow, not an autonomous
LLM supervisor, and its experimental agentic API can change.

**Spring AI** is integration-first: model access, structured conversion, tool
execution/search and MCP are native building blocks. The application owns the
overall sequence, retry policy and interaction lifecycle. Community skills and
human-question tools add capabilities without turning `Workflow.parallel()` into
a Spring AI workflow engine.

**Embabel** expresses the solution in domain types, actions and a goal, letting
the planner schedule ready actions. Explicit audit/revision states keep the
domain policy visible. Its application-level model bridge selects one Spring AI
`ChatModel` and registers it with Embabel's `SpringAiLlmService`; prompting and
planning remain native Embabel APIs.

## Build and run

Prerequisites: **JDK 25**, **Maven**, and a provider with tool-calling and
structured-output support. Docker is optional for local Ollama and observability.
Embabel's bundled skill additionally requires **Bash and writable temporary
storage**.

From the repository root:

```bash
mvn clean verify
mvn test -pl langchain4j
mvn test -pl spring-ai
mvn test -pl embabel
```

The suites use fake model responses and local transport fixtures; they require
no real model credentials, live model service or Docker. They exercise native
workflow/tool execution, retry boundaries, authentication, provider isolation,
MCP where implemented, and packaged skill loading. Embabel's integration profile
is `offline`, because its literal `test` profile disables agent auto-registration.

Select **exactly one** provider profile: `openai`, `azure`, or `ollama`. With no
explicit profile, `openai` is the default. Add `observability` alongside the
selected provider, not instead of it.

| Profile | Environment variables |
| --- | --- |
| `openai` | `OPENAI_API_KEY`; optional `OPENAI_MODEL_NAME` (default `gpt-4o`) |
| `azure` | `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`; `AZURE_OPENAI_DEPLOYMENT_NAME` (default `gpt-4o`) |
| `ollama` | `OLLAMA_BASE_URL` (default `http://localhost:11434`), `OLLAMA_MODEL_NAME` (default `qwen2.5`) |

Each profile configures only its selected chat model. Inactive providers do not
need credentials. Spring AI and Embabel use the current OpenAI SDK integration
for Azure; keep supplying the Azure resource-root URL, for example
`https://your-resource.openai.azure.com/`.

For local Ollama:

```bash
docker compose --profile ollama up -d
```

Compose pulls `qwen2.5`; wait for that pull to finish before requesting a plan.
The [dev container](.devcontainer/devcontainer.json) also configures Java, Maven,
Docker and Ollama; use `ollama list` to confirm the model is available.

To use an environment file:

```bash
cp .env.example .env
# Edit .env for the chosen provider, then export it in the current shell:
set -a
. ./.env
set +a
```

Spring Boot does **not** automatically read `.env`. Do not commit credentials.
For a local run, choose one module:

```bash
SPRING_PROFILES_ACTIVE=ollama mvn -pl langchain4j spring-boot:run
# Substitute spring-ai or embabel for langchain4j.
```

Module-local `./mvnw` wrappers are also available; there is no root wrapper.
The default port is **8080**, the UI is [http://localhost:8080](http://localhost:8080),
and demo form-login/HTTP Basic credentials are **`alice` / `123456`**.
To run samples side by side, use separate terminals and distinct `SERVER_PORT`
values, for example `8081`, `8082`, and `8083`. Each sample has a distinct session
cookie name, so signing into one localhost port does not log out another sample
in the same browser.

## REST, browser interaction and MCP

All three expose authenticated **`POST /api/nutrition-plan`** with the same
successful request/response shapes:

```bash
curl --silent --show-error --fail-with-body \
  http://localhost:8080/api/nutrition-plan \
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
  }'
```

The result contains `days` with nullable `breakfast`, `lunch`, and `dinner`
recipes. Unrequested meals stay null. HTTP 422 denotes exhausted audits;
other execution failures are errors rather than plans.

Only the **Spring AI browser flow** asks follow-up cooking-preference questions.
It owns the authenticated SSE interaction and releases pending questions on
completion, disconnect or timeout. Its REST and MCP paths are non-interactive;
they do not register a question tool with a fake empty-answer handler.

Spring AI and Embabel publish `createNutritionPlan` at **`/mcp`** using
**Streamable HTTP**, with HTTP Basic identity supplied on transport requests.
This is separate from the browser's SSE event channel. Use `tools/list` to inspect
each schema: Spring AI retains its map-form `request.meals` MCP input while REST
adapts the common `days` list; Embabel uses its native goal input/export and returns
a textual plan. MCP exhaustion is an error tool result, not REST ProblemDetail.
Neither implementation takes a model-controlled username as authentication.
See their [Spring AI](spring-ai/README.md) and [Embabel](embabel/README.md)
documentation for transport and lifecycle details.

## Observability

Start the Grafana/OTLP development stack:

```bash
docker compose --profile observability up -d
SPRING_PROFILES_ACTIVE=ollama,observability mvn -pl langchain4j spring-boot:run
```

Grafana is at [http://localhost:3000](http://localhost:3000), with demo credentials
`admin` / `admin`. The collector accepts OTLP HTTP on `4318` and gRPC on `4317`.
Applications export over HTTP; override destinations with
`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` and `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`.
Exporters are disabled outside the `observability` profile.
That profile also enables HTTP latency histograms and, in LangChain4j, the custom
agent-duration histograms used by the dashboard's percentile panels.

| Sample | Instrumentation demonstrated |
| --- | --- |
| LangChain4j | Application `agent_invocations_total`, `agent_duration`, `agent_active`, `agent_errors_total`; native `gen_ai.client.token.usage` |
| Spring AI | Native `spring.ai.chat.client` and `spring.ai.tool` observations; tool spans use `execute_tool` |
| Embabel | Native `embabel.agent.*`, `embabel.llm.*`, `embabel.tool.*` and tool-loop metrics, alongside Spring AI observations |

The [bundled dashboard](grafana/nutrition-planner.json) has **LangChain4j-specific
agent panels**, plus HTTP/JVM and trace views. Spring AI and Embabel's native
agent metrics need their own queries. Agent-node counts include orchestration
and shape-check steps, not just LLM requests: these are diagnostics, not normalized
cost/performance measurements. Set distinct `SPRING_APPLICATION_NAME` values such
as `spring-ai-nutrition-planner` and `embabel-nutrition-planner` when sending several
samples to the same collector.

## Azure deployment and demo boundaries

[`azure.yaml`](azure.yaml) and [`infra/`](infra/) provide an Azure Developer CLI
deployment for Container Apps, Container Registry and Azure OpenAI:

```bash
azd auth login
azd up
```

Deployment provisions billable resources and needs appropriate region/model
availability and quota. Embabel's container must include Bash and writable
temporary storage for its skill; do not assume a shell-free minimal runtime
image can execute it. Live cloud provisioning and model quality are not covered
by the offline build.

These are **demo applications**, with fixture profiles, public demo credentials
and simplified security configuration, not production services. Embabel executes
only the trusted bundled skill through a host process: its confined working
directory is **not a strong sandbox**, and the process inherits the application
environment. Do not load untrusted skills. Spring AI's current-month skill uses
a narrow Java tool instead of shell execution.

## Upstream references

- [LangChain4j 1.20.0](https://github.com/langchain4j/langchain4j/releases/tag/1.20.0), [Boot integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/), [agentic workflows](https://docs.langchain4j.dev/tutorials/agents/)
- [Spring AI 2.0.1](https://github.com/spring-projects/spring-ai/releases/tag/v2.0.1), [version-pinned migration notes](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)
- [Embabel 1.5.2](https://github.com/embabel/embabel-agent/releases/tag/v1.5.2), [typed states](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-docs/src/main/asciidoc/reference/states/page.adoc)
- [Spring 7's Jupiter 6 requirement](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-test/src/main/java/org/springframework/test/context/junit/jupiter/SpringExtension.java)
