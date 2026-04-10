#!/usr/bin/env bash
#
# Tears down all Azure resources created for the Nutrition Planner.
#
# Usage: ./teardown.sh [-g resourceGroup]

set -euo pipefail

RESOURCE_GROUP="rg-nutrition-planner"

while getopts "g:" opt; do
  case $opt in
    g) RESOURCE_GROUP="$OPTARG" ;;
    *) echo "Usage: $0 [-g resourceGroup]"; exit 1 ;;
  esac
done

echo "==> Deleting resource group '$RESOURCE_GROUP'..."
az group delete --name "$RESOURCE_GROUP" --yes --no-wait
echo "✅ Deletion initiated (runs in background). Resources will be removed shortly."
