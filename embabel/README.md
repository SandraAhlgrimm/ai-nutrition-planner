# Embabel nutrition planner

An independent Java 25 application with a validated GOAP nutrition-planning baseline
and three **opt-in** native pattern examples. November 2026 versions:

| Dependency | Previous | Current | Stability / compatibility |
| --- | --- | --- | --- |
| Embabel API, starters, skills, provider converters | 1.5.2 | **1.5.4** | Released October 28; the new decision APIs are explicitly **experimental** |
| Spring Boot | 4.1.1 | **4.1.2** | Released patch; this module does not require Boot 4.2 RC |
| Spring AI | 2.0.1 | **2.0.2** | Released patch on Embabel's Spring AI 2.0 line; no 2.1 RC dependency |
| Spring Framework | Boot-managed | **7.0.10** | Resolved by the Boot parent |
| Jackson | 3.1.5 | **3.1.7** | Boot-managed Jackson 3 |
| JUnit Jupiter | 6.0.3 | **6.0.3** | Boot-managed; Boot 4 / Spring 7 need Jupiter 6 or newer |

The published Embabel 1.5.4 platform uses Boot 4.1.1 / Spring AI 2.0.1.
The patch upgrades above are verified here with all three real Spring AI provider
adapters against local HTTP fixtures, not inferred from matching version numbers.

## Run

Use Java 25, Maven, and Bash (for the bundled current-month skill).
Make sure Maven also uses Java 25 (`mvn -version`). For a Homebrew installation,
set `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`
and prepend `$JAVA_HOME/bin` to `PATH` in the shell running Maven.

```sh
mvn -B -ntp -f embabel/pom.xml test
mvn -B -ntp -f embabel/pom.xml package
SPRING_PROFILES_ACTIVE=ollama mvn -f embabel/pom.xml spring-boot:run
```

The UI is at `http://localhost:8080`; the demo login remains `alice` / `123456`.
The JSON endpoint remains authenticated `POST /api/nutrition-plan`.
These credentials and the permissive demo CSRF policy are not production authentication.

### Daily calorie acceptance

The demo profile (`alice`) targets **2,500 kcal/day**. Every requested day's recipes
must total **2,250-2,750 kcal, inclusive** (+/-10% of the profile's daily target).
This is a deterministic check **in addition to** the model's dietary audit:
2,249 or 2,751 kcal fails, as does an obviously insufficient 190 kcal day.
One day's excess cannot offset another day's deficit; weekly averages are not used.
The same per-day rule applies when only one or two meals are requested: those
requested meals together must meet the range; no unrequested meals may be added.
For a custom profile, the inclusive limits are 90% rounded up and 110% rounded down
to whole kcal. This is demo policy, not personalized medical guidance.

Failures feed the existing revision cycle: one initial plan plus at most three
revisions, with every candidate audited. Exhaustion returns HTTP **422**, never an
invalid successful plan. REST, UI, and the exported MCP goal share this validation.

Choose exactly one model profile; add `observability` separately, for example
`SPRING_PROFILES_ACTIVE=azure,observability`. Without an explicit profile, `openai` is the default.

| Profile | Required configuration | Model default |
| --- | --- | --- |
| `openai` | `OPENAI_API_KEY`; optional `OPENAI_MODEL_NAME` | `gpt-4o` |
| `azure` | `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`; optional `AZURE_OPENAI_DEPLOYMENT_NAME` | `gpt-4o` deployment |
| `ollama` | Optional `OLLAMA_BASE_URL`, `OLLAMA_MODEL_NAME` | `http://localhost:11434`, `qwen2.5` |

Application configuration selects one Spring AI `ChatModel` using
`spring.ai.model.chat` and wraps it with Embabel's maintained `SpringAiLlmService`.
The bridge explicitly uses the released `OllamaOptionsConverter` or
`StandardOpenAiOptionsConverter`: Spring AI 2 requires provider-specific options,
including when Embabel adds tool callbacks. The generic default converter is not
a production provider adapter. The Ollama converter's artifact also contains
catalogue autoconfiguration; that autoconfiguration is excluded so merely using
the converter cannot enable model discovery or another provider.
Model properties use Spring AI 2's flattened `spring.ai.ollama.chat.model` and
`spring.ai.openai.chat.model` keys, not the deprecated `chat.options.model` aliases.
All planning and prompting above that boundary use Embabel APIs. Inactive providers
need no credentials. Ollama does not discover or download models at startup; pull a
tool-capable model yourself before making planning requests.

Azure uses Spring AI's OpenAI SDK integration with `microsoft-foundry: true`.
`ModelConfiguration` accepts the resource-root endpoint supplied by deployment
configuration and normalizes it to `/openai/v1`. The configured deployment is sent
as the model; authentication uses `api-key`. Legacy completions paths and
`api-version` query strings are not used.

## Opt-in pattern lab

The normal UI, `/api/nutrition-plan`, and its validated `WeeklyPlan` contract are
unchanged. Add **`patterns` plus exactly one provider profile** to expose
authenticated `GET /api/patterns` and the three POST routes below. With no
`patterns` profile, authenticated requests to these routes return **404** and the
demo beans are absent. The catalog does not call a model.

| Example / route | Actual native mechanism and code anchor | Trade-off and bound |
| --- | --- | --- |
| Baseline: `/api/nutrition-plan` | [`NutritionPlannerAgent`](src/main/java/com/example/nutritionplanner/NutritionPlannerAgent.java): `@Agent`, typed `@Action` dependencies, audit/revision `@State`, final `@AchievesGoal` | Deterministic GOAP composition; one initial candidate plus at most three revisions, with every candidate audited |
| `typed-routing`: `/api/patterns/typed-routing` | [`TypedRoutingExample.route`](src/main/java/com/example/nutritionplanner/TypedRoutingExample.java): `LlmDecisionServiceFactory`, `Ai.classifications().using(...)`, `ClassificationSpec`, then `Ai.decisions().using(...).ask(...)` with a typed `DecisionSpec` | Newly released **experimental 1.5.4 APIs**. Selects a culinary assistance route and assesses a preparation-time preference; does not execute a downstream worker. One model call for no-match/inconclusive, two for a selection |
| `supervisor`: `/api/patterns/supervisor` | [`SupervisorExample.run` / `CulinaryWorkers`](src/main/java/com/example/nutritionplanner/SupervisorExample.java): `SupervisorInvocation`, request-local `AgentScopeBuilder`, native actions-as-tools and typed blackboard | Pre-existing planner, newly demonstrated. LLM chooses worker order; two specialized AI worker actions plus a non-LLM composer. At most eight model sends, three tool calls, two calls in a tool batch, and one execution of each worker/composer |
| `hybrid`: `/api/patterns/hybrid` | [`HybridExample.run` / `KitchenResearch`](src/main/java/com/example/nutritionplanner/HybridExample.java): `PlannerType.HYBRID`, action `value - cost`, `NIRVANA` plus a real `@AchievesGoal` | Pre-existing planner, newly demonstrated. Gathers two curated local note sets, then synthesizes one suggestion and stops. Three actions normally; four-action ceiling and two-model-send ceiling including binding retries |

These are **culinary demonstrations, not validated nutrition plans**. They do
not return `WeeklyPlan`, claim dietary safety, calculate confidence scores, or
simulate human approval. The Hybrid notes are a small in-memory catalog, not web
research, retrieval-augmented generation, or clinical evidence.

### Run and call

For example, use a separate port from the baseline application:

```sh
mvn -B -ntp -f embabel/pom.xml package
java -jar embabel/target/nutrition-planner-embabel-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=ollama,patterns --server.port=9083
```

`openai,patterns` and `azure,patterns` use the same credentials and models listed
above; there is no TypeSafe, Cloudflare, or additional paid-service key. The
decision factory receives the **existing selected `SpringAiLlmService`**, including
its native provider-specific options converter. It never silently selects another
model. A tool-capable Ollama model must already be installed.

```sh
BASE_URL=http://localhost:9083
curl --fail-with-body --user alice:123456 "$BASE_URL/api/patterns"

for pattern in typed-routing supervisor hybrid; do
  curl --fail-with-body --user alice:123456 \
    --header 'Content-Type: application/json' \
    --data '{"prompt":"Use lentils and carrots in a 20-minute dinner."}' \
    "$BASE_URL/api/patterns/$pattern"
done
```

Every POST accepts exactly the same declared input shape:
`{"prompt":"..."}`. `prompt` must be nonblank and at most **2,000 characters**;
validation runs before invoking a model. There is no client-controlled username,
provider, model, action budget, or conversation identifier.

The catalog is an array of
`{id, name, mechanism, path, maxModelCalls}` objects. Every successful POST has
the envelope `{pattern, requestedBy, result, disclaimer}`. `requestedBy` is taken
from the authenticated principal, never from a model response or request field.

| Route | Fields inside `result` |
| --- | --- |
| `typed-routing` | `classification`: native `{status, categoryId?, provenance}`; `preferences`: native `{answers:[{name, kind, outcome}]}` or null; `route`: `"pantry"`, `"technique"`, or null; `nextStep`: explanation |
| `supervisor` | `advice.pantry.ideas`: two strings; `advice.preparation.steps`: two strings; `actions`: actual worker/composer invocation names; `modelCalls`: observed model sends |
| `hybrid` | `advice.suggestion.suggestion`: one string; `advice.pantry.notes` and `advice.technique.notes`: the actual curated notes; `actions`: native process action history; `modelCalls`: observed model sends |

For routing, `selected`, `no_match`, and `inconclusive` remain different native
outcomes. No-match and inconclusive return HTTP 200 **with no selected route and
no second model call**, rather than a default category. A preparation preference
can itself remain inconclusive. Prompted services return model provenance, not
numeric confidence. A native `Failure` becomes an explicit HTTP 502 problem,
never a successful route.

Other error responses are HTTP **400** for invalid input, **401** for missing
authentication, **502** for provider failures, and **422** for exhausted budgets
or an unreached terminal goal. Malformed generated objects can consume the bounded
binding retries and finish as 422 exhaustion. Errors never contain partial advice.

### Native execution versus application safeguards

The Supervisor workers are **typed actions**, not independently deployed nested
agents. Each request creates its own `CulinaryWorkers`; the LLM can only call its
three advertised tools. The composer accepts no model-supplied intermediate
objects: it uses the two actual worker results and rejects premature composition.
Worker order is model-selected, not hardwired by a Java sequence.

Hybrid intentionally gathers useful evidence that is not a declared prerequisite
of synthesis: the native net values select `pantryEvidence` (0.8), then
`techniqueEvidence` (0.7), then `synthesize` (0.3). The real terminal goal stops
the process without executing a negative-value appendix. `UtilityInvocation`
is used only to assemble the scoped agent plus `NIRVANA`; its `run()` method
would force `UTILITY`, so this example starts the agent explicitly with
`ProcessOptions.withPlannerType(HYBRID)`. It is not a demo of a long-running
pure Utility event loop.

[`PatternBudget`](src/main/java/com/example/nutritionplanner/PatternBudget.java)
and [`PatternConfiguration`](src/main/java/com/example/nutritionplanner/PatternConfiguration.java)
add **application safeguards** at Embabel's native `ToolLoopFactory` boundary.
The native Supervisor wraps its orchestration in one outer action, so
`EarlyTerminationPolicy.maxActions` alone cannot bound its inner calls. A
request-local budget travels out of band in `ProcessOptions.toolCallContext`;
the decorator caps model sends across supervisor and worker loops, limits each
inner loop to four iterations, and rejects excess fan-out before dispatch.
Budget and provider failures are sticky: framework retries cannot make further
model sends. Normal baseline calls carry no pattern budget and use the original
factory behavior, even when `patterns` is active.

`application-patterns.yaml` sets native decision retries to one attempt. Call
counts here mean **framework model sends**: provider SDK transport retries and
provider timeouts are separate, so these are not hard billing, token, or elapsed-time
guarantees. Structured-output validation checks DTO shape, not nutritional truth.

Both planner examples use **ephemeral processes** and request-local scopes; they
are not globally deployed, cannot become eligible for the baseline `WeeklyPlan`
goal, and are not exported as MCP tools. Nothing here provides durable execution,
cross-request chat memory, resumable sessions, or a persistent approval workflow.

### Capability notes, not additional implemented patterns

| Capability | Embabel 1.5.4 support | What this sample actually demonstrates |
| --- | --- | --- |
| GOAP, Utility, Hybrid, Supervisor | Native planner choices | GOAP baseline, Supervisor and Hybrid endpoints; pure Utility is **not demonstrated**, not unsupported |
| Typed classification / decision specs | Experimental service selectors, choice/proposition/rating specs, explicit uncertainty/failure | Classification and proposition assessment through the selected chat model; ratings and specialized decision providers are **not demonstrated** |
| Tools and agent composition | Native typed actions-as-tools, progressive tool disclosure, MCP exports | Supervisor action workers; baseline nutrition unfolding and authenticated MCP goal export; no independently spawned subagents |
| Guardrails | Native `withGuardRails`, input/assistant-message guardrail interfaces | Baseline audit plus deterministic meal checks, DTO validation, and application budgets; **not** an implementation of native guardrail policy interfaces |
| Human interaction | Native waiting/continuation lifecycle | **Not demonstrated**: no human approval endpoint, simulated consent, or UI pause |
| State / memory / persistence | Typed blackboard; snapshot-store SPI and optional JDBC/cache persistence at waiting/terminal boundaries | Per-request state only. No snapshot store or durable resume; an in-memory demo is **not** a durable workflow engine |

## Planned workflow and revision budget

`NutritionPlannerAgent` is a real GOAP-planned `@Agent`, not a Java loop:

1. Independent typed actions acquire the authenticated user's profile and seasonal ingredients.
2. A drafting action combines those results with the entire request and the Recipe Curator persona.
3. `NutritionAudit` and `ReviseWeeklyPlan` are `@State` records; their actions transition through the planner.
4. Only `Done.createNutritionPlan`, annotated with `@AchievesGoal`, returns the successful plan.

The initial candidate plus **at most three revisions** are audited, including the
last revision: **at most four audits**. A passing audit exits immediately. A final
failed audit raises `NutritionPlanRejectedException`, which implements Embabel's
`NonRetryable`; the framework must not retry this domain decision as a fifth audit.
The REST response is HTTP **422 ProblemDetail**, and the UI displays an escaped
error fragment. Model/configuration/parsing errors are not converted into plans.
Provider-level transient retries are distinct from candidate revisions.

The typed `PlanningContext` carries the full days/meals request, instructions,
seasonal ingredients, and profile through every state transition, including when
the looping action clears the blackboard. Deterministic checks reject omitted or
extra requested meals/days and missing recipe nutrition even if the model claims
the audit passed. Semantic dietary judgments and nutrition estimates still come
from the model; this is a framework sample, not a clinical nutrition service.

`DailyPlan` stores nullable meal recipes with explicit JSON property names and
retains its Optional-based Java accessors/constructor. This compatibility adapter
was introduced for Embabel 1.5.2's Optional JSON Schema type-array normalization.
It is retained and exercised through the real 1.5.4 provider loops; this upgrade
does not assume that native Optional schema generation has been fixed. Models may
omit unrequested meal fields; responses still serialize the same
`breakfast`/`lunch`/`dinner` fields with nulls for absent meals.

`process-type: CONCURRENT`, virtual threads, and the shared Spring task executor
allow the profile and seasonal actions to overlap. A latch-based integration test
verifies actual parallel execution on distinct virtual threads. Drafting, auditing,
and revising remain dependency-ordered; there is no claim that those stages run in parallel.

## Tools, skills, personas, and MCP

`WeeklyPlan` retains `@LlmTool` nutrition calculations and the category-based
`@UnfoldingTools` facade: `nutrition` exposes daily totals and totals for one day,
and `meal` exposes the meal count. This is progressive tool disclosure, **not
semantic tool search**. The two personas guide drafting and auditing independently.
No human approval step is implemented; Embabel's generic HITL support is not a
demonstrated step in this graph.

The current-month skill uses `Skills.asIndividualReferences()` and the released
`ProcessSkillScriptExecutionEngine.confinedTo(...)` Java API. Only the bundled
`SKILL.md` and shell script are copied from resource streams to a temporary
directory, so both exploded classpaths and packaged JARs work. Files are removed
at shutdown. The engine runs the trusted bundled script, not model-generated code.
It is not a strong sandbox and inherits the process environment; do not substitute
untrusted skills. The directory loader can emit a legacy
"script execution is not yet supported" warning, although the configured native
engine executes the script; the packaged-resource test verifies this.
Embabel 1.5.4 exposes reference-prefixed tool names. Prompts and provider fixtures
use the advertised activation/script catalog instead of assuming an unprefixed
`current_month` tool; tests also check that the catalog has no duplicate names.

The native `@AchievesGoal` export publishes **`createNutritionPlan`** at **`/mcp`**
using **Streamable HTTP / SYNC**. Its input is the existing `WeeklyPlanRequest`,
without a client-controlled username. Supply the same authenticated HTTP identity
on every MCP transport request. Native MCP success is a textual plan; exhaustion
is an MCP tool error, not a successful plan or an HTTP REST problem.

`McpIdentityConfiguration` extracts the trusted HTTP principal into the MCP
transport context and decorates Embabel's discovered exports at that boundary.
`PlannerIdentity` binds it in a Java 25 `ScopedValue`; the task decorator explicitly
propagates that scope to Embabel's shared executor. REST uses the same scope.
No global/inheritable identity, default user, or assumption about Spring Security's
thread-local context crossing executor/transport boundaries is used.

## Observability and offline verification

Exporters are disabled unless the `observability` profile is selected.
Boot 4 tracing uses `management.opentelemetry.tracing.export.otlp.endpoint` plus
`management.tracing.export.otlp.enabled`; OTLP metrics use
`management.otlp.metrics.export.*`. Defaults target localhost port 4318 and can be
overridden with `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` and
`OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`.

Embabel properties now live under `embabel.agent.platform.observability`.
Request bodies and prompt/response telemetry content capture are disabled. This
does not redact every upstream diagnostic log; avoid sensitive input in this demo.
Native
`embabel.agent.*`, `embabel.llm.*`, `embabel.tool.*`, and
`embabel.tool_loop.iterations` metrics accompany Spring AI model observations.
Only health, info, and metrics actuator endpoints are exposed.

Tests cover real GOAP registration, all budget boundaries, prompt/context/tool
wiring, overlapping actions, REST/auth/UI failures, two authenticated MCP users,
MCP exhaustion, packaged-classpath skills, isolated provider startup, Azure SDK
wire format, and actual local OTLP exports. All model responses are fake or served
by an in-process HTTP fixture; no live model service, real key, or Docker is used.
The provider regression additionally runs the production GOAP graph through real
Embabel `Ai`/tool-loop and Spring AI clients against that fixture for all three
profiles, including skill activation/script execution, progressive nutrition tool
disclosure, structured JSON binding, and custom model names in native options and
every HTTP request.
The integration profile is named `offline`, because Embabel deliberately disables
automatic agent registration when the literal `test` profile is active.

The module now has **71 offline tests**. The baseline real-provider regression
runs with `patterns` both off and on for OpenAI, Azure, and Ollama. Additional
tests drive the new native decision services, Supervisor and Hybrid planners
through those same real adapters, including selected/no-match/inconclusive/failure,
unknown categories, malformed verdicts and objects, missing worker fields, repeated
workers, premature composition, exact no-progress call exhaustion, fan-out
rejection before dispatch, and Hybrid termination before its unused appendix.
Authenticated concurrent REST requests verify state, identity, and budget isolation
for all three examples. Controller tests cover profile absence, auth, input bounds,
typed JSON outcomes, and explicit error responses.

```sh
# Full module, including the executable JAR
mvn -B -ntp -f embabel/pom.xml package

# Only the native pattern and provider-adapter regressions
mvn -B -ntp -f embabel/pom.xml \
  '-Dtest=PatternExamples*Tests,ModelProviderLoopTests' test
```

## Upstream references

- [Embabel 1.5.4 release](https://github.com/embabel/embabel-agent/releases/tag/v1.5.4)
- [LLM decision services: factory, outcomes, retries, observations (experimental)](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/llm-decisions/page.adoc)
- [Decision service selectors and execution (experimental)](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/decision-execution/page.adoc)
- [Typed decision specs and JSON contracts (experimental)](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/decision-specs/page.adoc)
- [Native GOAP, Utility, Hybrid and Supervisor planners](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/planners/page.adoc)
- [Supervisor's native inner orchestration loop](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-api/src/main/kotlin/com/embabel/agent/api/annotation/support/SupervisorAgentFactory.kt)
- [State planning and loops](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/states/page.adoc)
- [Concurrent agent processes and out-of-band tool context](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/agent-process/page.adoc)
- [ProcessOptions, including ephemeral lifecycle restrictions](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-api/src/main/kotlin/com/embabel/agent/core/ProcessOptions.kt)
- [Native guardrail interfaces](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/guardrails/page.adoc)
- [Persistence SPI, waiting/terminal checkpoints, and limitations](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/persistence/page.adoc)
- [Skills and individual references](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-skills/src/main/kotlin/com/embabel/agent/skills/Skills.kt)
- [Native Java-compatible script engine](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-skills/src/main/kotlin/com/embabel/agent/skills/script/ProcessSkillScriptExecutionEngine.kt)
- [Non-retryable domain failures](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-api/src/main/kotlin/com/embabel/agent/core/Retry.kt)
- [MCP integration](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-docs/src/main/asciidoc/reference/integrations/page.adoc)
- [Spring AI 2.0.2 OpenAI/Azure setup](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/setup/OpenAiSetup.java)
- [Embabel native Ollama options converter](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-autoconfigure/models/embabel-agent-ollama-autoconfigure/src/main/kotlin/com/embabel/agent/config/models/ollama/OllamaModelsConfig.kt)
- [Embabel native OpenAI options converters](https://github.com/embabel/embabel-agent/blob/v1.5.4/embabel-agent-openai/src/main/kotlin/com/embabel/agent/openai/converters.kt)
- [Original 1.5.2 Optional schema workaround context](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-common/embabel-agent-ai/src/main/kotlin/com/embabel/common/ai/converters/jsonSchemaSupport.kt)
