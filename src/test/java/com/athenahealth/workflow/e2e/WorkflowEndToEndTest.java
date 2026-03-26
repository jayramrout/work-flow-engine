package com.athenahealth.workflow.e2e;

import com.athenahealth.workflow.dto.StepExecutionResponse;
import com.athenahealth.workflow.dto.WorkflowExecutionResponse;
import com.athenahealth.workflow.dto.WorkflowRequest;
import com.athenahealth.workflow.dto.WorkflowResponse;
import com.athenahealth.workflow.messaging.WorkerTask;
import com.athenahealth.workflow.service.WorkerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * End-to-end integration test for the Workflow Engine.
 *
 * <p>All three infrastructure dependencies run <b>in-process</b> — no Docker,
 * no external services needed:
 *
 * <table>
 *   <tr><th>Dependency</th><th>Replacement</th></tr>
 *   <tr><td>PostgreSQL</td><td>H2 in PostgreSQL-compat mode</td></tr>
 *   <tr><td>RabbitMQ</td>
 *       <td>{@code @MockBean RabbitTemplate} — every {@code convertAndSend}
 *           call the scheduler makes is intercepted and routed directly to
 *           {@link WorkerService#processTask} in an async thread.
 *           {@link InMemoryInfraConfig} overrides the listener-container factory
 *           with {@code autoStartup=false} so no AMQP connection is ever
 *           attempted.</td></tr>
 *   <tr><td>Redis</td>
 *       <td>fppt/jedis-mock started on a random free port via {@code @BeforeAll}
 *           and wired in via {@code @DynamicPropertySource}.</td></tr>
 * </table>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // ── PostgreSQL → H2 (PostgreSQL-compat mode) ─────────────────────────
        "spring.datasource.url=" +
                "jdbc:h2:mem:wfe_e2e;MODE=PostgreSQL;" +
                "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // ── Both scheduler and real WorkerService are active ─────────────────
        "scheduler.enabled=true",
        "worker.enabled=true",
        // ── Point RabbitMQ at localhost so DNS at least resolves (safety net) ─
        "spring.rabbitmq.host=localhost",
        // ── Allow InMemoryInfraConfig to override rabbitListenerContainerFactory ─
        // The app defines its own factory bean (RabbitMQConfig), so the Boot
        // property spring.rabbitmq.listener.simple.auto-startup has no effect.
        // We override the factory bean directly in InMemoryInfraConfig and need
        // overriding enabled so the test definition wins.
        "spring.main.allow-bean-definition-overriding=true",
        // ── Suppress actuator Redis health-check ─────────────────────────────
        "management.health.redis.enabled=false"
})
@Import(InMemoryInfraConfig.class)   // overrides rabbitListenerContainerFactory → autoStartup=false
class WorkflowEndToEndTest {

    // ── jedis-mock: in-process Redis, started once per test class ────────────

    private static RedisServer redisServer;
    private static int         redisPort;

    @BeforeAll
    static void startRedis() throws IOException {
        redisPort   = findFreePort();
        redisServer = RedisServer.newRedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> redisPort);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    // ── RabbitMQ: mock template → routes messages to TestWorkerService ────────

    /**
     * Replaces the application's {@code RabbitTemplate} bean.
     * Every {@code convertAndSend} call from the scheduler is intercepted and
     * routed directly to the real {@link WorkerService#processTask}.
     */
    @MockBean
    RabbitTemplate rabbitTemplate;

    /**
     * The real {@code WorkerService} — its {@code @RabbitListener} container is
     * prevented from auto-starting via {@code spring.rabbitmq.listener.simple.auto-startup=false},
     * so we can call {@link WorkerService#processTask} directly without a broker.
     */
    @Autowired
    WorkerService workerService;

    /**
     * Wire the mock template so that each task the scheduler publishes is handed
     * directly to {@link WorkerService#processTask} in an async thread —
     * exactly as it would arrive via a real RabbitMQ queue.
     */
    @BeforeEach
    void routeMessagesToWorker() {
        doAnswer(inv -> {
            Object payload = inv.getArgument(2);
            if (payload instanceof WorkerTask task) {
                CompletableFuture.runAsync(() -> workerService.processTask(task));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ── Common test helpers ───────────────────────────────────────────────────

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper     objectMapper;

    /** Prepends the {@code /api} context-path defined in application.properties. */
    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    /**
     * Polls {@code GET /api/workflows/executions/{id}} every 500 ms until the
     * execution status is COMPLETED or FAILED, or the timeout elapses.
     */
    private WorkflowExecutionResponse awaitTerminalStatus(Long executionId, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<WorkflowExecutionResponse> resp = rest.getForEntity(
                    url("/workflows/executions/" + executionId),
                    WorkflowExecutionResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String status = resp.getBody().getStatus();
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    return resp.getBody();
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError(
                "Execution " + executionId + " did not reach terminal status within " + timeout);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Scenario 1 – sequential 3-step pipeline
    // ════════════════════════════════════════════════════════════════════════

    /**
     * <pre>
     *   fetch_data  ──►  process_data  ──►  publish
     * </pre>
     * Verifies that the scheduler dispatches tasks in dependency order,
     * {@link TestWorkerService} executes each step, and the execution
     * reaches COMPLETED with all steps marked COMPLETED.
     */
    @Test
    @DisplayName("Sequential pipeline: fetch → process → publish completes end-to-end")
    void sequentialPipeline_allStepsReachCompleted() throws Exception {

        // 1 ── Register workflow ───────────────────────────────────────────────
        JsonNode definition = objectMapper.readTree("""
                {
                  "steps": [
                    { "id": "fetch",   "type": "fetch_data",   "dependencies": [] },
                    { "id": "process", "type": "process_data", "dependencies": ["fetch"]   },
                    { "id": "publish", "type": "publish",      "dependencies": ["process"] }
                  ]
                }
                """);

        WorkflowRequest request = WorkflowRequest.builder()
                .name("e2e-sequential-" + System.currentTimeMillis())
                .description("E2E – sequential 3-step pipeline")
                .definition(definition)
                .build();

        ResponseEntity<WorkflowResponse> createResp =
                rest.postForEntity(url("/workflows"), request, WorkflowResponse.class);

        assertThat(createResp.getStatusCode())
                .as("POST /workflows must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody()).isNotNull();
        Long workflowId = createResp.getBody().getId();
        assertThat(workflowId).isPositive();

        // 2 ── Submit execution ────────────────────────────────────────────────
        ResponseEntity<WorkflowExecutionResponse> submitResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/executions"),
                null,
                WorkflowExecutionResponse.class);

        assertThat(submitResp.getStatusCode())
                .as("POST …/executions must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(submitResp.getBody()).isNotNull();
        Long executionId = submitResp.getBody().getId();

        // 3 ── Poll until terminal (≤ 60 s) ───────────────────────────────────
        WorkflowExecutionResponse result = awaitTerminalStatus(executionId, Duration.ofSeconds(60));

        // 4 ── Assert ──────────────────────────────────────────────────────────
        assertThat(result.getStatus())
                .as("Overall execution status")
                .isEqualTo("COMPLETED");

        assertThat(result.getSteps())
                .as("All 3 steps must be COMPLETED")
                .hasSize(3)
                .allSatisfy(step ->
                        assertThat(step.getStatus())
                                .as("Step '%s'", step.getStepId())
                                .isEqualTo("COMPLETED"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Scenario 2 – diamond / fork-join pattern
    // ════════════════════════════════════════════════════════════════════════

    /**
     * <pre>
     *               ┌─► process  ─┐
     *   fetch  ─────┤              ├──► aggregate
     *               └─► validate ─┘
     * </pre>
     * Verifies that parallel branches are dispatched independently and
     * {@code aggregate} only executes after both converge.
     */
    @Test
    @DisplayName("Diamond workflow: parallel branches converge in aggregate step")
    void diamondWorkflow_parallelBranchesAndFinalAggregation() throws Exception {

        // 1 ── Register workflow ───────────────────────────────────────────────
        JsonNode definition = objectMapper.readTree("""
                {
                  "steps": [
                    { "id": "fetch",     "type": "fetch_data",    "dependencies": [] },
                    { "id": "process",   "type": "process_data",  "dependencies": ["fetch"] },
                    { "id": "validate",  "type": "validate_data", "dependencies": ["fetch"] },
                    { "id": "aggregate", "type": "aggregate",     "dependencies": ["process", "validate"] }
                  ]
                }
                """);

        WorkflowRequest request = WorkflowRequest.builder()
                .name("e2e-diamond-" + System.currentTimeMillis())
                .description("E2E – diamond fork-join pattern")
                .definition(definition)
                .build();

        ResponseEntity<WorkflowResponse> createResp =
                rest.postForEntity(url("/workflows"), request, WorkflowResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody()).isNotNull();
        Long workflowId = createResp.getBody().getId();

        // 2 ── Submit execution ────────────────────────────────────────────────
        ResponseEntity<WorkflowExecutionResponse> submitResp = rest.postForEntity(
                url("/workflows/" + workflowId + "/executions"),
                null,
                WorkflowExecutionResponse.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResp.getBody()).isNotNull();
        Long executionId = submitResp.getBody().getId();

        // 3 ── Poll until terminal ─────────────────────────────────────────────
        WorkflowExecutionResponse result = awaitTerminalStatus(executionId, Duration.ofSeconds(60));

        // 4 ── Assert ──────────────────────────────────────────────────────────
        assertThat(result.getStatus())
                .as("Overall execution status")
                .isEqualTo("COMPLETED");

        assertThat(result.getSteps())
                .as("All 4 steps must be COMPLETED")
                .hasSize(4)
                .allSatisfy(step ->
                        assertThat(step.getStatus())
                                .as("Step '%s'", step.getStepId())
                                .isEqualTo("COMPLETED"));

        // Confirm aggregate actually ran (not just created as PENDING)
        StepExecutionResponse aggregate = result.getSteps().stream()
                .filter(s -> "aggregate".equals(s.getStepId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aggregate step missing from response"));

        assertThat(aggregate.getAttemptCount())
                .as("aggregate must have been attempted at least once")
                .isGreaterThanOrEqualTo(1);
        assertThat(aggregate.getCompletedAt())
                .as("aggregate must carry a completedAt timestamp")
                .isNotNull();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Scenario 3 – delete workflow definition
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Creates a workflow, deletes it via {@code DELETE /workflows/{id}}, then
     * asserts that a subsequent GET returns 404.
     */
    @Test
    @DisplayName("DELETE /workflows/{id} removes the definition; subsequent GET returns 404")
    void deleteWorkflow_removesDefinitionAndReturns404() throws Exception {

        JsonNode definition = objectMapper.readTree("""
                {
                  "steps": [
                    { "id": "only-step", "type": "fetch_data", "dependencies": [] }
                  ]
                }
                """);

        WorkflowRequest request = WorkflowRequest.builder()
                .name("e2e-delete-" + System.currentTimeMillis())
                .description("Workflow created only to be deleted")
                .definition(definition)
                .build();

        WorkflowResponse created = rest
                .postForEntity(url("/workflows"), request, WorkflowResponse.class)
                .getBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isPositive();

        // Confirm it exists
        assertThat(rest.getForEntity(url("/workflows/" + created.getId()), WorkflowResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Delete
        rest.delete(url("/workflows/" + created.getId()));

        // Verify gone
        assertThat(rest.getForEntity(url("/workflows/" + created.getId()), WorkflowResponse.class)
                .getStatusCode())
                .as("After deletion GET must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
