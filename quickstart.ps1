#!/usr/bin/env pwsh
# Quick start script for Distributed Workflow Engine
# Usage: .\quickstart.ps1

$ErrorActionPreference = "Stop"
$API_URL = "http://localhost:8080/api"

Write-Host "=== Distributed Workflow Engine - Quick Start ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking prerequisites..." -ForegroundColor Cyan
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker not found. Please install Docker." -ForegroundColor Yellow
    exit 1
}
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker Compose not found. Please install Docker Compose." -ForegroundColor Yellow
    exit 1
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Maven (mvn) not found. Please install Maven or add it to PATH." -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Docker, Docker Compose, and Maven found" -ForegroundColor Green
Write-Host ""

# Build JAR locally. Docker image copies prebuilt JAR.
Write-Host "Building JAR locally with Maven..." -ForegroundColor Cyan
& mvn clean package -DskipTests --no-transfer-progress
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven build failed. Fix compilation errors and retry." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] JAR built: target/work-flow-engine-1.0.0.jar" -ForegroundColor Green
Write-Host ""

Write-Host "Building Docker image..." -ForegroundColor Cyan
docker-compose build
Write-Host "[OK] Docker image ready" -ForegroundColor Green
Write-Host ""

Write-Host "Starting services (PostgreSQL, Redis, RabbitMQ, API, Workers)..." -ForegroundColor Cyan
docker-compose up -d
Write-Host "[OK] Services starting..." -ForegroundColor Green
Write-Host ""

Write-Host "Waiting for API to be ready..." -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        Invoke-RestMethod -Uri "$API_URL/workflows" -ErrorAction Stop | Out-Null
        $ready = $true
        Write-Host "[OK] API is ready" -ForegroundColor Green
        break
    }
    catch {
        Write-Host "Waiting... ($i/30)"
        Start-Sleep -Seconds 1
    }
}
Write-Host ""

if (-not $ready) {
    Write-Host "[ERROR] API did not respond in time. Try again in a moment." -ForegroundColor Yellow
    Write-Host "Check logs with: docker-compose logs api"
    exit 1
}

Write-Host "Loading sample workflows..." -ForegroundColor Cyan

$sequentialBody = @{
    name = "sequential-pipeline"
    description = "Sequential data processing pipeline"
    definition = @{
        steps = @(
            @{ id = "fetch"; type = "fetch_data"; dependencies = @(); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "process"; type = "process_data"; dependencies = @("fetch"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "validate"; type = "validate_data"; dependencies = @("process"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "publish"; type = "publish"; dependencies = @("validate"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } }
        )
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Method Post -Uri "$API_URL/workflows" -ContentType "application/json" -Body $sequentialBody | Out-Null
Write-Host "[OK] Sequential workflow loaded" -ForegroundColor Green

$parallelBody = @{
    name = "parallel-pipeline"
    description = "Parallel data processing with convergence"
    definition = @{
        steps = @(
            @{ id = "init"; type = "fetch_data"; dependencies = @(); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "process-a"; type = "process_data"; dependencies = @("init"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "process-b"; type = "process_data"; dependencies = @("init"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "validate-a"; type = "validate_data"; dependencies = @("process-a"); retryConfig = @{ maxAttempts = 2; initialDelayMs = 500; backoffMultiplier = 2.0 } },
            @{ id = "validate-b"; type = "validate_data"; dependencies = @("process-b"); retryConfig = @{ maxAttempts = 2; initialDelayMs = 500; backoffMultiplier = 2.0 } },
            @{ id = "aggregate"; type = "aggregate"; dependencies = @("validate-a", "validate-b"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } },
            @{ id = "publish"; type = "publish"; dependencies = @("aggregate"); retryConfig = @{ maxAttempts = 3; initialDelayMs = 1000; backoffMultiplier = 2.0 } }
        )
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Method Post -Uri "$API_URL/workflows" -ContentType "application/json" -Body $parallelBody | Out-Null
Write-Host "[OK] Parallel workflow loaded" -ForegroundColor Green
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] Workflow Engine is running" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:"
Write-Host "1. View workflows:   Invoke-RestMethod -Uri '$API_URL/workflows'"
Write-Host "2. Submit execution: Invoke-RestMethod -Method Post -Uri '$API_URL/workflows/1/executions'"
Write-Host "3. Check status:     Invoke-RestMethod -Uri '$API_URL/workflows/executions/1'"
Write-Host "4. API logs:         docker-compose logs -f api"
Write-Host "5. Worker logs:      docker-compose logs -f worker-1"
Write-Host "6. RabbitMQ UI:      http://localhost:15672 (guest/guest)"
Write-Host "7. Stop services:    docker-compose down"
Write-Host ""

