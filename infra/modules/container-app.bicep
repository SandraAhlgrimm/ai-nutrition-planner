param name string
param location string
param tags object = {}

param containerAppsEnvironmentId string

@description('Login server of the registry the app image is pulled from')
param registryLoginServer string

@description('Resource ID of the user-assigned identity that has AcrPull on the registry')
param identityId string

@description('Name of the Foundry account (same resource group) that hosts the model deployments')
param foundryAccountName string

@description('OpenAI-compatible base URL of the Foundry account, ending in /openai/v1')
param openAiBaseUrl string

@secure()
@description('Password of the demo user "alice"')
param demoPassword string

@description('0 scales the app to zero when idle; 1 keeps an instance warm')
@minValue(0)
@maxValue(1)
param minReplicas int = 0

@description('Set by azd once the app exists, so re-provisioning keeps the deployed image')
param exists bool = false

// Listens on 8080 like the apps, so the default ingress probes pass before the first deploy
var placeholderImage = 'mcr.microsoft.com/dotnet/samples:aspnetapp'

resource foundry 'Microsoft.CognitiveServices/accounts@2025-06-01' existing = {
  name: foundryAccountName
}

module currentImage 'fetch-container-image.bicep' = {
  name: '${name}-image'
  params: {
    exists: exists
    name: name
  }
}

resource app 'Microsoft.App/containerApps@2025-01-01' = {
  name: name
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identityId}': {} }
  }
  properties: {
    environmentId: containerAppsEnvironmentId
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
      registries: [
        {
          server: registryLoginServer
          identity: identityId
        }
      ]
      secrets: [
        { name: 'openai-api-key', value: foundry.listKeys().key1 }
        { name: 'demo-password', value: demoPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'main'
          image: exists ? currentImage.outputs.image : placeholderImage
          resources: {
            cpu: json('1.0')
            memory: '2Gi'
          }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
            { name: 'AZURE_OPENAI_BASEURL', value: openAiBaseUrl }
            { name: 'AZURE_OPENAI_API_KEY', secretRef: 'openai-api-key' }
            { name: 'SPRING_SECURITY_USER_PASSWORD', secretRef: 'demo-password' }
            // The ingress terminates TLS; honor X-Forwarded-* so redirects use https
            { name: 'SERVER_FORWARD_HEADERS_STRATEGY', value: 'framework' }
            // /actuator/** is not authenticated, so do not expose heapdump, env and the like
            { name: 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE', value: 'health,info' }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        // Pending SSE streams and human-in-the-loop sessions live in memory, so keep a single replica
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: { metadata: { concurrentRequests: '10' } }
          }
        ]
      }
    }
  }
}

output id string = app.id
output name string = app.name
output uri string = 'https://${app.properties.configuration.ingress.fqdn}'
