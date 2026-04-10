<#
.SYNOPSIS
    Deploys Azure OpenAI resources for the AI Nutrition Planner
    and writes a .env file with the connection details.
    Shared across all framework implementations (LangChain4j, Spring AI, Embabel).

.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -Location eastus -BaseName myplanner

.NOTES
    Prerequisite: Azure CLI (az) logged in with an active subscription.
#>

param(
    [string]$Location = "swedencentral",
    [string]$BaseName = "nutrition-planner",
    [string]$ResourceGroup = "rg-nutrition-planner"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "==> Creating resource group '$ResourceGroup' in '$Location'..." -ForegroundColor Cyan
az group create --name $ResourceGroup --location $Location -o none
if ($LASTEXITCODE -ne 0) { throw "Failed to create resource group" }

Write-Host "==> Deploying Azure OpenAI resources (Bicep)..." -ForegroundColor Cyan
$deploymentJson = az deployment group create `
    --resource-group $ResourceGroup `
    --template-file "$ScriptDir\main.bicep" `
    --parameters baseName=$BaseName location=$Location `
    --query "properties.outputs" `
    -o json

if ($LASTEXITCODE -ne 0) { throw "Bicep deployment failed" }

$outputs = $deploymentJson | ConvertFrom-Json
$endpoint = $outputs.endpoint.value
$openAiName = $outputs.openAiName.value
$deploymentName = $outputs.deploymentName.value

Write-Host "==> Retrieving API key..." -ForegroundColor Cyan
$apiKey = az cognitiveservices account keys list `
    --name $openAiName `
    --resource-group $ResourceGroup `
    --query "key1" -o tsv

if ($LASTEXITCODE -ne 0) { throw "Failed to retrieve API key" }

$envFile = Join-Path $ProjectDir ".env"
@"
AZURE_OPENAI_ENDPOINT=$endpoint
AZURE_OPENAI_API_KEY=$apiKey
AZURE_OPENAI_DEPLOYMENT_NAME=$deploymentName
"@ | Set-Content -Path $envFile -Encoding UTF8

Write-Host ""
Write-Host "✅ Deployment complete!" -ForegroundColor Green
Write-Host ""
Write-Host "   Resource Group:  $ResourceGroup"
Write-Host "   OpenAI Account:  $openAiName"
Write-Host "   Endpoint:        $endpoint"
Write-Host "   Deployment:      $deploymentName"
Write-Host ""
Write-Host "   .env written to: $envFile"
Write-Host ""
Write-Host "   To run the app:"
Write-Host "     # Load env vars (PowerShell):"
Write-Host "     Get-Content $envFile | ForEach-Object { if (`$_ -match '^(.+?)=(.*)$') { [Environment]::SetEnvironmentVariable(`$Matches[1], `$Matches[2], 'Process') } }"
Write-Host "     cd $ProjectDir; mvn spring-boot:run"
