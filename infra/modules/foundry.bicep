type modelDeployment = {
  @description('Deployment name. Apps use it as the model name, so it must match application-azure.yaml.')
  name: string

  @description('Name of the model in the Foundry model catalog')
  modelName: string

  @description('Model version')
  version: string
}

param name string
param location string
param tags object = {}

@description('Name of the Foundry project created in the account')
param projectName string

@description('Capacity of each deployment, in thousands of tokens per minute')
@minValue(1)
param modelCapacity int = 100

@description('Models to publish. Each deployment name must equal the model name configured in the apps.')
param deployments modelDeployment[] = [
  { name: 'gpt-6-sol', modelName: 'gpt-6-sol', version: '2026-09-22' }
  { name: 'gpt-5.6-sol', modelName: 'gpt-5.6-sol', version: '2026-07-09' }
]

resource account 'Microsoft.CognitiveServices/accounts@2025-06-01' = {
  name: name
  location: location
  tags: tags
  kind: 'AIServices'
  sku: { name: 'S0' }
  identity: { type: 'SystemAssigned' }
  properties: {
    customSubDomainName: name
    allowProjectManagement: true
    publicNetworkAccess: 'Enabled'
    // The apps authenticate with the account key
    disableLocalAuth: false
  }
}

resource project 'Microsoft.CognitiveServices/accounts/projects@2025-06-01' = {
  parent: account
  name: projectName
  location: location
  tags: tags
  identity: { type: 'SystemAssigned' }
  properties: {
    displayName: 'AI Nutrition Planner'
    description: 'Models used by the LangChain4j, Spring AI and Embabel nutrition planners'
  }
}

// The account accepts one write at a time, so create the deployments sequentially
@batchSize(1)
resource modelDeployments 'Microsoft.CognitiveServices/accounts/deployments@2025-06-01' = [
  for deployment in deployments: {
    parent: account
    name: deployment.name
    sku: {
      name: 'GlobalStandard'
      capacity: modelCapacity
    }
    properties: {
      model: {
        format: 'OpenAI'
        name: deployment.modelName
        version: deployment.version
      }
      versionUpgradeOption: 'NoAutoUpgrade'
    }
    dependsOn: [project]
  }
]

output id string = account.id
output name string = account.name
output projectName string = project.name
output openAiBaseUrl string = 'https://${account.name}.openai.azure.com/openai/v1'
