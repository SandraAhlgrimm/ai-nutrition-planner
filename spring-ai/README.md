# Spring AI nutrition planner

Independent Java 25 / Maven sample using Spring Boot **4.1.1**, Spring AI **2.0.1**,
and community **spring-ai-agent-utils 0.12.0**. Boot manages Spring Framework
7.0.9, Jackson 3.1.5, and JUnit Jupiter 6.0.3. Spring AI manages MCP Java SDK 2.0.0.
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

This is not a native Spring AI multi-agent planner, GOAP workflow, or durable
workflow engine. MCP elicitation and sampling are not used. Browser interaction
is not exposed as an MCP tool.

The first candidate and every revision are audited. A passing candidate returns
immediately. At most **three revisions** follow the initial plan, so exhaustion
means **four failed audits**, an explicit `NutritionPlanValidationException`, and
no returned plan. Original requested days, meals, country, profile, instructions,
seasonal ingredients, and collected browser answers survive revisions. A
deterministic shape check also rejects missing/extra meals and duplicate days.
Every audit also receives the recorded questions and answers, so it can apply
the user's cooking preferences without treating an already completed question as
an unmet instruction. Exhaustion displays the final audit feedback in the browser.
Malformed model output, missing audits/answers, and tool failures propagate as
errors rather than empty successful responses.

Nutrition estimates and dietary compliance still depend on model quality;
this is a framework demonstration, not independently verified medical advice.

## Run and test

From the repository root, with Maven and JDK 25 installed:

```bash
mvn -B -ntp -f spring-ai/pom.xml test
mvn -B -ntp -f spring-ai/pom.xml package
SPRING_PROFILES_ACTIVE=ollama mvn -f spring-ai/pom.xml spring-boot:run
```

Tests use deterministic fake chat responses, real native advisor/tool execution,
and local HTTP MCP requests. They do not call external model APIs, Docker,
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

## Browser, REST and MCP

The browser at `/` submits the existing `meals[MONDAY]=LUNCH` form to `/plan`.
`GET /interactions/{id}/events` starts generation; only its authenticated owner
may connect or answer via `POST /interaction/{id}/answers`. Single-choice,
multi-select and free-text answers are supported. Answer submission returns 204
so it cannot overwrite a concurrently delivered plan. Errors render an alert;
a terminal `done` event closes the EventSource. Completion, disconnect, timeout
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

- [Spring AI 2.0.1 compatibility and features](https://github.com/spring-projects/spring-ai/blob/v2.0.1/README.md)
- [Pinned Spring AI 2.x migration notes](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)
- [Native tool execution](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java)
- [Native tool search](https://github.com/spring-projects/spring-ai/blob/v2.0.1/advisors/spring-ai-tool-search-advisor/src/main/java/org/springframework/ai/chat/client/advisor/toolsearch/ToolSearchToolCallingAdvisor.java)
- [Current OpenAI/Azure provider properties](https://github.com/spring-projects/spring-ai/blob/v2.0.1/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/AbstractOpenAiProperties.java)
- [Community 0.12.0 JAR skill loader](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.12.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/utils/Skills.java)
- [Community human-question tool](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/v0.12.0/spring-ai-agent-utils/src/main/java/org/springaicommunity/agent/tools/AskUserQuestionTool.java)
- [Boot 4.1.1 OTLP tracing properties](https://github.com/spring-projects/spring-boot/blob/v4.1.1/module/spring-boot-micrometer-tracing-opentelemetry/src/main/java/org/springframework/boot/micrometer/tracing/opentelemetry/autoconfigure/otlp/OtlpTracingProperties.java)
