#!/bin/bash

# Script to load sample workflows into the workflow engine
# Usage: ./load-samples.sh

API_URL="http://localhost:8080/api"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Loading sample workflows...${NC}\n"

# Load Sequential Workflow
echo -e "${BLUE}1. Loading Sequential Workflow...${NC}"
RESPONSE=$(curl -s -X POST $API_URL/workflows \
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
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "process",
          "type": "process_data",
          "dependencies": ["fetch"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "validate",
          "type": "validate_data",
          "dependencies": ["process"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "publish",
          "type": "publish",
          "dependencies": ["validate"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        }
      ]
    }
  }')

WORKFLOW_ID=$(echo $RESPONSE | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ ! -z "$WORKFLOW_ID" ]; then
  echo -e "${GREEN}✓ Sequential workflow created (ID: $WORKFLOW_ID)${NC}\n"
else
  echo -e "✗ Failed to create sequential workflow${NC}\n"
  exit 1
fi

# Load Parallel Workflow
echo -e "${BLUE}2. Loading Parallel Workflow...${NC}"
RESPONSE=$(curl -s -X POST $API_URL/workflows \
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
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "process-a",
          "type": "process_data",
          "dependencies": ["init"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "process-b",
          "type": "process_data",
          "dependencies": ["init"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "validate-a",
          "type": "validate_data",
          "dependencies": ["process-a"],
          "retryConfig": {
            "maxAttempts": 2,
            "initialDelayMs": 500,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "validate-b",
          "type": "validate_data",
          "dependencies": ["process-b"],
          "retryConfig": {
            "maxAttempts": 2,
            "initialDelayMs": 500,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "aggregate",
          "type": "aggregate",
          "dependencies": ["validate-a", "validate-b"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "publish",
          "type": "publish",
          "dependencies": ["aggregate"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        }
      ]
    }
  }')

WORKFLOW_ID=$(echo $RESPONSE | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ ! -z "$WORKFLOW_ID" ]; then
  echo -e "${GREEN}✓ Parallel workflow created (ID: $WORKFLOW_ID)${NC}\n"
else
  echo -e "✗ Failed to create parallel workflow${NC}\n"
fi

# List all workflows
echo -e "${BLUE}3. Listing all workflows...${NC}"
curl -s $API_URL/workflows | jq '.[] | {id, name, description}'

echo -e "\n${GREEN}Sample workflows loaded successfully!${NC}"
echo -e "To submit an execution, use:"
echo -e "  curl -X POST $API_URL/workflows/{id}/executions"
