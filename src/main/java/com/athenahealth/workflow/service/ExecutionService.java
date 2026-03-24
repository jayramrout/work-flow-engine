package com.athenahealth.workflow.service;

import com.athenahealth.workflow.domain.*;
import com.athenahealth.workflow.dto.StepExecutionResponse;
import com.athenahealth.workflow.dto.WorkflowExecutionResponse;
import com.athenahealth.workflow.repository.StepExecutionRepository;
import com.athenahealth.workflow.repository.WorkflowExecutionRepository;
import com.athenahealth.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing workflow execution lifecycle and state transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Submit a new workflow execution.
     * Creates execution and all step execution records in PENDING state.
     */
    public WorkflowExecutionResponse submitWorkflow(Long workflowId) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        // Create execution
        WorkflowExecutionEntity execution = WorkflowExecutionEntity.builder()
            .workflowId(workflowId)
            .status(WorkflowStatus.PENDING)
            .build();
        execution = executionRepository.save(execution);

        // Create step executions
        JsonNode stepsNode = workflow.getDefinition().get("steps");
        for (JsonNode stepNode : stepsNode) {
            String stepId = stepNode.get("id").asText();
            int maxRetries = stepNode.has("retryConfig")
                ? stepNode.get("retryConfig").get("maxAttempts").asInt()
                : 3;

            StepExecutionEntity stepExecution = StepExecutionEntity.builder()
                .executionId(execution.getId())
                .stepId(stepId)
                .status(StepStatus.PENDING)
                .attemptCount(0)
                .maxRetries(maxRetries)
                .executionHistory(objectMapper.createArrayNode())
                .build();
            stepExecutionRepository.save(stepExecution);
        }

        log.info("Submitted workflow execution. ExecutionId: {}, WorkflowId: {}", execution.getId(), workflowId);
        return getExecutionStatus(execution.getId());
    }

    /**
     * Get current status of a workflow execution.
     */
    @Transactional(readOnly = true)
    public WorkflowExecutionResponse getExecutionStatus(Long executionId) {
        WorkflowExecutionEntity execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionId(executionId);

        return WorkflowExecutionResponse.builder()
            .id(execution.getId())
            .workflowId(execution.getWorkflowId())
            .status(execution.getStatus().name())
            .errorMessage(execution.getErrorMessage())
            .createdAt(execution.getCreatedAt())
            .updatedAt(execution.getUpdatedAt())
            .completedAt(execution.getCompletedAt())
            .steps(steps.stream().map(this::mapStepToResponse).collect(Collectors.toList()))
            .build();
    }

    /**
     * Get the next executable steps for a workflow execution.
     * A step is executable if:
     * - Its status is PENDING or RETRYING
     * - All its dependencies are COMPLETED
     */
    @Transactional(readOnly = true)
    public List<String> getNextExecutableSteps(Long executionId) {
        WorkflowExecutionEntity execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (execution.getStatus().isTerminal()) {
            return Collections.emptyList();
        }

        WorkflowEntity workflow = workflowRepository.findById(execution.getWorkflowId())
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionId(executionId);
        Map<String, StepExecutionEntity> stepMap = steps.stream()
            .collect(Collectors.toMap(StepExecutionEntity::getStepId, s -> s));

        JsonNode stepsNode = workflow.getDefinition().get("steps");
        List<String> executable = new ArrayList<>();

        for (JsonNode stepNode : stepsNode) {
            String stepId = stepNode.get("id").asText();
            StepExecutionEntity stepExecution = stepMap.get(stepId);

            // Must be pending or retrying
            if (stepExecution.getStatus() != StepStatus.PENDING &&
                stepExecution.getStatus() != StepStatus.RETRYING) {
                continue;
            }

            // Check dependencies
            JsonNode depsNode = stepNode.get("dependencies");
            boolean canExecute = true;

            if (depsNode != null && depsNode.isArray()) {
                for (JsonNode depNode : depsNode) {
                    String depId = depNode.asText();
                    StepExecutionEntity depExecution = stepMap.get(depId);
                    if (depExecution.getStatus() != StepStatus.COMPLETED) {
                        canExecute = false;
                        break;
                    }
                }
            }

            if (canExecute) {
                executable.add(stepId);
            }
        }

        return executable;
    }

    /**
     * Mark a step as running (acquired by worker).
     */
    @Transactional
    public int markStepRunning(Long executionId, String stepId) {
        StepExecutionEntity step = stepExecutionRepository.findByExecutionIdAndStepId(executionId, stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step execution not found"));

        step.setStatus(StepStatus.RUNNING);
        step.setAttemptCount(step.getAttemptCount() + 1);
        stepExecutionRepository.save(step);

        // Mark execution as running if not already
        WorkflowExecutionEntity execution = executionRepository.findById(executionId).orElseThrow();
        if (execution.getStatus() == WorkflowStatus.PENDING) {
            execution.setStatus(WorkflowStatus.RUNNING);
            executionRepository.save(execution);
        }

        log.debug("Step marked as running. ExecutionId: {}, StepId: {}, Attempt: {}",
            executionId, stepId, step.getAttemptCount());

        return step.getAttemptCount();
    }

    /**
     * Mark a step as completed with optional result.
     */
    @Transactional
    public void markStepCompleted(Long executionId, String stepId, JsonNode result) {
        StepExecutionEntity step = stepExecutionRepository.findByExecutionIdAndStepId(executionId, stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step execution not found"));

        step.setStatus(StepStatus.COMPLETED);
        step.setCompletedAt(Instant.now());

        // Add to execution history
        addExecutionHistory(step, "COMPLETED", result, null);

        stepExecutionRepository.save(step);

        // Check if workflow is complete
        updateWorkflowStatus(executionId);

        log.info("Step marked as completed. ExecutionId: {}, StepId: {}", executionId, stepId);
    }

    /**
     * Mark a step as failed and determine if retry is needed.
     */
    @Transactional
    public void markStepFailed(Long executionId, String stepId, String error) {
        StepExecutionEntity step = stepExecutionRepository.findByExecutionIdAndStepId(executionId, stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step execution not found"));

        step.setLastError(error);
        addExecutionHistory(step, "FAILED", null, error);

        if (step.getAttemptCount() < step.getMaxRetries()) {
            step.setStatus(StepStatus.RETRYING);
            log.info("Step will be retried. ExecutionId: {}, StepId: {}, Attempt: {}/{}",
                executionId, stepId, step.getAttemptCount(), step.getMaxRetries());
        } else {
            step.setStatus(StepStatus.FAILED);
            step.setCompletedAt(Instant.now());
            log.error("Step exhausted retries. ExecutionId: {}, StepId: {}, Error: {}",
                executionId, stepId, error);
        }

        stepExecutionRepository.save(step);

        // Update workflow status if failed
        if (step.getStatus() == StepStatus.FAILED) {
            updateWorkflowStatus(executionId);
        }
    }

    /**
     * Update workflow status based on step states.
     */
    private void updateWorkflowStatus(Long executionId) {
        WorkflowExecutionEntity execution = executionRepository.findById(executionId).orElseThrow();
        List<StepExecutionEntity> steps = stepExecutionRepository.findByExecutionId(executionId);

        boolean hasRunning = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.RUNNING);
        boolean hasRetrying = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.RETRYING);
        boolean allCompleted = steps.stream().allMatch(s -> s.getStatus() == StepStatus.COMPLETED);
        boolean hasFailed = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED);

        if (allCompleted) {
            execution.setStatus(WorkflowStatus.COMPLETED);
            execution.setCompletedAt(Instant.now());
        } else if (hasFailed) {
            execution.setStatus(WorkflowStatus.FAILED);
            execution.setCompletedAt(Instant.now());
            execution.setErrorMessage("One or more steps failed");
        } else if (hasRunning || hasRetrying) {
            execution.setStatus(WorkflowStatus.RUNNING);
        }

        executionRepository.save(execution);
    }

    /**
     * Add entry to step execution history.
     */
    private void addExecutionHistory(StepExecutionEntity step, String status, JsonNode result, String error) {
        ArrayNode history = (ArrayNode) step.getExecutionHistory();
        if (history == null) {
            history = objectMapper.createArrayNode();
        }

        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("attempt", step.getAttemptCount());
        entry.put("status", status);
        entry.put("timestamp", Instant.now().toString());
        if (result != null) {
            entry.set("result", result);
        }
        if (error != null) {
            entry.put("error", error);
        }

        history.add(entry);
        step.setExecutionHistory(history);
    }

    private StepExecutionResponse mapStepToResponse(StepExecutionEntity entity) {
        return StepExecutionResponse.builder()
            .id(entity.getId())
            .stepId(entity.getStepId())
            .status(entity.getStatus().name())
            .attemptCount(entity.getAttemptCount())
            .maxRetries(entity.getMaxRetries())
            .lastError(entity.getLastError())
            .executionHistory(entity.getExecutionHistory())
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .build();
    }
}
