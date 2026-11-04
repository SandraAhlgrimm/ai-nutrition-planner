# AI Nutrition Planner

Three independently runnable Java/Spring samples implement the same meal-planning
use case with **LangChain4j**, **Spring AI**, and **Embabel**. The comparison is
about their programming models and the features actually exercised here, not a
ranking of model quality or a performance benchmark.

[Browser conference talks](presentations/README.md) provide four standalone
45-minute tracks: comparison, LangChain4j, Spring AI and Embabel. Open the
self-contained [`presentations/index.html`](presentations/index.html) locally;
slides, speaker notes and synthetic demos work offline.

The browser talks and [historical presentation slides](slides.pdf) are retained
unchanged from earlier work. The code and comparison tables below reflect
**November 2026**: the validated planner remains the baseline, with separate,
runnable **opt-in pattern labs**.

## Versions and implementation entry points

Dependencies and APIs were checked against published artifacts and pinned tags
on **2026-11-03/04**. All modules use **Java 25**, Spring Framework **7**,
Jackson **3**, and Boot-managed **JUnit Jupiter 6+**. They do not have to use
identical Boot or AI versions to implement comparable REST behavior.

| Sample | AI release and stability | Boot / Spring Framework | Other selected dependencies | Start reading |
| --- | --- | --- | --- | --- |
| [LangChain4j](langchain4j/README.md) | BOM/core **1.23.0**; agentic, patterns and Boot 4 starters **1.23.0-beta33**, **experimental** agentic APIs | **4.1.2 / 7.0.10** (GA) | MCP Java SDK **2.1.0**; Jackson BOMs **3.2.4 / 2.22.4** | [Native workflow composition](langchain4j/src/main/java/com/example/nutritionplanner/AgentConfiguration.java), [typed agents](langchain4j/src/main/java/com/example/nutritionplanner/Agents.java) |
| [Spring AI](spring-ai/README.md) | **2.1.0-RC1**, **release candidate** | **4.2.0-RC1 / 7.1.0-RC1**, **release candidates** | Community agent utilities **0.13.0**; native session advisor **2.1.0-RC1**; MCP SDK **2.1.0** | [Baseline orchestration](spring-ai/src/main/java/com/example/nutritionplanner/NutritionPlannerAgent.java), [pattern lab](spring-ai/src/main/java/com/example/nutritionplanner/patterns/) |
| [Embabel](embabel/README.md) | Agent/skills **1.5.4**, released; new decision APIs remain **experimental** | **4.1.2 / 7.0.10** (GA) | Spring AI **2.0.2** with native provider options; released MCP/observability starters | [Typed actions, states and goal](embabel/src/main/java/com/example/nutritionplanner/NutritionPlannerAgent.java), [revision budget](embabel/src/main/java/com/example/nutritionplanner/RevisionBudget.java) |

Spring AI intentionally takes the approved RC line to demonstrate its new
session and confirmation APIs on the matching upstream Boot generation.
LangChain4j and Embabel stay on verified GA Boot versions; no version-parity
upgrade forces them onto RCs. LangChain4j preserves its newer Jackson BOMs rather
than letting Boot downgrade them. Spring 7's `SpringExtension` requires Jupiter
6; do not force JUnit 5 into any module.

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

Invalid requests are rejected before model execution. Duplicate days in a
model-generated candidate remain available for the audit/revision loop: nutrition
tools total all of that day's entries, while deterministic checks reject the
duplicate shape rather than letting a map-key exception bypass the audit.

This limit counts logical candidates and audits, not individual provider HTTP
attempts or tool-calling turns. Embabel's rejection is explicitly `NonRetryable`
so its action-retry machinery cannot turn exhaustion into a fifth audit.

Nutrition totals sum **model-generated estimates**. Structural checks are
deterministic; judgments about ingredients, allergens and dietary suitability
still rely on the model. This is a framework demonstration, not a clinically
validated nutrition service.

## Baseline implementation comparison

**Native** means supplied by the named framework. **Application** means code in
this repository. **Community** denotes a separate Spring AI community library.
**Not demonstrated** does not mean the framework cannot support the feature.

| Pattern | LangChain4j | Spring AI | Embabel |
| --- | --- | --- | --- |
| **Parallel preparation** | Native `parallelBuilder()` with an application-supplied virtual-thread executor | Application `Workflow.parallel()` with typed results and virtual threads | Native concurrent process schedules independent typed actions |
| **Orchestration and state** | Native typed `@Agent` interfaces, sequence/conditional/loop builders and per-invocation `AgenticScope` | Application orchestration around native `ChatClient`; not a native multi-agent planner | Native GOAP action graph, typed blackboard, `@State` and `@AchievesGoal` |
| **Audit/revision loop** | Native loop, end-of-iteration audit check, application shape audit and final acceptance gate | Application `ValidationRetryAdvisor` outside native tool-calling loop | Native state transitions with application `RevisionBudget` and non-retryable rejection |
| **Nutrition tools** | Native `@Tool`; current plan supplied through hidden, invocation-local `AgenticScope` | Native `@Tool` and `ToolCallingAdvisor` | Native `@LlmTool` via `withToolObject()` |
| **Tool discovery** | Fixed nutrition tools; search not demonstrated | Native `ToolSearchToolCallingAdvisor` with lexical metadata search via `LuceneToolIndex` | Native category-based `@UnfoldingTools` facade progressively reveals tools |
| **Human-in-the-loop** | Not demonstrated | Community `AskUserQuestionTool` in browser only; application owns SSE, answers, cancellation and timeouts | Not demonstrated in the nutrition graph |
| **Agent skills** | Not demonstrated; application supplies the current month | Community `SkillsTool` plus the application-defined `currentMonth` tool registered with native `@Tool`; no shell tools | Native `Skills` references and process execution of the bundled Bash script |
| **Persona** | Application role prompts via `@SystemMessage` | Application role prompts via `.system()` | Native `Persona` prompt contributors |
| **MCP server** | Application bridge to the official Java SDK's authenticated stateless Streamable HTTP `/mcp` | Native `@McpTool`, Streamable HTTP `/mcp` | Native remote goal export, Streamable HTTP `/mcp` |

Tool discovery is intentionally not collapsed into a single checkbox: this Spring
AI sample uses Lucene full-text (lexical) search over tool metadata; Embabel
exposes a known facade whose category selection reveals tools. Neither sample
demonstrates embedding-based semantic tool search.
Likewise, exposing a REST endpoint is not an MCP implementation.

## Runnable opt-in pattern labs

Every lab is disabled by default and protected by the existing authentication.
Enable `patterns` **with exactly one provider**, never instead of a provider.
These routes return illustrative culinary results, **not `WeeklyPlan`**; they
cannot bypass or silently replace the validated planner. No models are called
merely to start the application or read a catalog.

Start **one** module below, using an unused port and an already configured
provider (substitute `openai` or `azure` for `ollama` as appropriate):

| Module | Start from the repository root | Full walkthrough |
| --- | --- | --- |
| LangChain4j | `SERVER_PORT=8090 SPRING_PROFILES_ACTIVE=ollama,patterns mvn -pl langchain4j spring-boot:run` | [Pattern lab](langchain4j/patterns/README.md) |
| Spring AI | `SERVER_PORT=8090 SPRING_PROFILES_ACTIVE=ollama,patterns mvn -pl spring-ai spring-boot:run` | [Pattern lab](spring-ai/README.md#opt-in-pattern-lab) |
| Embabel | `SERVER_PORT=8090 SPRING_PROFILES_ACTIVE=ollama,patterns mvn -pl embabel spring-boot:run` | [Pattern lab](embabel/README.md#opt-in-pattern-lab) |

LangChain4j's decision routing/cascade additionally require an explicit
`PATTERNS_DECISION_MODEL_NAME`: an account/model supporting OpenAI's
`/v1/decisions`, or Ollama 0.35.0+ with an installed decision model supporting
`/v1/systemone`. An ordinary chat model is not a decision model. Without that
selection, the mapper works and the other catalog entries report unavailable.
Azure is mapper-only in this lab; it does not redirect to OpenAI. Catalog
availability is configuration readiness, **not a live entitlement/health check**.
No sample downloads a decision model automatically.

Use the common request helper for the table below:

```bash
BASE_URL=http://localhost:8090
PROMPT='{"prompt":"Suggest a quick Mediterranean dinner with lentils and carrots."}'
post() {
  curl --fail-with-body --user alice:123456 \
    --header 'Content-Type: application/json' \
    --data "$2" "$BASE_URL$1"
}
curl --fail-with-body --user alice:123456 "$BASE_URL/api/patterns"
```

| Sample / runnable pattern | Mechanism and code anchor | Run after starting that module | Meaning, bound and tradeoff |
| --- | --- | --- | --- |
| LangChain4j: decision routing | **Native** `DecisionRouterPlanner`; [PatternLab](langchain4j/src/main/java/com/example/nutritionplanner/patterns/PatternLab.java) | `post /api/patterns/decision-routing "$PROMPT"` | Threshold 0.8 can select multiple culinary specialists; at most 1 decision + 3 chat calls. No match/refusal is 422, not a default specialist. |
| LangChain4j: confidence cascade | **Native** `ConfidenceCascadePlanner` plus **application** final-tier guard; [PatternLab](langchain4j/src/main/java/com/example/nutritionplanner/patterns/PatternLab.java) | `post /api/patterns/confidence-cascade "$PROMPT"` | At most 3 candidate calls + 3 judgments; the final tier is judged too. Different prompts on the same chat model, not proven cheaper/better models. |
| LangChain4j: parallel mapper | **Native** `parallelMapperBuilder` then `sequenceBuilder`; [PatternAgents](langchain4j/src/main/java/com/example/nutritionplanner/patterns/PatternAgents.java), [PatternLab](langchain4j/src/main/java/com/example/nutritionplanner/patterns/PatternLab.java) | `post /api/patterns/parallel-mapper '{"prompt":"Quick cooking","items":["Lentil soup","Carrot salad"]}'` | One isolated item slot per brief, ordered results and synthesis. 1-4 items; N+1 chat calls, at most 5. Failed workers do not yield a partial success. |
| Spring AI: typed specialist routing | **Application** classification/dispatch using native `ChatClient`; [PatternLabService](spring-ai/src/main/java/com/example/nutritionplanner/patterns/PatternLabService.java) | `post /api/patterns/routing "$PROMPT"` | Exactly one selected specialist answers; two model invocations. Closed enum, no silent fallback. |
| Spring AI: orchestrator/workers | **Application** task planner, virtual-thread workers and synthesis; [PatternLabService](spring-ai/src/main/java/com/example/nutritionplanner/patterns/PatternLabService.java) | `post /api/patterns/orchestrator-workers "$PROMPT"` | Dynamic 1-3 subtasks: 3-5 model calls. Worker failure cancels peers; workers have a 30-second deadline. Not a native workflow-engine claim. |
| Spring AI: session memory | **Native 2.1 RC** `SessionAdvisors` and turn-window compaction; [PatternMemoryService](spring-ai/src/main/java/com/example/nutritionplanner/patterns/PatternMemoryService.java) | `post /api/patterns/memory/sessions '{}'`, then [send turns](spring-ai/README.md#try-the-stateful-examples) | Creation uses no model. Up to 8 turns, 2 model calls/turn; after more than 3 active turns, retain the newest 2 whole turns, including tool call/results. The event log is not erased by compaction. |
| Spring AI: tool confirmation | **Native 2.1 RC** pause/`ToolCallDecisions`/resume plus **application** ownership and one-shot lifecycle; [PatternConfirmationService](spring-ai/src/main/java/com/example/nutritionplanner/patterns/PatternConfirmationService.java) | `post /api/patterns/confirmations '{"prompt":"Remember to rinse lentils"}'`, then [inspect and decide](spring-ai/README.md#try-the-stateful-examples) | Initial request pauses before saving a temporary local demo note. Approve or deny the stored call, then resume: at most 2 model calls. No real purchase, email, or cloud mutation. |
| Embabel: typed classification/decision | **Native experimental 1.5.4** `LlmDecisionServiceFactory`, `ClassificationSpec`, typed `DecisionSpec`; [TypedRoutingExample](embabel/src/main/java/com/example/nutritionplanner/TypedRoutingExample.java) | `post /api/patterns/typed-routing "$PROMPT"` | Select a culinary route and assess a time preference; **does not execute a specialist**. 1-2 model sends; typed no-match/inconclusive remain distinct, with no invented confidence. |
| Embabel: supervisor | **Native** `SupervisorInvocation`, request-local scope and actions-as-tools; [SupervisorExample](embabel/src/main/java/com/example/nutritionplanner/SupervisorExample.java) | `post /api/patterns/supervisor "$PROMPT"` | LLM chooses order of 2 AI worker actions; a non-LLM composer consumes their actual results. Application guards cap 8 model sends, 3 tool calls, 2 per batch and one execution/action. |
| Embabel: hybrid reducer | **Native** Hybrid utility selection plus a real terminal goal; [HybridExample](embabel/src/main/java/com/example/nutritionplanner/HybridExample.java) | `post /api/patterns/hybrid "$PROMPT"` | Gather 2 curated local note sets, then synthesize and stop. Normally 3 actions/1 model send; ceiling 4 actions/2 sends including binding retries. Not web research, RAG, or a pure Utility event loop. |

Input bounds, output envelopes and error statuses intentionally remain
framework-specific; there is no shared runtime abstraction. LangChain4j/Spring AI
prompts allow 1,000 characters, Embabel 2,000; all reject invalid input before a
model call. Embabel's typed `no_match`/`inconclusive` outcomes can be HTTP 200
**without a route**, unlike LangChain4j's 422 no-match. Those are explicit
uncertainty results, not fabricated successful advice.

Call limits count logical framework/model invocations, not provider transport
retries, tokens, cost or guaranteed wall-clock billing. Native orchestration
still needs application acceptance rules and resource limits. Spring AI's
stateful demos additionally enforce opaque owner-bound IDs, expiry and capacity
limits; confirmation replay or a concurrent decision is rejected. Module guides
document their precise limits and failure behavior.

## Broader framework capabilities

This table surveys the **pinned releases**, not unreleased `main` branches.
**Available / not demonstrated** means an upstream capability was checked but
is not a runnable feature of this repository. A pattern composed in application
code is not a native workflow engine. LangChain4j's agentic/pattern APIs and
skills/guardrails APIs are experimental; Embabel's new decision APIs are also
explicitly experimental. Spring AI 2.1 APIs below are on a release candidate.

| Pattern / why use it / tradeoff | LangChain4j 1.23.0 / beta33 | Spring AI 2.1.0-RC1 + community 0.13.0 | Embabel 1.5.4 |
| --- | --- | --- | --- |
| **Chaining**: make dependencies explicit; serial stages add latency | Native sequence/conditional builders, used by the baseline. [Agent APIs][lc-agents] | Application calls around native `ChatClient`; the official `ChainWorkflow` is example code, not a library engine. [Effective agents][sa-workflows] | Native typed actions and preconditions let GOAP derive the dependency path. [Planners][emb-planners] |
| **Fan-out / map-reduce**: independent work then combine; bound concurrency and preserve item association | Native parallel and parallel-mapper builders; mapper lab is opt-in. [Agent APIs][lc-agents] | Application virtual-thread fan-out in the baseline and worker lab; aggregation is application-owned. [Effective agents][sa-workflows] | Native concurrent scheduling of independent actions; typed artifacts support reducer-style Hybrid composition. [Process][emb-process], [planners][emb-planners] |
| **Routing**: select relevant specialists; wrong classifications choose the wrong work | Native conditional workflows and decision routers; a threshold router may select several specialists. [Agent APIs][lc-agents] | Application structured enum classification and dispatch; the lab rejects unknown/unsupported routes. [Effective agents][sa-workflows] | Experimental native classification and typed decisions. The lab selects a route and assesses a preference; it does not execute a downstream specialist. [LLM decisions][emb-decisions] |
| **Orchestrator/workers / supervisor**: choose subtasks dynamically; enforce work limits | Native LLM supervisor available, not demonstrated here; explicit builders remain the baseline. [Agent APIs][lc-agents] | Application bounded worker lab. Community `TaskTools` provides subagents but is not registered here and is not Spring AI core. [Workflows][sa-workflows], [TaskTools][sa-tasks] | Native Supervisor planner lets an LLM select type-described actions. GOAP and Utility are different planning strategies, not aliases for a supervisor. [Planners][emb-planners] |
| **Reflection / evaluator-optimizer**: revise against feedback; an evaluator is not ground truth | Native loop composition; application owns the four-audit acceptance policy. [Agent APIs][lc-agents] | Application `ValidationRetryAdvisor`; native tool loops are inside the application evaluation loop. [Effective agents][sa-workflows] | Native `@State` lifecycle and GOAP action execution; application owns revision budget and audit rules. [States][emb-states] |
| **Decision / confidence cascade**: escalate when an assessment is insufficient; scores are model-dependent | Native decision agents and confidence-cascade planner. Lab adds an application guard for the last tier, which the native cascade does not grade. Same chat model across tiers: no cheaper-model claim. [Release][lc-release], [agent APIs][lc-agents] | Can be application-composed from structured calls; no confidence cascade is wired here. Native tool confirmation is a human decision, not model confidence. [Workflows][sa-workflows], [confirmation][sa-confirm] | New experimental `DecisionService` / `ClassificationService`; LLM-backed verdicts deliberately do not invent confidence. The lab demonstrates classification, not a cascade. [LLM decisions][emb-decisions] |
| **Goals / utility / blackboard**: choose work from current state; make termination visible | Native goal-oriented, blackboard and BDI pattern planners available, not demonstrated; per-invocation `AgenticScope` is used. Do not equate every goal-oriented planner with GOAP. [Agent APIs][lc-agents] | Application state/composition; this sample does not add a native GOAP or utility planner. [Effective agents][sa-workflows] | Native GOAP, Utility, Hybrid and Supervisor, with typed blackboards. Utility chooses immediate net value; Hybrid also stops on a satisfied goal. [Planners][emb-planners] |
| **Voting / debate**: combine independent opinions; correlated mistakes can still win | Native voting and debate planners available, not demonstrated. [Agent APIs][lc-agents] | Application fan-out/judging can compose the pattern; not demonstrated. [Effective agents][sa-workflows] | Can be composed with typed actions/planners; no voting/debate example is claimed here. [Planners][emb-planners] |
| **Guardrails**: reject invalid input/output; policy checks are not proof of truth | Experimental native AI Service input/output guardrails available. Baseline uses its own request/shape/audit policy. [Guardrails][lc-guards] | Native `SafeGuardAdvisor` is a simple keyword filter, not comprehensive protection; baseline/lab have application validation and budgets. [Source][sa-guards] | Native input/assistant guardrail interfaces with blackboard context; the nutrition auditor is a separate domain policy. [Guardrails][emb-guards] |
| **Tool search / progressive disclosure**: show fewer tools; retrieval can miss a necessary tool | Native simple, vector and decision-model search strategies; agent-builder wiring is new in 1.23. Fixed nutrition tools remain the demo. [Agent APIs][lc-agents] | Native `ToolSearchToolCallingAdvisor` with **lexical** Lucene metadata search is used. No embedding-based semantic search claim. [Source][sa-search] | Native category-based tool unfolding is used. Selecting a known category facade is not vector/semantic search. [Tools][emb-tools] |
| **Skills**: load reusable instructions on demand; trust and execution environment matter | Experimental native skills and classpath loaders available, not demonstrated. [Skills][lc-skills] | Community `SkillsTool` is used with a narrow Java clock tool; no shell tools. [Community release][sa-community] | Native skills/references are used with a trusted bundled Bash script. A working directory is not a strong sandbox. [Skills][emb-skills] |
| **Human input / tool approval**: pause for missing information or consent; bind decisions to an owner | Native human-input agents available, not demonstrated here. [Agent APIs][lc-agents] | Community browser questions remain; **native** 2.1 tool confirmation is separately demonstrated with pending/approve/deny/resume and replay protection. [Confirmation][sa-confirm] | Native `WaitFor` waits and confirmations available, not demonstrated in these graphs. [API][emb-hitl] |
| **Memory / context compaction**: retain relevant context; distinguish history from the model's view | Native message/token windows and agent `summarizedContext`; summaries add model calls. Neither is demonstrated as a cross-request conversation here. [Memory][lc-memory], [agent APIs][lc-agents] | Native session event log and turn-aware compaction demonstrated; application supplies owner checks, expiry and storage bounds. Covered events remain in the log. [Session advisors][sa-session] | Typed working blackboard by default; explicit `Conversation` / `Chatbot` for conversation history. Do not describe the baseline as a shared chat history or a demonstrated compaction loop. [Chatbots][emb-chat] |
| **MCP**: expose tools through a protocol; transport identity still belongs to the application | Native MCP client; this sample's HTTP **server** is an application integration with the official Java SDK, not a LangChain4j-native HTTP server. [Server guidance][lc-mcp] | Native client/server integrations; authenticated Streamable HTTP server is demonstrated. [MCP sources][sa-mcp] | Native remote goal export and MCP tool integrations; authenticated Streamable HTTP server is demonstrated. [Integration sources][emb-mcp] |
| **A2A**: call remote agents, not merely remote tools; cancellation/context semantics differ | Native `langchain4j-agentic-a2a` client agents available, not demonstrated. [Agent APIs][lc-agents] | No A2A integration is selected or demonstrated in this sample; this is not a claim that external integrations cannot provide it. | Native A2A server module available, not demonstrated. [Source][emb-a2a] |
| **Observability / testability**: inspect real boundaries; counters are not comparable benchmarks by default | Native model/AI Service/agent events, application agent metrics; 1.23 also adds `langchain4j-test` utilities (not a dependency here). [Release][lc-release], [agent APIs][lc-agents] | Native Micrometer client/tool observations; deterministic real-advisor and provider HTTP fixtures in this repo. | Native process/model/tool observations and test fakes; offline tests exercise actual planners. [Testing][emb-testing] |

### New release feature or newly demonstrated pattern?

| Framework | Newly available in the selected release | Already available, now demonstrated or retained |
| --- | --- | --- |
| LangChain4j 1.23.0 | Confidence cascade, decision agents, Ollama decision-model integration, agent-level tool-search wiring. [Release][lc-release] | Sequence, parallel/mapper, conditional/loop, supervisor and other pattern planners predate this refresh. The HTTP MCP server is a new application/SDK integration, not a newly invented LangChain4j server API. |
| Spring AI 2.1.0-RC1 | Native session memory/event log, turn-aware compaction, native tool-call confirmation, ordered message parts; legacy `ChatMemory` is deprecated. [Release][sa-release] | Chaining, parallelization, routing, orchestrator/workers and evaluator-optimizer remain application-composed examples. Community skills/questions and `TaskTools` are not new core workflow APIs. |
| Embabel 1.5.4 | Experimental classification/decision contracts, typed specs/results, LLM-backed services and decision-provider integrations. [Release][emb-release] | GOAP, Utility, Hybrid, Supervisor, typed state/blackboard, skills and MCP are pre-existing capabilities. The new labs demonstrate selected planners rather than claiming that 1.5.4 introduced them. |

### What the differences mean

**LangChain4j** makes the execution graph explicit. Specialized interfaces remain
small while framework builders express fan-out, sequencing and refinement.
The application still supplies the acceptance policy, shape checks and resource
configuration. The baseline is a composed workflow; the lab adds native mapper,
decision-routing and confidence-cascade examples, not an autonomous LLM
supervisor. Its experimental agentic API can change.

**Spring AI** is integration-first: model access, structured conversion, tool
execution/search and MCP are native building blocks. The application owns the
overall sequence, retry policy and interaction lifecycle. Community skills and
human-question tools add capabilities without turning `Workflow.parallel()` or
the new worker lab into a Spring AI workflow engine. Session memory/compaction
and tool-call confirmation, by contrast, are native 2.1 RC APIs; the application
still owns authorization, lifecycle and resource limits.

**Embabel** expresses the solution in domain types, actions and a goal, letting
the planner schedule ready actions. Explicit audit/revision states keep the
domain policy visible. Its application-level model bridge selects one Spring AI
`ChatModel` and registers it with Embabel's `SpringAiLlmService`; prompting and
planning remain native Embabel APIs. The optional labs distinguish native
Supervisor (LLM-selected actions) and Hybrid (utility selection plus goal
termination) from the baseline GOAP planner.

## Build and run

Prerequisites: **JDK 25**, **Maven**, and a provider with tool-calling and
structured-output support. Docker is optional for local Ollama and observability.
Embabel's bundled skill additionally requires **Bash and writable temporary
storage**. Confirm that `mvn -version` reports Java 25; set `JAVA_HOME` and prepend
its `bin` directory to `PATH` if Maven selects an older installed JDK.

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
`https://your-resource.openai.azure.com/`. A Foundry resource's
`https://your-resource.services.ai.azure.com/` root also works. Do not paste a
project URL containing `/api/projects/...` or a full `/responses` operation URL
into `AZURE_OPENAI_ENDPOINT`.

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

All three publish `createNutritionPlan` at **`/mcp`** using **Streamable HTTP**,
with authenticated identity supplied on transport requests. This is separate
from the browser's SSE event channel, and exposing REST alone would not satisfy
the MCP protocol.

| Sample | MCP mechanism | Tool input / output distinction |
| --- | --- | --- |
| [LangChain4j](langchain4j/README.md#mcp-server) | Official MCP Java SDK, application-owned **stateless** HTTP servlet; Basic auth on every request, not the browser cookie | Top-level REST-style `days`, `countryCode`, `additionalInstructions`; unchanged plan JSON in `structuredContent` and text |
| [Spring AI](spring-ai/README.md#browser-rest-and-mcp) | Native `@McpTool` and Spring AI Streamable HTTP server | Existing map-form `request.meals`; REST alone adapts the shared `days` list |
| [Embabel](embabel/README.md) | Native remote-goal export and Streamable HTTP server | Native goal input/export; textual plan result |

Use `tools/list` to inspect each schema. MCP exhaustion is an **error tool
result**, not a returned invalid plan or REST ProblemDetail. None takes a
model-controlled username as authentication. The optional lab endpoints do
not replace these planner tools.

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

The comparison links to release-tagged source, not a moving documentation
snapshot. A feature being available upstream does not imply this repository
demonstrates it or that a particular provider/model supports it.

- [LangChain4j 1.23.0 release][lc-release], [agentic APIs and patterns][lc-agents], [Boot 4 integration release](https://github.com/langchain4j/langchain4j-spring/releases/tag/1.23.0-beta33)
- [Spring AI 2.1.0-RC1 release][sa-release], [pinned migration notes](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc), [application-composed pattern guide][sa-workflows]
- [Embabel 1.5.4 release][emb-release], [planner strategies][emb-planners], [new experimental LLM decisions][emb-decisions]
- [Spring 7's Jupiter 6 requirement](https://github.com/spring-projects/spring-framework/blob/v7.0.10/spring-test/src/main/java/org/springframework/test/context/junit/jupiter/SpringExtension.java)

[lc-release]: https://github.com/langchain4j/langchain4j/releases/tag/1.23.0
[lc-agents]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md
[lc-memory]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/chat-memory.md
[lc-guards]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/guardrails.md
[lc-skills]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/skills.md
[lc-mcp]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/mcp-stdio-server.md
[sa-release]: https://github.com/spring-projects/spring-ai/releases/tag/v2.1.0-RC1
[sa-workflows]: https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/effective-agents.adoc
[sa-session]: https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/session/chat-client.adoc
[sa-confirm]: https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-model/src/main/java/org/springframework/ai/model/tool/ToolCallDecisions.java
[sa-search]: https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/advisors/spring-ai-tool-search-advisor/src/main/java/org/springframework/ai/chat/client/advisor/toolsearch/ToolSearchToolCallingAdvisor.java
[sa-guards]: https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/SafeGuardAdvisor.java
[sa-community]: https://github.com/spring-ai-community/spring-ai-agent-utils/tree/v0.13.0
[sa-tasks]: https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.13.0/docs/tools/TaskTools.md
[sa-mcp]: https://github.com/spring-projects/spring-ai/tree/v2.1.0-RC1/mcp
[emb-release]: https://github.com/embabel/embabel-agent/releases/tag/v1.5.4
[emb-planners]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/planners/page.adoc
[emb-decisions]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/llm-decisions/page.adoc
[emb-process]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/agent-process/page.adoc
[emb-states]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/states/page.adoc
[emb-guards]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/guardrails/page.adoc
[emb-tools]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/tools/page.adoc
[emb-skills]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/agent_skills/page.adoc
[emb-hitl]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-api/src/main/java/com/embabel/agent/core/hitl/WaitFor.java
[emb-chat]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/chatbots/page.adoc
[emb-mcp]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/integrations/page.adoc
[emb-a2a]: https://github.com/embabel/embabel-agent/tree/v1.5.4/embabel-agent-a2a
[emb-testing]: https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/testing/page.adoc
