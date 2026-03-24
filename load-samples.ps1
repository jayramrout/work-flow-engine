# Script to load sample workflows into the workflow engine
# Usage: .\load-samples.ps1

$API_URL = "http://localhost:8080/api"

Write-Host "Loading sample workflows..." -ForegroundColor Cyan
Write-Host ""

# Load Sequential Workflow
Write-Host "1. Loading Sequential Workflow..." -ForegroundColor Cyan

$sequentialBody = @{
    name        = "sequential-pipeline"
    description = "Sequential data processing pipeline"
    definition  = @{
        steps = @(
            @{
                id           = "fetch"
                type         = "fetch_data"
                dependencies = @()
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "process"
                type         = "process_data"
                dependencies = @("fetch")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "validate"
                type         = "validate_data"
                dependencies = @("process")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "publish"
                type         = "publish"
                dependencies = @("validate")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            }
        )
    }
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-RestMethod -Method Post -Uri "$API_URL/workflows" `
        -ContentType "application/json" -Body $sequentialBody
    $workflowId = $response.id
    if ($workflowId) {
        Write-Host "✓ Sequential workflow created (ID: $workflowId)" -ForegroundColor Green
    } else {
        Write-Host "✗ Failed to create sequential workflow" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ Failed to create sequential workflow: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Load Parallel Workflow
Write-Host "2. Loading Parallel Workflow..." -ForegroundColor Cyan

$parallelBody = @{
    name        = "parallel-pipeline"
    description = "Parallel data processing with convergence"
    definition  = @{
        steps = @(
            @{
                id           = "init"
                type         = "fetch_data"
                dependencies = @()
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "process-a"
                type         = "process_data"
                dependencies = @("init")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "process-b"
                type         = "process_data"
                dependencies = @("init")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "validate-a"
                type         = "validate_data"
                dependencies = @("process-a")
                retryConfig  = @{ maxAttempts = 2; initialDelayMs = 500; backoffMultiplier = 2.0 }
            },
            @{
                id           = "validate-b"
                type         = "validate_data"
                dependencies = @("process-b")
                retryConfig  = @{ maxAttempts = 2; initialDelayMs = 500; backoffMultiplier = 2.0 }
            },
            @{
                id           = "aggregate"
                type         = "aggregate"
                dependencies = @("validate-a", "validate-b")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            },
            @{
                id           = "publish"
                type         = "publish"
                dependencies = @("aggregate")
                retryConfig  = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 }
            }
        )
    }
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-RestMethod -Method Post -Uri "$API_URL/workflows" `
        -ContentType "application/json" -Body $parallelBody
    $workflowId = $response.id
    if ($workflowId) {
        Write-Host "✓ Parallel workflow created (ID: $workflowId)" -ForegroundColor Green
    } else {
        Write-Host "✗ Failed to create parallel workflow" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Failed to create parallel workflow: $_" -ForegroundColor Red
}
Write-Host ""

# List all workflows
Write-Host "3. Listing all workflows..." -ForegroundColor Cyan
$workflows = Invoke-RestMethod -Uri "$API_URL/workflows"
$workflows | ForEach-Object {
    [PSCustomObject]@{ id = $_.id; name = $_.name; description = $_.description }
} | Format-Table -AutoSize

Write-Host ""
Write-Host "Sample workflows loaded successfully!" -ForegroundColor Green
Write-Host "To submit an execution, use:"
Write-Host "  Invoke-RestMethod -Method Post -Uri `"$API_URL/workflows/{id}/executions`""

