# Embabel nutrition planner

An independent Java 25 application using Embabel **1.5.2**, its released skills module
**1.5.2**, Spring Boot **4.1.1**, Spring AI **2.0.1**, and Jackson **3.1.5**.
Tests use Boot-managed JUnit Jupiter **6.0.3** and Embabel's fake/Mockito helpers.
Boot 4 / Spring 7 require the newer Jupiter runtime.

## Run

Use Java 25, Maven, and Bash (for the bundled current-month skill).

```sh
mvn -B -ntp -f embabel/pom.xml test
mvn -B -ntp -f embabel/pom.xml package
SPRING_PROFILES_ACTIVE=ollama mvn -f embabel/pom.xml spring-boot:run
```

The UI is at `http://localhost:8080`; the demo login remains `alice` / `123456`.
The JSON endpoint remains authenticated `POST /api/nutrition-plan`.
These credentials and the permissive demo CSRF policy are not production authentication.

Choose exactly one model profile; add `observability` separately, for example
`SPRING_PROFILES_ACTIVE=azure,observability`. Without an explicit profile, `openai` is the default.

| Profile | Required configuration | Model default |
| --- | --- | --- |
| `openai` | `OPENAI_API_KEY`; optional `OPENAI_MODEL_NAME` | `gpt-4o` |
| `azure` | `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`; optional `AZURE_OPENAI_DEPLOYMENT_NAME` | `gpt-4o` deployment |
| `ollama` | Optional `OLLAMA_BASE_URL`, `OLLAMA_MODEL_NAME` | `http://localhost:11434`, `qwen2.5` |

Application configuration selects one Spring AI `ChatModel` using
`spring.ai.model.chat` and wraps it with Embabel's maintained `SpringAiLlmService`.
All planning and prompting above that boundary use Embabel APIs. Inactive providers
need no credentials. Ollama does not discover or download models at startup; pull a
tool-capable model yourself before making planning requests.

Azure uses Spring AI's OpenAI SDK integration with `microsoft-foundry: true`.
`ModelConfiguration` accepts the resource-root endpoint supplied by deployment
configuration and normalizes it to `/openai/v1`. The configured deployment is sent
as the model; authentication uses `api-key`. Legacy completions paths and
`api-version` query strings are not used.

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
untrusted skills. Embabel 1.5.2's directory loader still emits a legacy
"script execution is not yet supported" warning, although the configured native
engine executes the script; the packaged-resource test verifies this.

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
Request bodies and prompt/response content capture are disabled. Native
`embabel.agent.*`, `embabel.llm.*`, `embabel.tool.*`, and
`embabel.tool_loop.iterations` metrics accompany Spring AI model observations.
Only health, info, and metrics actuator endpoints are exposed.

Tests cover real GOAP registration, all budget boundaries, prompt/context/tool
wiring, overlapping actions, REST/auth/UI failures, two authenticated MCP users,
MCP exhaustion, packaged-classpath skills, isolated provider startup, Azure SDK
wire format, and actual local OTLP exports. All model responses are fake or served
by an in-process HTTP fixture; no live model service, real key, or Docker is used.
The integration profile is named `offline`, because Embabel deliberately disables
automatic agent registration when the literal `test` profile is active.

## Upstream references

- [Embabel 1.5.2 release (Boot 4.1.1)](https://github.com/embabel/embabel-agent/releases/tag/v1.5.2)
- [State planning and loops](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-docs/src/main/asciidoc/reference/states/page.adoc)
- [Concurrent agent processes](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-docs/src/main/asciidoc/reference/agent-process/page.adoc)
- [Skills and individual references](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-skills/src/main/kotlin/com/embabel/agent/skills/Skills.kt)
- [Native Java-compatible script engine](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-skills/src/main/kotlin/com/embabel/agent/skills/script/ProcessSkillScriptExecutionEngine.kt)
- [Non-retryable domain failures](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-api/src/main/kotlin/com/embabel/agent/core/Retry.kt)
- [MCP integration](https://github.com/embabel/embabel-agent/blob/v1.5.2/embabel-agent-docs/src/main/asciidoc/reference/integrations/page.adoc)
- [Spring AI 2.0.1 OpenAI/Azure setup](https://github.com/spring-projects/spring-ai/blob/v2.0.1/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/setup/OpenAiSetup.java)
