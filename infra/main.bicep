targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the azd environment, used to name the resource group and tag resources')
param environmentName string

@description('Azure region for all resources. It must offer the gpt-6-sol and gpt-5.6-sol models with the GlobalStandard SKU.')
param location string

@description('Capacity of each model deployment, in thousands of tokens per minute')
@minValue(1)
param modelCapacity int = 100

@description('Minimum replicas per app. 0 scales to zero when idle (cold start takes about a minute); 1 keeps an instance warm.')
@minValue(0)
@maxValue(1)
param minReplicas int = 0

@secure()
@minLength(6)
@description('Password of the demo user "alice" on all three apps')
param demoPassword string

// Set by azd (see main.parameters.json) once a service has been deployed
param langchain4jExists bool = false
param springAiExists bool = false
param embabelExists bool = false

var abbrs = {
  containerAppsEnvironment: 'cae-'
  containerApp: 'ca-'
  containerRegistry: 'cr'
  foundry: 'aif-'
  identity: 'id-'
  logAnalyticsWorkspace: 'log-'
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

// ── Identity used by the apps to pull images ────────────────
module identity 'modules/identity.bicep' = {
  name: 'identity'
  scope: rg
  params: {
    name: '${abbrs.identity}${resourceToken}'
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
    pullPrincipalId: identity.outputs.principalId
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
    logAnalyticsWorkspaceName: logAnalytics.outputs.name
  }
}

// ── Microsoft Foundry: account, project and model deployments ─
module foundry 'modules/foundry.bicep' = {
  name: 'foundry'
  scope: rg
  params: {
    name: '${abbrs.foundry}${resourceToken}'
    location: location
    tags: tags
    projectName: 'nutrition-planner'
    modelCapacity: modelCapacity
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
    registryLoginServer: containerRegistry.outputs.loginServer
    identityId: identity.outputs.id
    foundryAccountName: foundry.outputs.name
    openAiBaseUrl: foundry.outputs.openAiBaseUrl
    demoPassword: demoPassword
    minReplicas: minReplicas
    exists: langchain4jExists
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
    registryLoginServer: containerRegistry.outputs.loginServer
    identityId: identity.outputs.id
    foundryAccountName: foundry.outputs.name
    openAiBaseUrl: foundry.outputs.openAiBaseUrl
    demoPassword: demoPassword
    minReplicas: minReplicas
    exists: springAiExists
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
    registryLoginServer: containerRegistry.outputs.loginServer
    identityId: identity.outputs.id
    foundryAccountName: foundry.outputs.name
    openAiBaseUrl: foundry.outputs.openAiBaseUrl
    demoPassword: demoPassword
    minReplicas: minReplicas
    exists: embabelExists
  }
}

// ── Outputs for azd ─────────────────────────────────────────
output AZURE_CONTAINER_REGISTRY_ENDPOINT string = containerRegistry.outputs.loginServer
output AZURE_CONTAINER_APPS_ENVIRONMENT_ID string = containerAppsEnvironment.outputs.id
output AZURE_RESOURCE_GROUP string = rg.name
output AZURE_FOUNDRY_NAME string = foundry.outputs.name
output AZURE_FOUNDRY_PROJECT_NAME string = foundry.outputs.projectName
output AZURE_OPENAI_BASEURL string = foundry.outputs.openAiBaseUrl
output SERVICE_LANGCHAIN4J_ENDPOINT_URL string = langchain4jApp.outputs.uri
output SERVICE_SPRING_AI_ENDPOINT_URL string = springAiApp.outputs.uri
output SERVICE_EMBABEL_ENDPOINT_URL string = embabelApp.outputs.uri
