#!/bin/bash

# Quick start script for Distributed Workflow Engine
# This script builds, starts, and loads sample workflows

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=== Distributed Workflow Engine - Quick Start ===${NC}\n"

# Check prerequisites
echo -e "${BLUE}Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}✗ Docker not found. Please install Docker.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${YELLOW}✗ Docker Compose not found. Please install Docker Compose.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker and Docker Compose found${NC}\n"

# Build
echo -e "${BLUE}Building Docker image...${NC}"
docker-compose build

echo -e "${GREEN}✓ Build complete${NC}\n"

# Start services
echo -e "${BLUE}Starting services (PostgreSQL, Redis, RabbitMQ, API, Workers)...${NC}"
docker-compose up -d

echo -e "${GREEN}✓ Services starting...${NC}\n"

# Wait for API to be ready
echo -e "${BLUE}Waiting for API to be ready...${NC}"
for i in {1..30}; do
  if curl -s http://localhost:8080/api/workflows > /dev/null 2>&1; then
    echo -e "${GREEN}✓ API is ready${NC}\n"
    break
  fi
  echo "Waiting... ($i/30)"
  sleep 1
done

# Check if API is responding
if ! curl -s http://localhost:8080/api/workflows > /dev/null 2>&1; then
  echo -e "${YELLOW}✗ API did not respond in time. Try again in a moment.${NC}"
  echo -e "Check logs with: docker-compose logs api${NC}"
  exit 1
fi

# Load sample workflows
echo -e "${BLUE}Loading sample workflows...${NC}"

# Sequential
curl -s -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sequential-pipeline",
    "description": "Sequential data processing pipeline",
    "definition": {
      "steps": [
        {
          "id": "fetch",
          "type": "fetch_data",
          "dependencies": [],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "process",
          "type": "process_data",
          "dependencies": ["fetch"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "validate",
          "type": "validate_data",
          "dependencies": ["process"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "publish",
          "type": "publish",
          "dependencies": ["validate"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        }
      ]
    }
  }' > /dev/null

echo -e "${GREEN}✓ Sequential workflow loaded${NC}"

# Parallel
curl -s -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "parallel-pipeline",
    "description": "Parallel data processing with convergence",
    "definition": {
      "steps": [
        {
          "id": "init",
          "type": "fetch_data",
          "dependencies": [],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "process-a",
          "type": "process_data",
          "dependencies": ["init"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "process-b",
          "type": "process_data",
          "dependencies": ["init"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "validate-a",
          "type": "validate_data",
          "dependencies": ["process-a"],
          "retryConfig": {"maxAttempts": 2, "initialDelayMs": 500, "backoffMultiplier": 2.0}
        },
        {
          "id": "validate-b",
          "type": "validate_data",
          "dependencies": ["process-b"],
          "retryConfig": {"maxAttempts": 2, "initialDelayMs": 500, "backoffMultiplier": 2.0}
        },
        {
          "id": "aggregate",
          "type": "aggregate",
          "dependencies": ["validate-a", "validate-b"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        },
        {
          "id": "publish",
          "type": "publish",
          "dependencies": ["aggregate"],
          "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}
        }
      ]
    }
  }' > /dev/null

echo -e "${GREEN}✓ Parallel workflow loaded${NC}\n"

# Print next steps
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ Workflow Engine is running!${NC}"
echo -e "${GREEN}========================================${NC}\n"

echo -e "Next steps:\n"
echo -e "${BLUE}1. View available workflows:${NC}"
echo -e "   curl http://localhost:8080/api/workflows\n"

echo -e "${BLUE}2. Submit a workflow execution:${NC}"
echo -e "   curl -X POST http://localhost:8080/api/workflows/1/executions\n"

echo -e "${BLUE}3. Check execution status:${NC}"
echo -e "   curl http://localhost:8080/api/workflows/executions/1\n"

echo -e "${BLUE}4. View logs:${NC}"
echo -e "   docker-compose logs -f api${NC}"
echo -e "   docker-compose logs -f worker-1${NC}\n"

echo -e "${BLUE}5. RabbitMQ Management UI:${NC}"
echo -e "   http://localhost:15672 (guest/guest)\n"

echo -e "${BLUE}6. Stop services:${NC}"
echo -e "   docker-compose down\n"

echo -e "Documentation: See README.md for detailed usage${NC}\n"
