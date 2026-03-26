# Build script for healthedge-util with Java 21
# This script sets JAVA_HOME to JDK 21 for the current session and runs Maven build

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Spring Boot 3.3.6 Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set Java 21 path
$JAVA_21_HOME = "C:\Program Files\Java\jdk-21.0.10"

# Verify Java 21 exists
if (-Not (Test-Path $JAVA_21_HOME)) {
    Write-Host "ERROR: Java 21 not found at: $JAVA_21_HOME" -ForegroundColor Red
    Write-Host "Please verify the JDK installation path." -ForegroundColor Red
    exit 1
}

Write-Host "Setting JAVA_HOME to: $JAVA_21_HOME" -ForegroundColor Green
$env:JAVA_HOME = $JAVA_21_HOME
$env:PATH = "$JAVA_21_HOME\bin;$env:PATH"

Write-Host ""
Write-Host "Verifying Java version..." -ForegroundColor Yellow
& java -version
Write-Host ""

Write-Host "Verifying Maven..." -ForegroundColor Yellow
& mvn -version
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Maven Build..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Run Maven build
& mvn clean install -DskipTests

# Check build result
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  BUILD FAILED!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit $LASTEXITCODE
}