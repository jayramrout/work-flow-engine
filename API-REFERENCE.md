# Workflow Engine API Reference

## Base URL
```
http://localhost:8080/api
```

## Endpoints

### 1. Create Workflow

**POST** `/workflows`

Create a new workflow definition.

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-workflow",
    "description": "My first workflow",
    "definition": {
      "steps": [
        {
          "id": "step-1",
          "type": "fetch_data",
          "dependencies": [],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        },
        {
          "id": "step-2",
          "type": "process_data",
          "dependencies": ["step-1"],
          "retryConfig": {
            "maxAttempts": 3,
            "initialDelayMs": 1000,
            "backoffMultiplier": 2.0
          }
        }
      ]
    }
  }'
```

Response:
```json
{
  "id": 1,
  "name": "my-workflow",
  "description": "My first workflow",
  "definition": {...},
  "createdAt": "2026-03-21T10:30:00Z",
  "updatedAt": "2026-03-21T10:30:00Z"
}
```

---

### 2. Get Workflow by ID

**GET** `/workflows/{id}`

Retrieve workflow definition by ID.

```bash
curl http://localhost:8080/api/workflows/1
```

---

### 3. Get Workflow by Name

**GET** `/workflows/by-name/{name}`

Retrieve workflow definition by name.

```bash
curl http://localhost:8080/api/workflows/by-name/my-workflow
```

---

### 4. List All Workflows

**GET** `/workflows`

List all defined workflows.

```bash
curl http://localhost:8080/api/workflows
```

Response:
```json
[
  {
    "id": 1,
    "name": "sequential-pipeline",
    ...
  },
  {
    "id": 2,
    "name": "parallel-pipeline",
    ...
  }
]
```

---

### 5. Submit Workflow Execution

**POST** `/workflows/{id}/executions`

Create and start a new execution of a workflow.

```bash
curl -X POST http://localhost:8080/api/workflows/1/executions
```

Response:
```json
{
  "id": 100,
  "workflowId": 1,
  "status": "PENDING",
  "createdAt": "2026-03-21T10:31:00Z",
  "updatedAt": "2026-03-21T10:31:00Z",
  "completedAt": null,
  "steps": [
    {
      "id": 1000,
      "stepId": "step-1",
      "status": "PENDING",
      "attemptCount": 0,
      "maxRetries": 3,
      "lastError": null,
      "executionHistory": [],
      "createdAt": "2026-03-21T10:31:00Z",
      "completedAt": null
    },
    ...
  ]
}
```

---

### 6. Get Execution Status

**GET** `/workflows/executions/{executionId}`

Get the current status and details of a workflow execution.

```bash
curl http://localhost:8080/api/workflows/executions/100
```

Response:
```json
{
  "id": 100,
  "workflowId": 1,
  "status": "RUNNING",
  "createdAt": "2026-03-21T10:31:00Z",
  "updatedAt": "2026-03-21T10:31:15Z",
  "completedAt": null,
  "errorMessage": null,
  "steps": [
    {
      "id": 1000,
      "stepId": "step-1",
      "status": "COMPLETED",
      "attemptCount": 1,
      "maxRetries": 3,
      "lastError": null,
      "executionHistory": [
        {
          "attempt": 1,
          "status": "COMPLETED",
          "timestamp": "2026-03-21T10:31:05Z"
        }
      ],
      "createdAt": "2026-03-21T10:31:00Z",
      "completedAt": "2026-03-21T10:31:05Z"
    },
    {
      "id": 1001,
      "stepId": "step-2",
      "status": "RUNNING",
      "attemptCount": 1,
      "maxRetries": 3,
      "lastError": null,
      "executionHistory": [
        {
          "attempt": 1,
          "status": "RUNNING",
          "timestamp": "2026-03-21T10:31:15Z"
        }
      ],
      "createdAt": "2026-03-21T10:31:00Z",
      "completedAt": null
    }
  ]
}
```

---

### 7. Get Next Executable Steps

**GET** `/workflows/executions/{executionId}/next-steps`

Get the list of steps ready to execute (all dependencies satisfied).

```bash
curl http://localhost:8080/api/workflows/executions/100/next-steps
```

Response:
```json
["step-2", "step-3"]
```

---

## Workflow Definition Guide

### Step Configuration

Each step in a workflow definition has:

```json
{
  "id": "unique-step-id",
  "type": "step_type",
  "description": "Optional description",
  "dependencies": ["step-a", "step-b"],
  "retryConfig": {
    "maxAttempts": 3,
    "initialDelayMs": 1000,
    "backoffMultiplier": 2.0
  }
}
```

**Fields:**
- `id` (string, required): Unique identifier for this step
- `type` (string, required): Step type (determines executor)
- `description` (string, optional): Human-readable description
- `dependencies` (array, optional): IDs of steps that must complete first
- `retryConfig` (object, optional): Retry behavior

### Supported Step Types

- `fetch_data`: Simulates fetching data
- `process_data`: Simulates data processing
- `validate_data`: Simulates data validation
- `aggregate`: Simulates result aggregation
- `publish`: Simulates publishing results
- Custom types can be added to `StepExecutorService`

### Execution Status

**Workflow Status:**
- `PENDING`: Waiting to start
- `RUNNING`: Steps executing
- `COMPLETED`: All steps completed successfully
- `FAILED`: One or more steps failed
- `CANCELLED`: Workflow was cancelled

**Step Status:**
- `PENDING`: Waiting for dependencies
- `RUNNING`: Currently executing
- `COMPLETED`: Successful completion
- `FAILED`: Permanent failure (max retries exhausted)
- `RETRYING`: Failed but will retry

---

## Common Workflows

### Sequential Pipeline

Steps execute one after another:

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sequential",
    "definition": {
      "steps": [
        {"id": "a", "type": "fetch_data", "dependencies": []},
        {"id": "b", "type": "process_data", "dependencies": ["a"]},
        {"id": "c", "type": "publish", "dependencies": ["b"]}
      ]
    }
  }'
```

Execution: A → B → C

---

### Parallel Pipeline

Multiple branches execute concurrently:

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "parallel",
    "definition": {
      "steps": [
        {"id": "init", "type": "fetch_data", "dependencies": []},
        {"id": "a", "type": "process_data", "dependencies": ["init"]},
        {"id": "b", "type": "process_data", "dependencies": ["init"]},
        {"id": "c", "type": "aggregate", "dependencies": ["a", "b"]},
        {"id": "d", "type": "publish", "dependencies": ["c"]}
      ]
    }
  }'
```

Execution:
```
  init
   / \
  a   b  (parallel)
   \ /
    c
    |
    d
```

---

## Testing Commands

### Load Sample Workflows and Execute

```bash
# Create sequential workflow
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d @- << 'EOF'
{
  "name": "test-seq",
  "definition": {
    "steps": [
      {"id": "s1", "type": "fetch_data", "dependencies": [], "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}},
      {"id": "s2", "type": "process_data", "dependencies": ["s1"], "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}},
      {"id": "s3", "type": "publish", "dependencies": ["s2"], "retryConfig": {"maxAttempts": 3, "initialDelayMs": 1000, "backoffMultiplier": 2.0}}
    ]
  }
}
EOF

# Submit execution
EXEC_ID=$(curl -s -X POST http://localhost:8080/api/workflows/1/executions | jq -r '.id')

# Poll status
watch -n 1 "curl -s http://localhost:8080/api/workflows/executions/$EXEC_ID | jq '.status, .steps[].status'"
```

### Monitor Real-Time Progress

```bash
# Terminal 1: Watch execution
watch -n 1 'curl -s http://localhost:8080/api/workflows/executions/1 | jq'

# Terminal 2: Watch logs
docker-compose logs -f worker-1
```

### Check Database State

```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U workflow_user -d workflow_db

# View all executions
SELECT id, status, created_at FROM workflow_executions ORDER BY id DESC LIMIT 10;

# View step executions for a specific execution
SELECT step_id, status, attempt_count FROM step_executions WHERE execution_id = 1;

# View execution history
SELECT id, execution_id, step_id, status FROM step_executions WHERE status = 'COMPLETED';
```

---

## Error Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request (invalid DAG, duplicate step ID, etc.) |
| 404 | Not found (workflow or execution doesn't exist) |
| 500 | Server error (unexpected exception) |

---

## Tips

1. **Pretty-print JSON**: Pipe to `| jq`
2. **Extract fields**: Use `jq '.[] | {id, status}'`
3. **Save responses**: Redirect to file: `> response.json`
4. **Batch operations**: Use loop: `for i in {1..10}; do curl ...; done`
