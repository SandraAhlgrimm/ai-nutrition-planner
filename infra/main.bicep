targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the azd environment')
param environmentName string

@description('Azure region for resources')
param location string

@description('GPT-4o model version')
param modelVersion string = '2024-11-20'

@description('Tokens-per-minute capacity (in thousands)')
param tpmCapacity int = 10

var abbrs = {
  containerAppsEnvironment: 'cae-'
  containerApp: 'ca-'
  containerRegistry: 'cr'
  logAnalyticsWorkspace: 'log-'
  openAi: 'aoai-'
}

var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = { 'azd-env-name': environmentName }

// ── Resource Group ──────────────────────────────────────────
resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: 'rg-${environmentName}'
  location: location
  tags: tags
}

// ── Log Analytics ───────────────────────────────────────────
module logAnalytics 'modules/log-analytics.bicep' = {
  name: 'log-analytics'
  scope: rg
  params: {
    name: '${abbrs.logAnalyticsWorkspace}${resourceToken}'
    location: location
    tags: tags
  }
}

// ── Container Registry ──────────────────────────────────────
module containerRegistry 'modules/container-registry.bicep' = {
  name: 'container-registry'
  scope: rg
  params: {
    name: '${abbrs.containerRegistry}${resourceToken}'
    location: location
    tags: tags
  }
}

// ── Container Apps Environment ──────────────────────────────
module containerAppsEnvironment 'modules/container-apps-environment.bicep' = {
  name: 'container-apps-environment'
  scope: rg
  params: {
    name: '${abbrs.containerAppsEnvironment}${resourceToken}'
    location: location
    tags: tags
    logAnalyticsWorkspaceId: logAnalytics.outputs.id
  }
}

// ── Azure OpenAI ────────────────────────────────────────────
module openAi 'modules/openai.bicep' = {
  name: 'openai'
  scope: rg
  params: {
    name: '${abbrs.openAi}${resourceToken}'
    location: location
    tags: tags
    modelVersion: modelVersion
    tpmCapacity: tpmCapacity
  }
}

// ── LangChain4j Container App ───────────────────────────────
module langchain4jApp 'modules/container-app.bicep' = {
  name: 'langchain4j-app'
  scope: rg
  params: {
    name: '${abbrs.containerApp}langchain4j-${resourceToken}'
    location: location
    tags: union(tags, { 'azd-service-name': 'langchain4j' })
    containerAppsEnvironmentId: containerAppsEnvironment.outputs.id
    containerRegistryName: containerRegistry.outputs.name
    containerName: 'langchain4j-nutrition-planner'
    springProfiles: 'cloud,azure'
    openAiEndpoint: openAi.outputs.endpoint
    openAiApiKey: openAi.outputs.apiKey
    openAiDeploymentName: openAi.outputs.deploymentName
  }
}

// ── Spring AI Container App ─────────────────────────────────
module springAiApp 'modules/container-app.bicep' = {
  name: 'spring-ai-app'
  scope: rg
  params: {
    name: '${abbrs.containerApp}spring-ai-${resourceToken}'
    location: location
    tags: union(tags, { 'azd-service-name': 'spring-ai' })
    containerAppsEnvironmentId: containerAppsEnvironment.outputs.id
    containerRegistryName: containerRegistry.outputs.name
    containerName: 'spring-ai-nutrition-planner'
    springProfiles: 'cloud,azure'
    openAiEndpoint: openAi.outputs.endpoint
    openAiApiKey: openAi.outputs.apiKey
    openAiDeploymentName: openAi.outputs.deploymentName
  }
}

// ── Embabel Container App ───────────────────────────────────
module embabelApp 'modules/container-app.bicep' = {
  name: 'embabel-app'
  scope: rg
  params: {
    name: '${abbrs.containerApp}embabel-${resourceToken}'
    location: location
    tags: union(tags, { 'azd-service-name': 'embabel' })
    containerAppsEnvironmentId: containerAppsEnvironment.outputs.id
    containerRegistryName: containerRegistry.outputs.name
    containerName: 'embabel-nutrition-planner'
    springProfiles: 'cloud,azure'
    openAiEndpoint: openAi.outputs.endpoint
    openAiApiKey: openAi.outputs.apiKey
    openAiDeploymentName: openAi.outputs.deploymentName
  }
}

// ── Outputs for azd ─────────────────────────────────────────
output AZURE_CONTAINER_REGISTRY_ENDPOINT string = containerRegistry.outputs.loginServer
output AZURE_CONTAINER_APPS_ENVIRONMENT_ID string = containerAppsEnvironment.outputs.id
output AZURE_RESOURCE_GROUP string = rg.name
output AZURE_OPENAI_ENDPOINT string = openAi.outputs.endpoint
output AZURE_OPENAI_DEPLOYMENT_NAME string = openAi.outputs.deploymentName
output SERVICE_LANGCHAIN4J_ENDPOINT string = langchain4jApp.outputs.fqdn
output SERVICE_SPRING_AI_ENDPOINT string = springAiApp.outputs.fqdn
output SERVICE_EMBABEL_ENDPOINT string = embabelApp.outputs.fqdn
