# Spring AI nutrition planner

Independent Java 25 / Maven sample using Spring Boot **4.2.0-RC1** and Spring AI
**2.1.0-RC1** (both **release candidates**), and community
**spring-ai-agent-utils 0.13.0**. Boot manages Spring Framework 7.1.0-RC1,
Jackson 3.1.7 / 2.21.7, and JUnit Jupiter 6.1.3. Spring AI manages MCP Java SDK 2.1.0.
JUnit 6 is required by this Spring Test generation; the tests retain Jupiter
annotations and use `@MockitoBean`.

## What the sample demonstrates

| Capability | Implementation |
| --- | --- |
| Fluent, autoconfigured model client | Native `ChatClient.Builder`; one selected provider |
| Tool execution | Native `ToolCallingAdvisor`, automatically registered by `ChatClient` |
| Dynamic tool discovery during nutrition audits | Native `ToolSearchToolCallingAdvisor` and `LuceneToolIndex`; request-local index IDs are evicted after each audit |
| Structured responses and nutrition calculations | Native structured conversion and `@Tool` methods on `WeeklyPlan` |
| MCP server | Native `@McpTool`, authenticated Streamable HTTP at `/mcp` |
| Skills | Community `SkillsTool`, including JAR-safe resource loading |
| Browser human interaction | Community `AskUserQuestionTool` with application-owned SSE/question lifecycle |
| Personas | Application system prompts, not framework agent types |
| Parallel profile/seasonal lookup | Application `Workflow.parallel`, with typed results, virtual threads and Micrometer context propagation |
| Nutrition audit/revision policy | Application `ValidationRetryAdvisor`, outside the native tool-calling loop |
| Opt-in pattern lab | Application typed routing and bounded workers; native 2.1 RC session advisors and tool-call confirmation |

This is not a native Spring AI multi-agent planner, GOAP workflow, or durable
workflow engine. MCP elicitation and sampling are not used. Browser interaction
is not exposed as an MCP tool.

The first candidate and every revision are audited. A passing candidate returns
immediately. At most **three revisions** follow the initial plan, so exhaustion
means **four failed audits** and an explicit `NutritionPlanValidationException`.
REST and MCP return an error without a plan. The browser alone shows the final
audited candidate with a requirements-not-met warning, escaped audit feedback,
and a **Try again** button. It is not a validated or allergy-safe plan. Rerunning
is a user decision and uses the current form selections and instructions.
Original requested days, meals, country, profile, instructions,
seasonal ingredients, and collected browser answers survive revisions. A
deterministic shape check also rejects missing/extra meals and duplicate days.
Every audit also receives the recorded questions and answers, so it can apply
the user's cooking preferences without treating an already completed question as
an unmet instruction. Exhaustion displays the final audit feedback in the browser.
Malformed model output, missing audits/answers, and tool failures propagate as
errors rather than empty successful responses.

Nutrition estimates and dietary compliance still depend on model quality;
this is a framework demonstration, not independently verified medical advice.
The demo profile targets **2,500 kcal per day**. In addition to the model's
nutrition audit, deterministic acceptance requires every requested day to total
**2,250–2,750 kcal inclusive**; totals are checked per day and cannot offset one
another. The bound is an example acceptance policy, not personalized nutrition
guidance.

Spring AI **2.1.0-RC1** and Boot **4.2.0-RC1** are deliberate pre-GA choices
for demonstrating the matching native session and tool-confirmation APIs. This
enables comparisons against APIs not available in the prior GA line, with the
tradeoff that dependencies and integration details may change before GA; recheck
the native session/confirmation behavior when upgrading.

## Run and test

From the repository root, with Maven and JDK 25 installed:

```bash
mvn -B -ntp -f spring-ai/pom.xml test
mvn -B -ntp -f spring-ai/pom.xml package
SPRING_PROFILES_ACTIVE=ollama mvn -f spring-ai/pom.xml spring-boot:run
```

Tests use deterministic fake chat responses, real native advisor/tool execution,
and local HTTP MCP requests. Provider regression tests also drive the actual
OpenAI SDK (including Azure configuration) and Ollama HTTP client through the
complete skill, structured-plan, tool-search and audit loop against loopback
fixtures. They do not call external model APIs, Docker,
Ollama, or telemetry collectors. Resource tests load the skill from both a loose
classpath and a JAR with a `BOOT-INF/classes` prefix.

The default profile is `openai`. Select **one** of these provider profiles:

| Profile | Environment variables | Spring AI 2.x configuration |
| --- | --- | --- |
| `openai` | `OPENAI_API_KEY`, `OPENAI_MODEL_NAME` (default `gpt-4o`) | `spring.ai.model.chat=openai`; `spring.ai.openai.chat.model` |
| `azure` | `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_DEPLOYMENT_NAME` | Same native `openai` provider; `spring.ai.openai.base-url`, `microsoft-foundry=true`, `microsoft-deployment-name`, and `chat.model` |
| `ollama` | `OLLAMA_BASE_URL` (default `http://localhost:11434`), `OLLAMA_MODEL_NAME` (default `qwen2.5`) | `spring.ai.model.chat=ollama`; `spring.ai.ollama.chat.model`; automatic model pulls disabled |

The old `azure-openai` and `openai-sdk` starters/provider names are replaced by
the unified OpenAI starter. The deprecated `.chat.options.*` properties are
flattened to `.chat.*`. Embedding, image, audio, and moderation models stay
disabled; selecting Ollama does not create an OpenAI model.

Port remains `8080`; demo login/HTTP Basic credentials remain `alice` / `123456`.
Use appropriate credentials, access controls and TLS before deployment.

## Opt-in pattern lab

The validated `/api/nutrition-plan`, browser, and MCP workflows remain the
baseline. The lab is **off by default**: authenticated requests to its routes
return 404 unless `patterns` is selected **alongside exactly one provider**.
It returns illustrative cooking advice, never a `WeeklyPlan` or a claim that the
nutrition audit passed.

```bash
SPRING_PROFILES_ACTIVE=ollama,patterns mvn -pl spring-ai spring-boot:run
# Or use openai,patterns / azure,patterns with the environment variables above.
curl -fsS -u alice:123456 http://localhost:8080/api/patterns
```

| Runnable pattern | Mechanism and code | Invocation | Bound / tradeoff |
| --- | --- | --- | --- |
| Typed routing | **Application** classification into a closed specialist enum, then one native `ChatClient` call; [PatternLabService](src/main/java/com/example/nutritionplanner/patterns/PatternLabService.java) | `POST /api/patterns/routing` with `{"prompt":"Quick plant-based cooking ideas"}` | Two model invocations. `UNSUPPORTED` is 422; unknown/malformed classifications fail, not a fallback specialist. This is not a native router engine. |
| Orchestrator/workers | **Application** model-produced task list, virtual-thread workers, ordered synthesis; [PatternLabService](src/main/java/com/example/nutritionplanner/patterns/PatternLabService.java) | `POST /api/patterns/orchestrator-workers` with `{"prompt":"Suggest economical quick lentil dishes"}` | One planner + 1-3 concurrent workers + one synthesis: 3-5 calls. Workers have a 30-second deadline; failure cancels peers and skips synthesis. |
| Session memory / compaction | **Native** `SessionAdvisors`, append-only session events, `TurnCountTrigger`, `WindowCompactionStrategy`; [PatternMemoryService](src/main/java/com/example/nutritionplanner/patterns/PatternMemoryService.java) | Create with `POST /api/patterns/memory/sessions`; send `{"prompt":"What could I cook from the demo pantry?"}` to `POST /api/patterns/memory/sessions/{id}/turns` | At most eight turns, two model calls and one read-only tool per turn. More than three active turns triggers a window retaining the newest two complete turns, including tool call/result pairs. |
| Tool-call confirmation | **Native** `requiresConfirmation` metadata, paused `ToolCallingAdvisor`, `ToolCallDecisions` and `ToolCallingManager`; [PatternConfirmationService](src/main/java/com/example/nutritionplanner/patterns/PatternConfirmationService.java) | `POST /api/patterns/confirmations` with `{"prompt":"Remember to rinse lentils"}`; inspect the proposal, then `POST /api/patterns/confirmations/{id}/decision` with `{"decision":"APPROVE"}` or `{"decision":"DENY"}` | One initial model call + one resume call. Approval can save only the displayed, server-stored note in this temporary demo. Denial never executes it. No external operation or real account data changes. |

Routing and orchestrator/workers are **existing design patterns newly
demonstrated here**. Session memory/turn-aware compaction and native tool-call
confirmation are **new APIs in the 2.1 release candidate**. Ordered message parts
are also new upstream; native provider messages are retained across confirmation
resume, not reconstructed from text. The existing parallel preparation and
audit/revision examples are retained rather than duplicated.

All lab prompts are 1-1000 characters. Worker instructions, model output and tool
arguments are bounded, including a 32 KiB serialized assistant-message ceiling
covering reasoning and opaque provider parts before session storage.
[PatternModels](src/main/java/com/example/nutritionplanner/patterns/PatternModels.java)
counts calls **inside** the native tool loop and admits at most eight in-flight
model invocations across the lab. Counts describe `ChatModel` invocations, not
SDK transport retries, tokens or cost. Provider-specific options are mutated,
not replaced with an incompatible generic options object.

### Try the stateful examples

The commands below use `jq` only to extract the returned opaque ID:

```bash
SESSION=$(curl -fsS -u alice:123456 -X POST \
  http://localhost:8080/api/patterns/memory/sessions | jq -r .id)
curl -fsS -u alice:123456 -H 'Content-Type: application/json' \
  "http://localhost:8080/api/patterns/memory/sessions/$SESSION/turns" \
  -d '{"prompt":"I prefer quick cooking. What is in the demo pantry?"}'
# Repeat with distinct cooking requests. The fourth successful turn demonstrates compaction.
curl -fsS -u alice:123456 "http://localhost:8080/api/patterns/memory/sessions/$SESSION"

curl -fsS -u alice:123456 -H 'Content-Type: application/json' \
  http://localhost:8080/api/patterns/confirmations \
  -d '{"prompt":"Remember to rinse the lentils before cooking"}'
# Read the returned id and proposed.text before deciding. Replace CONFIRMATION_ID:
curl -fsS -u alice:123456 -H 'Content-Type: application/json' \
  http://localhost:8080/api/patterns/confirmations/CONFIRMATION_ID/decision \
  -d '{"decision":"DENY"}'
```

Memory responses expose `loggedEvents`, `activeMessages`, `activeTurns`,
`compactions`, `activeToolCalls` and `activeToolResults`, so a window change is
observable. Compaction changes the **active prompt view**, not the event log:
covered events remain until the session expires. This example does not summarize
or persist conversations across application restarts.

Both kinds of state are server-generated and bound to the authenticated
principal, with four states per user and 32 total **per stateful pattern**.
Memory expires after ten minutes; confirmation records and saved demo notes
after five. Expiry is enforced on access and cleaned once a minute (an in-flight
operation holds its own state lock). In-memory storage is bounded by these
limits and the per-session turn/output bounds; it is not distributed or durable.
Use a new session after a failed memory turn.

Confirmation returns `PENDING` only when the native loop really paused.
`GET /api/patterns/confirmations/{id}` exposes the actual proposal, decision,
saved note, terminal status and call count. Decisions accept no replacement tool
arguments. Concurrent decisions and replay return 409; a different user or an
expired ID sees 404. Resume exposes no tools, so an approval cannot authorize a
second model-requested action. If the note was saved but the subsequent model
call fails, the record says `FAILED` with `saved` still present; it cannot be
replayed. A denial is also terminal.

Invalid JSON/input is 400, unsupported culinary routing is 422, capacity/call
limits are 429, and malformed or failed model responses are explicit errors,
not successful advice. These guards bound the demo; model-selected routing and
advice are still probabilistic. No filesystem, shell, purchase, email, or cloud
mutation tools are registered in this lab.

### Scope of the upgrade

Community 0.13.0 still supplies `SkillsTool` and `AskUserQuestionTool`; its
published POM targets Spring AI 2.0.1. The retained skill/HITL workflow and real
provider tool loops are regression-covered on 2.1.0-RC1. Community `TaskTools`
is **not** registered: this small worker example needs no nested filesystem or
shell agents. It must not be described as a new Spring AI core workflow engine.

The native memory and confirmation tests run through real advisors, including
tool-turn retention, compaction, denial, ownership, replay and failure branches.
Loopback OpenAI, Azure and Ollama HTTP fixtures exercise all four patterns
through the actual provider implementations, not a mocked top-level service.
This proves the wiring, not that every deployed model follows the requested
schema or makes good culinary decisions.

## Browser, REST and MCP

The browser at `/` submits the existing `meals[MONDAY]=LUNCH` form to `/plan`.
`GET /interactions/{id}/events` starts generation; only its authenticated owner
may connect or answer via `POST /interaction/{id}/answers`. Single-choice,
multi-select and free-text answers are supported. Answer submission returns 204
so it cannot overwrite a concurrently delivered plan. Errors render an alert.
Invalid selections and answers return an HTML 400 fragment in a separate error
area, leaving any pending question available for correction. REST request-validation
exceptions still produce JSON ProblemDetail responses.
A terminal `done` event closes the EventSource. Completion, disconnect, timeout
and application shutdown remove interactions and release waiting questions.
Answers time out after five minutes; an interaction expires after ten minutes,
including one whose browser never connects.

Authenticated `POST /api/nutrition-plan` accepts the same successful request and
response shapes as the sibling samples:

```json
{
  "days": [{"day": "MONDAY", "meals": ["LUNCH"]}],
  "countryCode": "DE",
  "additionalInstructions": "Under 30 minutes"
}
```

`NutritionPlannerController.PlanRequest` adapts this to the existing internal
`WeeklyPlanRequest`. All entry points use its validation rules. Success returns
the existing `WeeklyPlan` JSON (`days`, nullable `breakfast`/`lunch`/`dinner`,
recipe ingredients and nutrition). Invalid HTTP requests return 400; exhausted
audits return **422 ProblemDetail** with `audits` and `feedback`.

MCP uses **Streamable HTTP**, not the browser SSE protocol, at `/mcp`.
Clients must supply HTTP Basic authentication on requests. The native tool is
`createNutritionPlan`; its argument remains the existing map-form schema:

```json
{
  "request": {
    "meals": {"MONDAY": ["LUNCH"]},
    "countryCode": "DE",
    "additionalInstructions": "Under 30 minutes"
  }
}
```

The authenticated servlet principal is carried in `McpTransportContext`, not
read from a thread-local inside the MCP handler. There is no user-name argument
to impersonate another profile. MCP failures become error tool results.
**REST and MCP are genuinely non-interactive**: no ask-user tool is registered,
and no handler substitutes empty answers.

## Skills and observability migration

`skills/current-month/SKILL.md` still loads through community `SkillsTool`.
It now instructs the model to call the application-defined `currentMonth` tool,
registered with Spring AI's native `@Tool` annotation and backed by an injectable
UTC `Clock`. No shell/file tools or shell scripts are
registered or required, including when running the executable JAR.

Add `observability` to the chosen provider profile to enable OTLP metrics and
traces. Exports are disabled otherwise. Boot 4 tracing uses
`management.opentelemetry.tracing.export.otlp.endpoint` (default
`http://localhost:4318/v1/traces`); metrics use
`management.otlp.metrics.export.url` (default
`http://localhost:4318/v1/metrics`) with a five-second step. Override destinations
with `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` and `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`.

Native observations include `spring.ai.chat.client` and `spring.ai.tool`.
Spring AI 2 tool spans use `execute_tool <tool-name>` and
`gen_ai.operation.name=execute_tool`, replacing the old `tool_call`/`framework`
labels. Prompt/tool content logging is not enabled by default.

## Verified upstream references

- [Spring AI 2.1.0-RC1 release](https://github.com/spring-projects/spring-ai/releases/tag/v2.1.0-RC1)
- [Pinned 2.1 migration notes and ordered message parts](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)
- [Effective agents: five application-composed workflow examples](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/effective-agents.adoc)
- [Session advisors and tool-loop integrity](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/session/chat-client.adoc)
- [Turn-aware compaction](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/session/compaction.adoc)
- [Native tool execution and confirmation](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java)
- [Native confirmation decisions](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/spring-ai-model/src/main/java/org/springframework/ai/model/tool/ToolCallDecisions.java)
- [Native lexical tool search](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/advisors/spring-ai-tool-search-advisor/src/main/java/org/springframework/ai/chat/client/advisor/toolsearch/ToolSearchToolCallingAdvisor.java)
- [Current OpenAI/Azure provider properties](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC1/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/AbstractOpenAiProperties.java)
- [Community 0.13.0 skill loader](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.13.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/utils/Skills.java)
- [Community human-question tool](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.13.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java)
- [Community TaskTools (not wired here)](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.13.0/docs/tools/TaskTools.md)
