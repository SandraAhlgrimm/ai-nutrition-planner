#!/usr/bin/env bash
#
# Deploys Azure OpenAI resources for the AI Nutrition Planner
# and writes a .env file with the connection details.
# Shared across all framework implementations (LangChain4j, Spring AI, Embabel).
#
# Usage:
#   ./deploy.sh                          # uses defaults
#   ./deploy.sh -l eastus -n myplanner   # custom location & name
#
# Prerequisites: Azure CLI (az) logged in with an active subscription.

set -euo pipefail

LOCATION="swedencentral"
BASE_NAME="nutrition-planner"
RESOURCE_GROUP="rg-nutrition-planner"

while getopts "l:n:g:" opt; do
  case $opt in
    l) LOCATION="$OPTARG" ;;
    n) BASE_NAME="$OPTARG" ;;
    g) RESOURCE_GROUP="$OPTARG" ;;
    *) echo "Usage: $0 [-l location] [-n baseName] [-g resourceGroup]"; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "==> Creating resource group '$RESOURCE_GROUP' in '$LOCATION'..."
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" -o none

echo "==> Deploying Azure OpenAI resources (Bicep)..."
az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --template-file "$SCRIPT_DIR/main.bicep" \
  --parameters baseName="$BASE_NAME" location="$LOCATION" \
  -o none

ENDPOINT=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name main \
  --query "properties.outputs.endpoint.value" -o tsv)

OPENAI_NAME=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name main \
  --query "properties.outputs.openAiName.value" -o tsv)

DEPLOYMENT_NAME=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name main \
  --query "properties.outputs.deploymentName.value" -o tsv)

echo "==> Retrieving API key..."
API_KEY=$(az cognitiveservices account keys list \
  --name "$OPENAI_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --query "key1" -o tsv)

ENV_FILE="$PROJECT_DIR/.env"
cat > "$ENV_FILE" <<EOF
AZURE_OPENAI_ENDPOINT=$ENDPOINT
AZURE_OPENAI_API_KEY=$API_KEY
AZURE_OPENAI_DEPLOYMENT_NAME=$DEPLOYMENT_NAME
EOF

echo ""
echo "✅ Deployment complete!"
echo ""
echo "   Resource Group:  $RESOURCE_GROUP"
echo "   OpenAI Account:  $OPENAI_NAME"
echo "   Endpoint:        $ENDPOINT"
echo "   Deployment:      $DEPLOYMENT_NAME"
echo ""
echo "   .env written to: $ENV_FILE"
echo ""
echo "   To run the app:"
echo "     set -a; source $ENV_FILE; set +a"
echo "     cd $PROJECT_DIR && ./mvnw spring-boot:run"
