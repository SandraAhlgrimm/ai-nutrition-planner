param name string
param location string
param tags object = {}
param modelVersion string = '2024-11-20'
param tpmCapacity int = 10

resource openAi 'Microsoft.CognitiveServices/accounts@2024-10-01' = {
  name: name
  location: location
  tags: tags
  kind: 'OpenAI'
  sku: { name: 'S0' }
  properties: {
    customSubDomainName: name
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
output name string = openAi.name
output deploymentName string = gpt4oDeployment.name
#disable-next-line outputs-should-not-contain-secrets
output apiKey string = openAi.listKeys().key1
