<#
.SYNOPSIS
    Tears down all Azure resources created for the Nutrition Planner.

.EXAMPLE
    .\teardown.ps1
    .\teardown.ps1 -ResourceGroup rg-nutrition-planner
#>

param(
    [string]$ResourceGroup = "rg-nutrition-planner"
)

$ErrorActionPreference = "Stop"

Write-Host "==> Deleting resource group '$ResourceGroup'..." -ForegroundColor Yellow
az group delete --name $ResourceGroup --yes --no-wait
Write-Host "✅ Deletion initiated (runs in background). Resources will be removed shortly." -ForegroundColor Green
