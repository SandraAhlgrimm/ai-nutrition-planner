# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across three frameworks to compare their programming models, abstractions, and features.

[Slides: Comparing Agentic AI Frameworks for Java](slides.pdf)

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel
2. Asks the user for meal preferences to tailor the plan (human-in-the-loop)
3. Generates recipes for the requested days and meals using seasonal produce
4. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
5. Revises recipes based on feedback and re-validates — looping until the plan passes or a maximum number of iterations is reached

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
| **Tool Search** — dynamic tool discovery at runtime | ✅ `@UnfoldingTools` — a facade tool progressively discloses its inner tools on invocation | ✅ `@ToolSearchStrategySupplier` (`SimpleToolSearchStrategy`) — only the tool-search tool is sent to the LLM, which discovers matching tools by keywords | ✅ `tool-search-advisor` — a separate search step discovers tools via metadata before each call |
| **Human-in-the-Loop** — pause workflow for user input | ✅ `WaitFor.formSubmission()` | ✅ `@HumanInTheLoop` + `SuspendedResponse` | ✅ `AskUserQuestionTool` |
| **Agent Skills** — invoke pre-packaged executable skills | ✅ `Skills` with script execution engine | ✅ `Skills.from()` via `CustomSkillLoader` | ✅ `SkillsTool` |
| **Multi-Agent Orchestration** — specialized sub-agents collaborate | ✅ goal-driven action graph | ✅ `@SequenceAgent` with sub-agents | ✅ custom orchestration in code — no direct framework support |
| **Persona** — role-based system prompts per agent | ✅ first-class `Persona` object | ✅ custom implementation via `@SystemMessage` | ✅ custom implementation via `.system()` on `ChatClient` |
| **MCP Server** — expose agent as a Model Context Protocol tool | ✅ `@Export(remote = true)` | ❌ | ✅ `@McpTool` |

## Prerequisites

- **Java 25**
- **Maven** (wrapper included in each module)
- An **OpenAI API key**
- **Docker Desktop** (optional, for the Grafana observability stack)
- An **Azure subscription** and the **Azure Developer CLI** (`azd`) (optional, to [deploy to Azure](#deploy-to-azure))

## Setup

### Codespaces (recommended)

Open this repo in a GitHub Codespace. The dev container installs Java 25, Maven, and Docker automatically.
Add your OpenAI API key as a Codespaces secret named `OPENAI_API_KEY` (or export it in the terminal).

### LLM Providers

**OpenAI** (default `openai` profile):

```bash
export OPENAI_API_KEY=sk-...
```

**Microsoft Foundry / Azure OpenAI** (`azure` profile):

```bash
export SPRING_PROFILES_ACTIVE=azure
export AZURE_OPENAI_BASEURL=https://your-resource.openai.azure.com/openai/v1
export AZURE_OPENAI_API_KEY=your-api-key
```

The model name configured in each module's `application-azure.yaml` must match the name of a deployment in your Foundry resource, see [Publishing the Foundry models](#publishing-the-foundry-models). To run against the resources that `azd up` created, see [Run locally against the Foundry deployments](#run-locally-against-the-foundry-deployments).

Alternatively copy [`.env.example`](.env.example) to `.env`, fill in the values and `source` it.

## Run

Change into the implementation directory and start the app:

```bash
cd langchain4j   # or embabel, spring-ai
./mvnw spring-boot:run
```

The app starts on port `8080` with the `openai` profile active unless `SPRING_PROFILES_ACTIVE` says otherwise. Log in as `alice` / `123456`, pick the days and meals for your plan, and answer the follow-up questions the agent asks (human-in-the-loop) at [http://localhost:8080](http://localhost:8080).

For Azure demos, run `azd env set DEMO_PASSWORD 123456` before provisioning to use the same login on all three apps. The deployment hook preserves this setting across redeployments; when unset, it generates a random password instead. The setting is stored in the gitignored `.azure/` directory. To change it on an existing deployment, run `azd provision` after updating the setting, then restart each active Container App revision so it picks up the updated secret. Azure apps are publicly accessible: use a strong password outside a disposable demo.

### MCP Server

The Spring AI and Embabel implementations additionally expose the agent as an MCP tool (`createNutritionPlan`) via Streamable HTTP at `http://localhost:8080/mcp`, protected by HTTP Basic auth (`alice` / `123456`). When called through MCP, the agent skips the human-in-the-loop step.

## Deploy to Azure

The three apps are deployed as Azure Container Apps by Azure Developer CLI (`azd`). The Bicep templates in `infra/` provision a Microsoft Foundry account and project, the model deployments, Azure Container Registry, Container Apps environment, Log Analytics workspace, and the apps. Container images are built remotely in ACR, so a local Docker installation is not required.

### Running demo

These are the currently deployed demo instances:

| App | Link |
|-----|------|
| LangChain4j | [Open LangChain4j](https://ca-langchain4j-yz5iwm5st63uq.nicewave-e6798498.swedencentral.azurecontainerapps.io/) |
| Spring AI | [Open Spring AI](https://ca-spring-ai-yz5iwm5st63uq.nicewave-e6798498.swedencentral.azurecontainerapps.io/) |
| Embabel | [Open Embabel](https://ca-embabel-yz5iwm5st63uq.nicewave-e6798498.swedencentral.azurecontainerapps.io/) |

Sign in as `alice` / `123456`. These apps have public ingress and the credentials are intentionally weak for this disposable demo only; do not use this password for a production deployment. The apps scale to zero, so the first request after idle may take 30–40 seconds.

### Prerequisites

- An Azure subscription with quota for both models in the selected region.
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) (`az`) and [Azure Developer CLI](https://learn.microsoft.com/azure/developer/azure-developer-cli/install-azd) (`azd`).
- These resource providers registered on the subscription: `Microsoft.App`, `Microsoft.ContainerRegistry`, `Microsoft.CognitiveServices`, `Microsoft.OperationalInsights`, and `Microsoft.ManagedIdentity`. Register any that are missing:

  ```bash
  az provider register --namespace Microsoft.App --wait
  az provider register --namespace Microsoft.ContainerRegistry --wait
  az provider register --namespace Microsoft.CognitiveServices --wait
  az provider register --namespace Microsoft.OperationalInsights --wait
  az provider register --namespace Microsoft.ManagedIdentity --wait
  az provider show --namespace Microsoft.CognitiveServices --query registrationState -o tsv
  ```

### Deploy the apps

Authenticate and start the deployment:

```bash
az login
azd auth login
azd up
```

`azd up` prompts for an environment name, subscription, and Azure region. To choose them before deployment:

```bash
azd env new <environment-name> --location <region> --subscription <subscription-id>
azd up
```

Choose a region that offers both models and has enough quota; see [Publishing the Foundry models](#publishing-the-foundry-models) for commands to check availability and quota. The `azd` environment stores deployment settings and generated secrets in `.azure/`, which is gitignored. The deployment hook generates a random 24-character demo password if `DEMO_PASSWORD` is unset. To explicitly use the local demo credentials, set the password before provisioning:

```bash
azd env set DEMO_PASSWORD 123456
azd up
```

The password is used by all three apps and is retained for later `azd up` and `azd provision` runs in this environment. Because the apps have public ingress, use a strong password outside a disposable demo.

After deployment, `azd show` prints the app URLs. To redeploy one app after changing its code, run `azd deploy langchain4j`, `azd deploy spring-ai`, or `azd deploy embabel`. Run `azd provision` to apply infrastructure or environment setting changes without rebuilding images.

### Configuration and operations

The model deployments default to capacity 100 (thousands of tokens per minute). Increase it only if the subscription has sufficient quota; concurrent plan generation can otherwise receive HTTP 429 rate-limit responses. A plan uses substantial model capacity, so reduce concurrent requests or request more quota if needed:

```bash
azd env set MODEL_CAPACITY 150
azd provision
```

The apps default to scale-to-zero. To keep one replica warm and avoid cold starts:

```bash
azd env set MIN_REPLICAS 1
azd provision
```

Each app is limited to one replica because pending server-sent events and human-in-the-loop state are held in memory. An app restart or scale-to-zero can discard an in-progress plan. View container logs with:

```bash
az containerapp list --resource-group "$(azd env get-value AZURE_RESOURCE_GROUP)" --output table
az containerapp logs show --resource-group <resource-group> --name <container-app-name> --tail 100 --type console
```

The apps use the official OpenAI Java SDKs against Microsoft Foundry's OpenAI-compatible `/openai/v1` endpoint and authenticate with the Foundry account key, supplied to each app as a Container Apps secret. No Azure-specific OpenAI or identity SDK is used; managed-identity/Entra authentication to the model endpoint is not implemented. Ingress is public HTTPS with application login, and only the `health` and `info` actuator endpoints are exposed.

The Foundry deployments incur token-based model charges. The Container Apps scale to zero by default; the Container Registry and Log Analytics workspace may have costs while idle. Review Azure pricing for your region and usage before deploying.

To remove the deployment, use `azd down --purge`. This deletes the deployed resources and permanently purges soft-deleted resources so the Foundry account name can be reused. This cleanup command has not been exercised as part of this deployment; verify the target `azd` environment before running it.

### Publishing the Foundry models

The deployment provisions the models declaratively in [`infra/modules/foundry.bicep`](infra/modules/foundry.bicep). The deployment name must match the model name configured in each module:

| App | Deployment/model | Model version |
|-----|------------------|---------------|
| LangChain4j | `gpt-6-sol` | `2026-09-22` |
| Spring AI | `gpt-6-sol` | `2026-09-22` |
| Embabel | `gpt-5.6-sol` | `2026-07-09` |

Check model availability and regional quota with Azure CLI:

```bash
az cognitiveservices model list --location swedencentral \
  --query "[?model.name=='gpt-6-sol' || model.name=='gpt-5.6-sol'].{model:model.name,version:model.version,skus:join(',',model.skus[].name)}" \
  --output table
az cognitiveservices usage list --location swedencentral \
  --query "[?contains(name.value,'gpt-6-sol') || contains(name.value,'gpt-5.6-sol')].{name:name.value,used:currentValue,limit:limit}" \
  --output table
```

`azd up` publishes both models automatically. To create a model deployment manually in an existing Foundry account, substitute your account name, resource group, and chosen capacity:

```bash
az cognitiveservices account deployment create \
  --resource-group <resource-group> --name <foundry-account> \
  --deployment-name gpt-6-sol \
  --model-name gpt-6-sol --model-version 2026-09-22 --model-format OpenAI \
  --sku-name GlobalStandard --sku-capacity 100

az cognitiveservices account deployment create \
  --resource-group <resource-group> --name <foundry-account> \
  --deployment-name gpt-5.6-sol \
  --model-name gpt-5.6-sol --model-version 2026-07-09 --model-format OpenAI \
  --sku-name GlobalStandard --sku-capacity 100
```

List deployments with `az cognitiveservices account deployment list --resource-group <resource-group> --name <foundry-account> --output table`. Deployments can also be created in the [Microsoft Foundry portal](https://ai.azure.com): select the Foundry resource, open **Discover → Models**, select a model, choose **Deploy** with **Global Standard**, and set the deployment name to the model name. See Microsoft's [model deployment guide](https://learn.microsoft.com/azure/foundry/foundry-models/how-to/deploy-foundry-models). The account needs permission to create Cognitive Services deployments.

The apps use the account's OpenAI-compatible base URL, `https://<foundry-account>.openai.azure.com/openai/v1`. The LangChain4j and Spring AI apps use Chat Completions with function tools, so their Azure profiles set `reasoning-effort: none`. Embabel's bundled model configuration routes `gpt-5.6-sol` through the Responses API. These models do not support custom temperature values.

### Run locally against the Foundry deployments

After deploying the Azure resources, retrieve the endpoint and account key from the current `azd` environment, then run a module locally:

```bash
export SPRING_PROFILES_ACTIVE=azure
export AZURE_OPENAI_BASEURL="$(azd env get-value AZURE_OPENAI_BASEURL)"
export AZURE_OPENAI_API_KEY="$(az cognitiveservices account keys list \
  --resource-group "$(azd env get-value AZURE_RESOURCE_GROUP)" \
  --name "$(azd env get-value AZURE_FOUNDRY_NAME)" \
  --query key1 --output tsv)"

cd spring-ai   # or langchain4j, embabel
./mvnw spring-boot:run
```

## Tests

Each module can be tested on its own; AI model responses are mocked, no API key is required:

```bash
cd spring-ai     # or embabel
./mvnw test
```

## Observability

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose:

```bash
docker compose up -d
```

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

To export traces and metrics from any module, activate the `observability` profile in addition to the LLM provider profile:

```bash
SPRING_PROFILES_ACTIVE=openai,observability ./mvnw spring-boot:run
```

The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.
