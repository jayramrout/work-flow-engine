# Distributed Workflow Engine

A production-ready distributed workflow execution engine built with Spring Boot, supporting multi-step workflows with proper state management, concurrency control, and fault tolerance.

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (if running locally without Docker)
- Maven 3.9+ (if building locally)

### Build & Run

```bash
# Build the Docker image
docker-compose build

# Start all services (API, workers, infrastructure)
docker-compose up -d

# Verify services are running
docker-compose ps

# View logs
docker-compose logs -f api
docker-compose logs -f worker-1
```

The API will be available at `http://localhost:8080/api`

### Stopping Services

```bash
docker-compose down
```

## Architecture Overview

### System Design

```
┌──────────────────────────────────────────────────────────┐
│                    REST API (Port 8080)                  │
│  • Submit workflows                                      │
│  • Query execution status                                │
│  • List workflows                                        │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │   Execution Service        │
        │  • DAG validation          │
        │  • Step scheduling         │
        │  • State management        │
        └────┬─────────────────┬─────┘
             │                 │
        ┌────▼──┐      ┌──────▼────┐
        │        │      │           │
   PostgreSQL   Redis   RabbitMQ
    (State)    (Locks)  (Tasks)
        │        │      │           │
        └────┬─────────┬────────────┘
             │         │
             ▼         ▼
     ┌──────────────────────┐
     │   Worker Pool        │
     │  • Poll task queue   │
     │  • Acquire locks     │
     │  • Execute steps     │
     │  • Report status     │
     └──────────────────────┘
```

### Key Components

**Workflow Definition (DAG)**
- Directed Acyclic Graph of steps
- Each step has: ID, type, dependencies, retry config
- Dependencies define execution order
- Parallel steps execute concurrently

**State Management**
- PostgreSQL persists all state (workflows, executions, steps)
- Execution lifecycle: PENDING → RUNNING → COMPLETED/FAILED
- Step lifecycle: PENDING → RUNNING → COMPLETED/FAILED/RETRYING
- Full audit trail with execution history

**Distribution & Concurrency**
- RabbitMQ distributes tasks to worker pool
- Redis distributed locks prevent duplicate execution
- Multiple workers execute steps in parallel
- Handles worker crashes gracefully

**Fault Tolerance**
- Configurable retry with exponential backoff
- Dead letter queue for failed deliveries
- State persisted across system restarts
- Automatic recovery of in-flight tasks

## Usage Examples

### 1. Create a Workflow

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "data-pipeline",
    "description": "Simple data processing pipeline",
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
          "id": "publish",
          "type": "publish",
          "dependencies": ["process"],
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
  "name": "data-pipeline",
  "description": "Simple data processing pipeline",
  "definition": {...},
  "createdAt": "2026-03-21T10:30:00Z",
  "updatedAt": "2026-03-21T10:30:00Z"
}
```

### 2. Submit a Workflow Execution

```bash
curl -X POST http://localhost:8080/api/workflows/1/executions \
  -H "Content-Type: application/json"
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
  "steps": [...]
}
```

### 3. Query Execution Status

```bash
# Get full execution status with all step details
curl http://localhost:8080/api/workflows/executions/100

# Get next executable steps
curl http://localhost:8080/api/workflows/executions/100/next-steps
```

### 4. List All Workflows

```bash
curl http://localhost:8080/api/workflows
```

### 5. Get Workflow by Name

```bash
curl http://localhost:8080/api/workflows/by-name/data-pipeline
```

## Testing Instructions

### Manual Testing

1. **Start the system**
   ```bash
   docker-compose up -d
   ```

2. **Create a simple workflow**
   ```bash
   # Use the curl commands from Usage Examples section
   ```

3. **Submit and monitor execution**
   ```bash
   # Submit
   curl -X POST http://localhost:8080/api/workflows/1/executions
   
   # Poll status (watch it progress)
   watch -n 1 'curl http://localhost:8080/api/workflows/executions/100'
   ```

4. **Verify worker execution**
   ```bash
   # Check worker logs
   docker-compose logs worker-1
   docker-compose logs worker-2
   ```

5. **Test infrastructure access**
   ```bash
   # PostgreSQL
   docker-compose exec postgres psql -U workflow_user -d workflow_db -c "SELECT * FROM workflow_executions;"
   
   # Redis
   docker-compose exec redis redis-cli KEYS "step_lock*"
   
   # RabbitMQ Management UI
   # Browse to http://localhost:15672 (guest/guest)
   ```

### Test Scenarios

**Scenario 1: Sequential Execution**
- Create workflow with linear dependency chain (A → B → C → D)
- Submit execution
- Verify steps execute in order
- Check that no step starts until previous completes

**Scenario 2: Parallel Execution**
- Create workflow with parallel branches
- Submit execution
- Verify branches execute concurrently
- Check timing via execution history
- Monitor both workers picking up tasks

**Scenario 3: Worker Failure Recovery**
- Submit workflow execution
- Kill a worker mid-execution
- Verify lock times out after 30s
- Another worker picks up the task
- Workflow continues normally

**Scenario 4: Retry Logic** (when implemented)
- Configure a step type that fails
- Verify it retries with backoff
- Check attempt count increases
- Eventually fails after max retries exhausted

## Docker Services

### API Server (8080)
- REST API for workflow management
- Scheduler service publishes ready steps to queue
- `WORKER_ENABLED=false` (API-only mode)

### Worker Nodes (8081, 8082)
- Poll RabbitMQ for tasks
- Execute steps using StepExecutorService
- Report status back to database
- `WORKER_ENABLED=true` (Worker mode)

### PostgreSQL (5432)
- Stores all workflow, execution, and step data
- ACID transactions for consistency
- Automatic connection pooling

### Redis (6379)
- Distributed locks for step exclusivity
- Lock timeout: 30 seconds
- Prevents duplicate execution

### RabbitMQ (5672 / Management: 15672)
- Task queue for step distribution
- Dead letter queue for failed messages
- Management UI at `http://localhost:15672`
- Credentials: guest/guest

## Project Structure

```
workflow-engine/
├── src/main/java/com/athenahealth/workflow/
│   ├── controller/           # REST API endpoints
│   ├── service/              # Business logic
│   │   ├── WorkflowService
│   │   ├── ExecutionService
│   │   ├── WorkflowSchedulerService
│   │   ├── WorkerService
│   │   └── StepExecutorService
│   ├── domain/               # JPA entities
│   ├── repository/           # Data access
│   ├── messaging/            # Message definitions
│   ├── config/               # Spring configuration
│   └── WorkflowEngineApplication.java
├── src/main/resources/
│   └── application.properties
├── pom.xml                   # Maven dependencies
├── Dockerfile                # Container image
├── docker-compose.yml        # Local deployment
├── README.md                 # This file
├── DESIGN.md                 # Architecture decisions
└── examples/
    └── workflows.json        # Example workflow definitions
```

## Configuration

Key properties in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://postgres:5432/workflow_db
spring.datasource.username=workflow_user
spring.datasource.password=workflow_password

# Redis
spring.data.redis.host=redis
spring.data.redis.port=6379

# RabbitMQ
spring.rabbitmq.host=rabbitmq
spring.rabbitmq.port=5672

# Worker
worker.enabled=true|false
worker.queue-poll-interval-ms=1000
worker.max-retry-attempts=3
worker.retry-backoff-multiplier=2.0
worker.initial-retry-delay-ms=1000
```

## Operational Considerations

### Monitoring
- Check worker logs for task execution
- Query database for execution history
- Use RabbitMQ Management UI to monitor queue depth
- Track Redis locks for blocked steps

### Debugging
- Enable DEBUG logging in application.properties
- Check execution history for step attempts
- Verify task is in RabbitMQ queue (Management UI)
- Confirm Redis lock exists (redis-cli)

### Scaling
- Add more worker containers via docker-compose
- RabbitMQ distributes tasks automatically
- Redis locks ensure no duplicate execution
- Database connection pooling (HikariCP) handles concurrency

### Known Limitations
- In-memory scheduling (no cluster coordination)
- Retry delay not fully implemented yet
- No timeout on step execution
- No compensation/rollback logic
- Single database instance (no replication)

## Next Steps & Future Improvements

See DESIGN.md for detailed roadmap.

## Support

For questions or issues:
1. Check logs: `docker-compose logs [service]`
2. Review DESIGN.md for architecture decisions
3. Verify service health: `docker-compose ps`
