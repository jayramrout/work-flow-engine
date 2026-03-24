package com.athenahealth.workflow.service;

import com.athenahealth.workflow.config.RabbitMQConfig;
import com.athenahealth.workflow.config.WorkerProperties;
import com.athenahealth.workflow.domain.WorkflowEntity;
import com.athenahealth.workflow.domain.WorkflowExecutionEntity;
import com.athenahealth.workflow.domain.WorkflowStatus;
import com.athenahealth.workflow.messaging.WorkerTask;
import com.athenahealth.workflow.repository.WorkflowExecutionRepository;
import com.athenahealth.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduler that periodically polls for executable workflow steps and publishes them to the task queue.
 * This is the bridge between the execution engine and the worker pool.
 */
@Service
@ConditionalOnProperty(prefix = "scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WorkflowSchedulerService {

    private final ExecutionService executionService;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;
    private final RabbitTemplate rabbitTemplate;
    private final WorkerProperties workerProperties;

    /**
     * Scheduled task that runs every 1 second to publish ready steps to the task queue.
     * This is the heartbeat of the workflow engine.
     */
    @Scheduled(fixedRateString = "${worker.queue-poll-interval-ms:1000}")
    public void scheduleExecutableSteps() {
        try {
            // Find all running workflow executions
            List<WorkflowExecutionEntity> runningExecutions = 
                executionRepository.findByStatus(WorkflowStatus.RUNNING);

            if (runningExecutions.isEmpty()) {
                // Also check for PENDING executions
                runningExecutions = executionRepository.findByStatus(WorkflowStatus.PENDING);
            }

            for (WorkflowExecutionEntity execution : runningExecutions) {
                // Get next executable steps
                List<String> executableSteps = executionService.getNextExecutableSteps(execution.getId());

                if (!executableSteps.isEmpty()) {
                    WorkflowEntity workflow = workflowRepository.findById(execution.getWorkflowId())
                        .orElse(null);

                    if (workflow != null) {
                        for (String stepId : executableSteps) {
                            publishStepToQueue(execution.getId(), stepId, workflow.getDefinition());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in workflow scheduler", e);
        }
    }

    /**
     * Publish a single step to the task queue for workers to pick up.
     */
    private void publishStepToQueue(Long executionId, String stepId, JsonNode workflowDefinition) {
        try {
            // Find the step definition
            JsonNode stepsNode = workflowDefinition.get("steps");
            JsonNode stepDefinition = null;

            for (JsonNode step : stepsNode) {
                if (step.get("id").asText().equals(stepId)) {
                    stepDefinition = step;
                    break;
                }
            }

            if (stepDefinition == null) {
                log.warn("Step definition not found: {}", stepId);
                return;
            }

            // Extract step configuration
            String stepType = stepDefinition.get("type").asText();
            JsonNode retryConfigNode = stepDefinition.get("retryConfig");

            WorkerTask.RetryConfig retryConfig = WorkerTask.RetryConfig.builder()
                .maxAttempts(retryConfigNode != null ? retryConfigNode.get("maxAttempts").asInt() : workerProperties.getMaxRetryAttempts())
                .initialDelayMs(retryConfigNode != null ? retryConfigNode.get("initialDelayMs").asLong() : workerProperties.getInitialRetryDelayMs())
                .backoffMultiplier(retryConfigNode != null ? retryConfigNode.get("backoffMultiplier").asDouble() : workerProperties.getRetryBackoffMultiplier())
                .build();

            // Create and publish task
            WorkerTask task = WorkerTask.builder()
                .executionId(executionId)
                .stepId(stepId)
                .stepType(stepType)
                .retryConfig(retryConfig)
                .build();

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.TASK_ROUTING_KEY,
                task
            );

            log.debug("Published task to queue. ExecutionId: {}, StepId: {}, Type: {}",
                executionId, stepId, stepType);

        } catch (Exception e) {
            log.error("Error publishing step to queue. ExecutionId: {}, StepId: {}",
                executionId, stepId, e);
        }
    }
}
