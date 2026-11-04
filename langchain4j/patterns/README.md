# Optional LangChain4j pattern lab

Three small, runnable examples using published native LangChain4j orchestration.
They are **off by default**: activate `patterns` together with exactly one provider
(`openai`, `ollama`, or `azure`). The existing authentication protects the catalog
and every invocation. Without `patterns`, authenticated requests to these routes
return 404.

These endpoints return **advisory cooking ideas, not audited nutrition plans or
medical advice**. They do not load a dietary profile, certify allergy safety, or
return `WeeklyPlan`. The ordinary `/api/nutrition-plan`, browser UI, and `/mcp`
continue to use the unchanged audited planner and its three-revision policy.
Nothing automatically invokes a lab workflow from those entry points.

## What is native, and what this application adds

| Endpoint ID | Native orchestration | Application support | Maximum logical model calls |
|---|---|---|---|
| `decision-routing` | `DecisionRouterPlanner` asks one batched decision request and activates every specialist with probability at least `0.8`. Its threshold mode supports multiple routes, not just one winner. | Three bounded culinary specialists (`mediterranean`, `eastAsian`, `baking`); explicit no-match and inconclusive errors; bounded text responses. | 1 decision + 3 chat calls |
| `confidence-cascade` | `ConfidenceCascadePlanner` escalates through `quick`, `practical`, and `detailed` agents, stopping when its decision-model grade reaches `0.8`. | An explicit final-tier acceptance check, because the native planner returns its last tier **without grading it**. Full rejection returns 422, never a successful rejected answer. | 3 chat + 3 decision calls |
| `parallel-mapper` | `parallelMapperBuilder` fans the same typed agent out over the item list; `sequenceBuilder` waits for the mapped results before synthesis. | At most four meal briefs; a request-owned virtual-thread executor; input-index association in the response. | Number of items + 1 chat calls; at most 5 |

Every generative agent uses the **same selected existing `ChatModel`**. Cascade
tiers differ in their prompts, not in model price or demonstrated capability.
There is **no claim of cost savings or measured quality improvement**. A decision
probability is a model judgment, not objective correctness, calibrated confidence,
medical validation, or a security boundary. The illustrative threshold is not
validated for every decision model; changing models requires evaluation.

Native builders manage orchestration and fresh ephemeral `AgenticScope`s. Request
identities are generated internally; no username, memory ID, or scope ID is accepted
as an orchestration input. Mapper workers receive only the common prompt and their
own item, use distinct native result slots, and have no chat memory. They share the
request's scope, not each other's input arguments or result slots. Results retain
input order even when workers finish out of order.

`PatternRun` supplies application-level call budgets, interruption/deadline handling,
and response bounds. It does not replace the planners with Java routing or loops.
There are no filesystem, shell, purchase, or external-action tools. The only network
calls are to the explicitly selected model provider.

## Versions and stability

| Component | Pin | Stability / change |
|---|---|---|
| Java / Spring Boot | Java 25 / Boot 4.1.2 | No preview language features |
| LangChain4j BOM, core, OpenAI and Ollama integrations | `1.23.0` | Released artifacts; `DecisionModel` and its integrations are experimental APIs |
| Agentic framework and Boot 4 starters | `1.23.0-beta33` | Beta; the agentic framework is explicitly experimental |
| **Added dependency**: `dev.langchain4j:langchain4j-agentic-patterns` | `1.23.0-beta33`, managed by the existing BOM | Native decision-router and confidence-cascade planners; no separate paid-service integration added |

No release candidate is needed for these examples. The
[pinned release notes][release] identify the confidence cascade, decision agents,
Ollama decision integration, and agent tool-search support as additions in 1.23.
The [decision router][older-router] and [parallel mapper][older-mapper] already
existed in 1.22: they are **new demonstrations here, not newly invented 1.23 APIs**.
The 1.23 release also fixes mapper item/result isolation.

## Provider capabilities

Ordinary chat models do **not** implement `DecisionModel`. The catalog's
`available` flag describes configuration readiness, **not a live provider health or
model-availability check**. Reading the catalog never calls a model.

| Selected profile | Parallel mapper | Decision routing and cascade | Configuration / prerequisites |
|---|---|---|---|
| `ollama,patterns` | Uses the existing `OLLAMA_BASE_URL` and `OLLAMA_MODEL_NAME` chat configuration | Native `OllamaDecisionModel`, `POST /v1/systemone` | Explicitly set `PATTERNS_DECISION_MODEL_NAME`, for example `nimble`, after installing that decision model yourself. Requires Ollama **0.35.0+**, according to the [pinned integration guide][ollama-decisions]. An older server or a missing model fails explicitly; no model is downloaded automatically. |
| `openai,patterns` | Uses the existing OpenAI chat configuration and key | Native `OpenAiDecisionModel`, `POST /v1/decisions` | Explicitly set `PATTERNS_DECISION_MODEL_NAME` to a decision-capable model available to your account, for example `gpt-6-luna`. Reuses `OPENAI_API_KEY` and the selected chat base URL, organization and project settings. An OpenAI-compatible chat endpoint is not necessarily Decisions-compatible. See the [pinned integration guide][openai-decisions]. |
| `azure,patterns` | Uses the existing Azure chat deployment | **Not configured in this lab**; catalog reports unavailable, invocation returns 503 | No matching native Azure DecisionModel is wired here. The lab does not redirect an Azure request or key to OpenAI, nor substitute a chat-model probability. |

`PATTERNS_DECISION_MODEL_NAME` has **no default**. It binds to
`nutrition-planner.patterns.decision-model-name` only when `patterns` is active.
Leaving it unset keeps the mapper usable while the two decision-based demos report
their missing prerequisite. No additional paid-service key is required.

## Run

Use Java 25 and the existing provider environment variables described in the
[module README](../README.md). Spring Boot does not automatically import `.env`.
Use an unused application port; the examples below use `8091`.

From the repository root, start the mapper-only lab with an already configured
Ollama chat model:

```bash
SPRING_PROFILES_ACTIVE=ollama,patterns \
mvn -pl langchain4j spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8091
```

To enable the decision examples as well, first satisfy the provider prerequisites
above, then explicitly select the decision model:

```bash
SPRING_PROFILES_ACTIVE=ollama,patterns \
PATTERNS_DECISION_MODEL_NAME=nimble \
mvn -pl langchain4j spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8091
```

Or, with the existing `OPENAI_API_KEY` and chat model configuration:

```bash
SPRING_PROFILES_ACTIVE=openai,patterns \
PATTERNS_DECISION_MODEL_NAME=gpt-6-luna \
mvn -pl langchain4j spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8091
```

For Azure, use `SPRING_PROFILES_ACTIVE=azure,patterns` without a decision-model
selection. `observability` may be added independently. The profile also works in a
packaged JAR:

```bash
mvn -pl langchain4j package
java -jar langchain4j/target/nutrition-planner-langchain4j-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=ollama,patterns --server.port=8091
```

The following `curl --user alice` examples prompt for the configured sample login
password; replace the username if needed. No identity belongs in the JSON body.

```bash
curl --fail-with-body --user alice \
  -H 'Accept: application/json' \
  http://localhost:8091/api/patterns

curl --fail-with-body --user alice \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Suggest a Mediterranean dinner using chickpeas and tomatoes."}' \
  http://localhost:8091/api/patterns/decision-routing

curl --fail-with-body --user alice \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Explain how to roast root vegetables evenly with one oven tray."}' \
  http://localhost:8091/api/patterns/confidence-cascade

curl --fail-with-body --user alice \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Vegetarian weeknight cooking, no nuts.","items":["A warming lentil soup","A quick bean salad","A vegetable tray bake"]}' \
  http://localhost:8091/api/patterns/parallel-mapper
```

## Input and output contracts

All routes are synchronous. `prompt` must be nonblank and at most **1,000 UTF-16
code units**. The mapper additionally requires **1-4** nonblank string `items`, each
at most **160 code units**. Bounds are checked before any model call. Whitespace at
the edges is stripped. Duplicate briefs are allowed and remain separate indexed
items.

| Route | Request | Successful response fields |
|---|---|---|
| `GET /api/patterns` | No body | `framework`, `coreVersion`, `agenticVersion`, `provider`, `chatModel`, nullable `decisionModel`, `patterns[]` with ID, description, endpoint, native API, availability, requirement and separate chat/decision call budgets |
| `POST /api/patterns/decision-routing` | `{"prompt":"..."}` | `advisoryOnly`, `notice`, `threshold`, `probabilities` keyed by specialist, `advice` keyed only by activated specialists, `calls` |
| `POST /api/patterns/confidence-cascade` | `{"prompt":"..."}` | `advisoryOnly`, `notice`, accepted `tier`, `advice`, `threshold`, `assessments[]` (`tier`, `confidence`, `accepted`, `applicationFinalCheck`), `calls` |
| `POST /api/patterns/parallel-mapper` | `{"prompt":"...","items":["..."]}` | `advisoryOnly`, `notice`, `items[]` (`index`, original normalized `item`, generated `idea`), `synthesis`, `calls` |

`calls` has the shape `{"chatModel":3,"decisionModel":3}` and counts **logical
model invocations**, not tokens, dollars, or transport attempts. Decision clients
have SDK retries disabled. Chat clients retain the selected provider's existing
transport-retry configuration; this lab does not mutate baseline provider settings.

Each chat call requests at most **700 output tokens** and the application rejects
blank, incomplete/filtered, unexpected tool-call, or over-**4,000-character** responses.
Each invocation has a **two-minute deadline**. Cancellation interrupts active model
threads and prevents further calls; a remote provider may still finish an already
received request. No partial mapper result is returned if a worker fails, and
synthesis does not run after such a failure.

Errors use `application/problem+json`, a `code`, and an explicit HTTP status:

| Status | Code | Meaning |
|---|---|---|
| 400 | `invalid_pattern_request` | Missing, malformed, blank or oversized input |
| 422 | `no_matching_specialist` | All routing probabilities are below the threshold; includes probabilities and call counts |
| 422 | `decision_inconclusive` | A required decision was refused; it is not treated as approval or an ordinary negative answer |
| 422 | `cascade_exhausted` | All three candidates were judged and rejected; includes assessments/counts but not the rejected advice |
| 502 | `pattern_execution_failed`, `invalid_pattern_output`, `pattern_budget_exceeded` | Provider/native-workflow failure, unusable output, or unexpected extra model call; no success fallback |
| 503 | `decision_model_unavailable`, `pattern_cancelled` | Missing/unsupported configured capability, or interrupted invocation |
| 504 | `pattern_timeout` | Deadline exceeded |

## Capability comparison: demonstrated versus available

The broader framework catalog is intentionally not implemented in full. **Not
demonstrated does not mean unsupported.** These are LangChain4j capabilities at
the pinned version, not claims about other frameworks.

| Pattern / capability | Native support at the pin | Demonstrated here |
|---|---|---|
| Sequence, parallel, conditional and loop workflows | [Typed `AgenticServices` builders][agents] | Existing audited planner; sequence also used in this lab |
| Parallel mapper | [Native mapper builder][mapper] | Runnable `parallel-mapper` |
| Decision-based routing | [Native `DecisionRouterPlanner`][router] | Runnable threshold-based culinary routing |
| Confidence cascade | [Native `ConfidenceCascadePlanner`][cascade] | Runnable cascade plus the explicitly application-owned final-tier check |
| Typed decision agents | [New `decisionAgentBuilder` / `@Decide` support][decision-agents] | Not demonstrated; routing here uses the native planner instead |
| Supervisor | [Native supervisor builder][supervisor] | Not demonstrated |
| Goal-oriented, peer-to-peer, blackboard | [Published custom planner patterns][custom-patterns] | Not demonstrated |
| Voting, debate, BDI | [Published `VotingPlanner`, `DebatePlanner`, `BDIPlanner`][custom-patterns] | Not demonstrated |
| Activation conditions | [Native agent/workflow activation hooks][activation] | Not demonstrated |
| Agentic scope, memory, recovery | [Native context and persistence APIs][memory] | Fresh ephemeral scopes only; no conversational persistence or recovery endpoint |
| Human-in-the-loop | [Native human-agent suspension and resume][hitl] | Not demonstrated; no fake approval |
| Agent tool search | [Native `toolSearchStrategy` support][tool-search] | Not demonstrated; no tools are attached to lab agents |
| Skills | [Native skill loading/execution APIs][skills] | Not demonstrated |
| A2A | [Native remote-agent integration][a2a] | Not demonstrated; no external agents configured |
| MCP **client / tool agents** | [Native MCP-based tool agents][mcp-agents] | Not demonstrated; not the same as hosting an MCP server |
| MCP **server** | Existing sample uses MCP Java SDK `2.1.0` to expose the audited planner | Existing `/mcp`; lab endpoints are REST only and do not become MCP tools |

## Code and offline checks

| Anchor | Responsibility |
|---|---|
| [`PatternAgents`](../src/main/java/com/example/nutritionplanner/patterns/PatternAgents.java) | Typed `@Agent` interfaces, culinary roles, worker inputs and synthesis |
| [`PatternLab`](../src/main/java/com/example/nutritionplanner/patterns/PatternLab.java) | Real native builders, catalog, no-match handling and final cascade check |
| [`PatternConfiguration`](../src/main/java/com/example/nutritionplanner/patterns/PatternConfiguration.java) | Opt-in profile and provider-specific DecisionModel wiring |
| [`PatternRun`](../src/main/java/com/example/nutritionplanner/patterns/PatternRun.java) | Application-owned budgets, bounds, deadline and cancellation |
| [`PatternController`](../src/main/java/com/example/nutritionplanner/patterns/PatternController.java) / [`PatternModels`](../src/main/java/com/example/nutritionplanner/patterns/PatternModels.java) | REST routes and typed contracts |
| [`patterns` tests](../src/test/java/com/example/nutritionplanner/patterns) | Native orchestration, HTTP fixtures, authentication, profile-off behavior and failures |

```bash
mvn -pl langchain4j -Dtest='Pattern*Test' test
mvn -pl langchain4j test
```

Tests use scripted `ChatModel`/`DecisionModel` responses while running the **real
native builders and planners**. Provider fixtures run on isolated random loopback
ports for the actual OpenAI/Ollama clients; Azure uses its real SDK with an
in-memory HTTPS transport. No live LLM, private `.env`, shared Ollama instance, or
Docker is required. Coverage includes threshold/no-match/refusal behavior, every
cascade stopping point and final rejection with exact counts, parallel overlap and
item/request isolation, cancellation, provider failures, authentication and
profile-off 404s. These fixtures verify integration mechanics, not real-model
quality or a particular account's decision-model entitlement.

[release]: https://github.com/langchain4j/langchain4j/releases/tag/1.23.0
[agents]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md
[mapper]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#parallel-mapper-workflow
[router]: https://github.com/langchain4j/langchain4j/blob/1.23.0/langchain4j-agentic-patterns/src/main/java/dev/langchain4j/agentic/patterns/decisionrouter/DecisionRouterPlanner.java
[cascade]: https://github.com/langchain4j/langchain4j/blob/1.23.0/langchain4j-agentic-patterns/src/main/java/dev/langchain4j/agentic/patterns/cascade/ConfidenceCascadePlanner.java
[older-router]: https://github.com/langchain4j/langchain4j/blob/1.22.0/langchain4j-agentic-patterns/src/main/java/dev/langchain4j/agentic/patterns/decisionrouter/DecisionRouterPlanner.java
[older-mapper]: https://github.com/langchain4j/langchain4j/blob/1.22.0/langchain4j-agentic/src/main/java/dev/langchain4j/agentic/workflow/ParallelMapperService.java
[ollama-decisions]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/integrations/decision-models/ollama.md
[openai-decisions]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/integrations/decision-models/open-ai.md
[decision-agents]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#decision-agents
[supervisor]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#pure-agentic-ai
[custom-patterns]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#custom-agentic-patterns
[activation]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#activation-conditions
[memory]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#memory-and-context-engineering
[hitl]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#human-in-the-loop
[tool-search]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#tool-search
[skills]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/skills.md
[a2a]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#a2a-integration
[mcp-agents]: https://github.com/langchain4j/langchain4j/blob/1.23.0/docs/docs/tutorials/agents.md#mcp-based-tool-agents
