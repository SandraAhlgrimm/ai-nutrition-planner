# Copilot Instructions — AI Nutrition Planner

## Project Overview

Agentic Spring implementations comparing three AI frameworks: **LangChain4j**, **Spring AI**, and **Embabel**. Each framework solves the same nutrition-planning domain to evaluate agentic patterns on the JVM.

## Tech Stack

- **Java 25** — prefer standard language features, records, and scoped values; preview features require explicit compiler and runtime configuration.
- **Spring Boot**: LangChain4j and Embabel use **4.1.2** / Spring Framework **7.0.10** (GA); the Spring AI sample uses **4.2.0-RC1** / Framework **7.1.0-RC1** (approved release candidates for the native Spring AI 2.1 examples).
- **Build**: Maven — each module independently inherits its own `spring-boot-starter-parent`. Use system Maven at the root; wrappers are also available inside each module. Verify `mvn -version` uses Java 25; set `JAVA_HOME` and prepend its `bin` to `PATH` when needed.
- **AI Frameworks**:
  - LangChain4j BOM/core **1.23.0**; agentic, `langchain4j-agentic-patterns`, Jackson 3 integration, Micrometer integration and dedicated Boot 4 starters **1.23.0-beta33**. Agentic and decision APIs remain experimental. Preserve the Jackson BOM overrides **3.2.4 / 2.22.4**.
  - LangChain4j's HTTP MCP server uses **`io.modelcontextprotocol.sdk:mcp:2.1.0`**, not a LangChain4j-native HTTP server or a sibling sample.
  - Spring AI **2.1.0-RC1** model/MCP starters, `spring-ai-tool-search-advisor`, and **`spring-ai-session-advisor`**. Community **`org.springaicommunity:spring-ai-agent-utils:0.13.0`** provides skills and browser questions; it is not Spring AI core orchestration. Existing lexical tool search uses **Lucene 9.12.3**.
  - Embabel **1.5.4** MCP/observability starters, released skills, test support and native OpenAI/Ollama option converters. Model providers use Spring AI **2.0.2** starters bridged through `SpringAiLlmService`. Its new decision APIs are experimental; no extra specialized decision-provider dependency is needed for the LLM-backed lab.

Do not force version parity across modules. The root is an aggregator, not their parent. Do not copy Spring AI's RC dependencies into Embabel or LangChain4j without an independently verified compatibility need.

## Build & Test Commands

```bash
mvn clean install                              # full build
mvn test                                       # run all tests
mvn test -pl <module-name>                     # tests for one module
mvn test -Dtest=MyTestClass                    # single test class
mvn test -Dtest=MyTestClass#myMethod           # single test method
mvn spring-boot:run -pl <module-name>          # run a specific module
```

## Architecture

The root POM aggregates three independently runnable Maven modules. Each framework implements the same domain with its own DTOs and has no runtime dependency on another sample. Keep the successful REST request/response contract comparable without introducing a shared framework abstraction.

```
ai-nutrition-planner/
├── langchain4j/             # LangChain4j agentic implementation
├── spring-ai/               # Spring AI implementation
├── embabel/                 # Embabel implementation
├── infra/                   # Bicep IaC for Azure (ACA + OpenAI)
├── grafana/                 # Grafana dashboard + provisioning
├── docker-compose.yaml      # LGTM observability stack
├── azure.yaml               # azd project manifest
└── pom.xml                  # reactor aggregator, not the modules' parent
```

## Key Conventions

### Spring Boot 4 Specifics

- Use **virtual threads** as the default execution model.
- Use **JSpecify null-safety annotations** where required by public APIs and framework contracts.
- Use **declarative HTTP service clients** (`@HttpExchange`) instead of `RestTemplate` or `WebClient` for external API calls.
- Use **Jakarta APIs** (`jakarta.*`), not legacy `javax.*` equivalents.
- Account for **Jackson 3** and Boot 4's modular starters and test dependencies when migrating configuration, serialization, or controller tests.

### Java 25 Specifics

- Prefer **records** for DTOs, domain value objects, and AI model responses.
- Use **sealed interfaces** to model domain hierarchies (meal types, nutrient categories).
- Use **pattern matching with `switch`** where it improves clarity; do not introduce primitive-pattern preview features without configuring and testing preview support.
- Use **scoped values** (`ScopedValue`) over `ThreadLocal` for request-scoped context.
- Use **flexible constructor bodies** — validate inputs before `super()`/`this()` calls.

### AI Framework Patterns

- **LangChain4j**: define agents with `@Agent`-annotated interfaces. Compose with native `AgenticServices.sequenceBuilder()`, `parallelBuilder()`, and `loopBuilder()`. Use the dedicated `*-spring-boot4-starter` artifacts and configure models under `langchain4j.*`.
- **Spring AI**: use the autoconfigured `ChatClient.Builder` fluent API and native tool-calling/search advisors. Configure under `spring.ai.*`. Application-managed concurrency, routing, worker composition and retry policy are not a native multi-agent orchestration framework. In 2.1, prefer native `SessionAdvisors`/session APIs over deprecated `ChatMemory`. Preserve provider-specific option builders with `mutate()`; do not replace them with generic options that fail inside real provider clients.
- **Embabel**: define typed actions and goals using `@Agent`, `@Action`, and `@AchievesGoal`, with `@State` for the audit/revision lifecycle. Let GOAP compose the baseline action chains. Utility, Hybrid and Supervisor are distinct native planner choices, not synonyms for GOAP. Preserve the native provider-specific converters when using `SpringAiLlmService`.
- **Shared retry contract**: generate an initial plan and allow at most three revisions. Audit every candidate, including the final revision. Each requested day must independently total 90%-110% of its calorie target, inclusive; enforce this deterministically in addition to the model audit. Stop on success; expose exhaustion as an explicit error (HTTP 422 for REST), never as a successful invalid or unaudited plan.

### Opt-in Pattern Labs

- Require the **`patterns`** profile alongside exactly one provider. Protect `GET /api/patterns` and explicit POST routes with the existing authentication. With the profile off, authenticated lab requests return 404. Startup/catalog reads must not call providers or download models.
- Preserve `/api/nutrition-plan`, browser/HITL, and MCP as the audited baseline. Lab outputs are illustrative culinary results, not `WeeklyPlan`, allergy certification, medical advice, or fabricated approval/confidence.
- LangChain4j demonstrates native `DecisionRouterPlanner`, `ConfidenceCascadePlanner`, and parallel mapper/sequence composition. The native cascade leaves its final tier ungraded, so preserve the application final-tier judgment. Decision-based labs require explicit `PATTERNS_DECISION_MODEL_NAME` and a compatible OpenAI/Ollama decision endpoint; Azure is mapper-only. Never substitute chat-generated probabilities for an unavailable `DecisionModel`.
- Spring AI demonstrates application typed routing/bounded workers and native 2.1 session memory/turn-aware compaction/tool confirmation. Session ownership, capacity/turn/message bounds, expiry, failure state and approval replay protection remain application responsibilities. Approve/deny only the immutable server-stored native tool call; no client-supplied replacement arguments or automatic approval.
- Embabel's typed-routing example uses native experimental classification/decision contracts and does not execute a specialist. Preserve explicit selected/no-match/inconclusive/failure outcomes. Supervisor/Hybrid use request-local scopes, not globally registered agents or MCP exports. Bound Supervisor's inner work at the native tool-loop boundary; outer `maxActions` alone is insufficient. Baseline calls without a pattern budget must retain their original behavior.
- Bound tasks, model sends and retained state; propagate failure/cancellation rather than returning partial success. Call counts are not transport-attempt, token, billing, or medical guarantees. Do not add filesystem/shell/purchase/cloud-side-effect tools to the labs.
- Keep comparison tables tied to code and pinned upstream tags. Distinguish native, community and application composition; newly released versus newly demonstrated; available-but-not-demonstrated versus unsupported. Lexical Lucene/category unfolding is not semantic vector search.

### Testing

- Use **Boot-managed JUnit Jupiter 6**. Spring Framework 7's `SpringExtension` requires Jupiter 6 or newer; do not force the old JUnit 5 version or downgrade `spring-test`.
- Use **`@SpringBootTest`** for integration tests, **`@WebMvcTest`** for controller slices.
- Use **`@MockitoBean`** (from `org.springframework.test.context.bean.override.mockito`) instead of `@MockBean`.
- Mock AI model responses in unit tests — never call live APIs in CI.
- On framework/provider upgrades, also drive real SDK/HTTP adapters, native advisors/tool loops and provider-specific options against isolated loopback/in-memory fixtures. A mocked top-level service or model cannot detect native option casts or wire-format regressions.
- Each module should have tests validating its agentic workflow independently.
- Cover first-pass success, revision success, success on the final permitted revision, and exhaustion with exact generation/audit counts.
- Embabel workflow integration tests use the `offline` profile; the framework disables automatic agent registration under the literal `test` profile.
- Cover pattern selection, actual model/action counts, caps, failure/cancellation, owner/request isolation, profile-off 404s and catalog authentication. Spring session tests must retain complete tool turns through compaction; confirmation tests must prove no pre-approval execution, denial, ownership, expiry and replay protection.

### Configuration

- Externalize all API keys and model endpoints via environment variables or Spring config profiles.
- Never hardcode API keys — use `${ENV_VAR}` placeholders in `application.yml`.
- Select exactly one provider profile: `openai`, `azure`, or `ollama`. Add `observability` independently when exporting telemetry and `patterns` only when intentionally enabling the lab.
- Preserve the environment variable names documented in `.env.example`; Spring Boot does not automatically import that file.
- Skills and other bundled resources must work from a packaged JAR, not only from an exploded source-tree classpath.
