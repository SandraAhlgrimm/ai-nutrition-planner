// Returns the image currently deployed to a container app so re-provisioning
// does not replace an image pushed by `azd deploy` with the placeholder image.
param exists bool
param name string

resource existingApp 'Microsoft.App/containerApps@2025-01-01' existing = if (exists) {
  name: name
}

output image string = exists ? existingApp!.properties.template.containers[0].image : ''
