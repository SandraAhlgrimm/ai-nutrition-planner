param name string
param location string
param tags object = {}

@description('Principal ID of the identity that pulls images (granted AcrPull)')
param pullPrincipalId string

var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource containerRegistry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: name
  location: location
  tags: tags
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false
  }
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(containerRegistry.id, pullPrincipalId, acrPullRoleId)
  scope: containerRegistry
  properties: {
    principalId: pullPrincipalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
  }
}

output id string = containerRegistry.id
output name string = containerRegistry.name
output loginServer string = containerRegistry.properties.loginServer
