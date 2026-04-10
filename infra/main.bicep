@description('Base name for all resources')
param baseName string = 'nutrition-planner'

@description('Azure region for resources')
param location string = resourceGroup().location

@description('GPT-4o model version')
param modelVersion string = '2024-11-20'

@description('Tokens-per-minute capacity (in thousands)')
param tpmCapacity int = 10

var openAiName = 'aoai-${baseName}-${uniqueString(resourceGroup().id)}'

resource openAi 'Microsoft.CognitiveServices/accounts@2024-10-01' = {
  name: openAiName
  location: location
  kind: 'OpenAI'
  sku: {
    name: 'S0'
  }
  properties: {
    customSubDomainName: openAiName
    publicNetworkAccess: 'Enabled'
  }
}

resource gpt4oDeployment 'Microsoft.CognitiveServices/accounts/deployments@2024-10-01' = {
  parent: openAi
  name: 'gpt-4o'
  sku: {
    name: 'Standard'
    capacity: tpmCapacity
  }
  properties: {
    model: {
      format: 'OpenAI'
      name: 'gpt-4o'
      version: modelVersion
    }
  }
}

output endpoint string = openAi.properties.endpoint
output openAiName string = openAi.name
output deploymentName string = gpt4oDeployment.name
