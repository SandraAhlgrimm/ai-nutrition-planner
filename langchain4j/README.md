# LangChain4j nutrition planner

This independent sample exposes the same LangChain4j planning workflow through
the browser, authenticated REST, and an authenticated MCP server. It does not
call the Spring AI or Embabel sample.

## Versions

Verified against published artifacts and compiled dependencies on 2026-11-03:

| Component | Version |
| --- | --- |
| Java | 25 |
| Spring Boot / Spring Framework | 4.1.2 / 7.0.10 |
| LangChain4j BOM and core | 1.23.0 |
| Agentic, Boot 4 starters, Jackson 3 integration, Micrometer integration | 1.23.0-beta33 |
| Official MCP Java SDK | 2.1.0 |
| Jackson 3 / Jackson 2 compatibility dependencies | 3.2.4 / 2.22.4 |
| JUnit Jupiter | Boot-managed 6.0.3 |

The Jackson BOM overrides preserve the newer JSON baseline of the LangChain4j
release instead of allowing Boot's dependency management to downgrade it.
Boot 4.2 release candidates and MCP SDK 2.2 milestones are not needed for this
implementation; the sample uses the current supported stable lines.

References: [LangChain4j release](https://github.com/langchain4j/langchain4j/releases/tag/1.23.0),
[Boot 4 starters](https://github.com/langchain4j/langchain4j-spring/releases/tag/1.23.0-beta33),
[MCP SDK release](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.1.0).

## Build and run

Use JDK 25; check `mvn -version` and set `JAVA_HOME` if Maven selects an older JDK.
From the repository root:

```bash
mvn -B -ntp -f langchain4j/pom.xml test
mvn -B -ntp -f langchain4j/pom.xml package
SPRING_PROFILES_ACTIVE=ollama mvn -f langchain4j/pom.xml spring-boot:run
```

Select exactly one provider profile. With no explicit profile, `openai` is used.
The default port is 8080, the UI is `/`, and the demo credentials are
`alice` / `123456`.

| Profile | Environment variables |
| --- | --- |
| `openai` | `OPENAI_API_KEY`; `OPENAI_MODEL_NAME` defaults to `gpt-4o` |
| `azure` | `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`; `AZURE_OPENAI_DEPLOYMENT_NAME` defaults to `gpt-4o` |
| `ollama` | `OLLAMA_BASE_URL` defaults to `http://localhost:11434`; `OLLAMA_MODEL_NAME` defaults to `qwen2.5` |

Add `observability` alongside the provider profile to export OTLP metrics/traces.
The existing agent metric families are preserved. LangChain4j's current native
listener also records `gen_ai.client.token.usage` and
`gen_ai.client.operation.duration`.

## MCP server

The server is enabled by default at **`http://localhost:8080/mcp`**. Set
`NUTRITION_PLANNER_MCP_ENABLED=false` to disable it without affecting REST or UI.

It uses the **official MCP Java SDK's stateless Streamable HTTP servlet**. The
SDK handles JSON-RPC, initialization, tool discovery, schema validation and tool
results. The application supplies the authenticated identity and delegates to
the existing LangChain4j workflow; it does not implement its own MCP protocol.
This is not a LangChain4j-native HTTP server feature: LangChain4j's community
server is stdio-only, and its [server documentation](https://docs.langchain4j.dev/tutorials/mcp-stdio-server/#streamable-http-servers)
points to the Java SDK for HTTP servers.

MCP clients must send HTTP Basic authentication on **every request**. MCP does
not reuse the browser session cookie, allocate an MCP session, or require a
long-lived GET/SSE connection. Browser-origin requests are rejected by the SDK
header validator; cross-origin browser MCP access is not enabled. Use HTTPS
and deployment-appropriate credentials outside local development.

The exported tool is **`createNutritionPlan`**. Its arguments are the REST
request fields directly, not a nested `request` or a model-controlled username:

```json
{
  "days": [
    { "day": "MONDAY", "meals": ["LUNCH", "DINNER"] }
  ],
  "countryCode": "DE",
  "additionalInstructions": "Prefer recipes under 30 minutes."
}
```

`additionalInstructions` is optional. Unknown arguments, invalid days/meals,
duplicate requested days/meals, and invalid country codes are rejected before
planning. The profile always comes from the authenticated principal.

For example, discover and invoke the tool with the stateless endpoint:

```bash
curl --silent --show-error --fail-with-body \
  -u alice:123456 http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

curl --silent --show-error --fail-with-body \
  -u alice:123456 http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc":"2.0","id":2,"method":"tools/call",
    "params":{
      "name":"createNutritionPlan",
      "arguments":{
        "days":[{"day":"MONDAY","meals":["DINNER"]}],
        "countryCode":"DE",
        "additionalInstructions":"Prefer recipes under 30 minutes."
      }
    }
  }'
```

An SDK client can initialize normally, then call `tools/list` and `tools/call`.
Configure its request timeout for model latency and up to four audits; a short
generic MCP-client timeout can expire before planning finishes.

Success returns the unchanged `WeeklyPlan` JSON in both `structuredContent`
and a text content block. Planning/validation failures return
**`isError: true`**, explanatory text, and **no plan**. MCP tool errors can use
HTTP 200 because the failure is in the JSON-RPC tool result; REST continues to
use HTTP 422 ProblemDetail for validation exhaustion.

## Workflow and verification

Native `parallelBuilder()` prepares the profile and seasonal ingredients.
Native sequence/conditional/loop composition generates one initial candidate
and allows at most three revisions, auditing every candidate. A deterministic
shape audit checks requested days/meals before loop exit and final acceptance.
The nutrition tools use the current invocation's hidden `AgenticScope`, not
model-supplied plan data.

For Ollama, draft and revision calls also receive a request-specific JSON schema
through native `ChatRequestParameters`: the selected days are an exact tuple,
selected meals require complete recipe objects, and unrequested meals are null.
The same schema is retained across revisions. It constrains generation rather
than filling in missing recipes after the fact. Tool-enabled audits deliberately
do not receive this schema, because Ollama applies its output grammar to the
entire response and must remain free to emit tool calls. Other providers retain
their existing output handling. All providers still use the deterministic shape
check and model audit; JSON validity alone is never treated as a passing audit.

Draft/revision prompts spell out every selected meal slot, even when instructions
ask for one dish that can be reused across meals. Nutrition values are per
serving, and audit prompts require evidence from the current candidate and
per-day tool totals rather than invented ingredients or comparisons against a
per-meal minimum.

The tests use deterministic chat responses and local/in-memory transport
fixtures. They cover real MCP HTTP discovery and execution, authenticated-user
isolation, all four permitted audits, shape repair/exhaustion, model/parse
failures, provider configuration, REST and UI. No live model, credentials,
Docker or Ollama service is needed. The Ollama empty-output tests remain
reproduction/control coverage, not a claim that model-output quality or the
earlier uncaptured runtime failure was fixed.
