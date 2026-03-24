package com.athenahealth.workflow.service;

import com.athenahealth.workflow.domain.WorkflowEntity;
import com.athenahealth.workflow.dto.WorkflowRequest;
import com.athenahealth.workflow.dto.WorkflowResponse;
import com.athenahealth.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing workflow definitions and validating DAGs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    /**
     * Create and persist a new workflow.
     * Validates the DAG structure before persisting.
     */
    public WorkflowResponse createWorkflow(WorkflowRequest request) {
        validateWorkflowDefinition(request.getDefinition());

        WorkflowEntity workflow = WorkflowEntity.builder()
            .name(request.getName())
            .description(request.getDescription())
            .definition(request.getDefinition())
            .build();

        WorkflowEntity saved = workflowRepository.save(workflow);
        return mapToResponse(saved);
    }

    /**
     * Get workflow by ID.
     */
    public WorkflowResponse getWorkflow(Long id) {
        WorkflowEntity workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        return mapToResponse(workflow);
    }

    /**
     * Get workflow by name.
     */
    public WorkflowResponse getWorkflowByName(String name) {
        WorkflowEntity workflow = workflowRepository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + name));
        return mapToResponse(workflow);
    }

    /**
     * List all workflows.
     */
    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Validate workflow DAG structure.
     * Checks:
     * - All steps have unique IDs
     * - Dependencies reference existing steps
     * - No circular dependencies
     */
    private void validateWorkflowDefinition(JsonNode definition) {
        JsonNode stepsNode = definition.get("steps");
        if (stepsNode == null || !stepsNode.isArray()) {
            throw new IllegalArgumentException("Workflow definition must contain 'steps' array");
        }

        Set<String> stepIds = new HashSet<>();
        List<String> steps = new ArrayList<>();

        // Collect all step IDs
        for (JsonNode stepNode : stepsNode) {
            String stepId = stepNode.get("id").asText();
            if (stepIds.contains(stepId)) {
                throw new IllegalArgumentException("Duplicate step ID: " + stepId);
            }
            stepIds.add(stepId);
            steps.add(stepId);
        }

        // Validate dependencies
        for (JsonNode stepNode : stepsNode) {
            String stepId = stepNode.get("id").asText();
            JsonNode depsNode = stepNode.get("dependencies");
            if (depsNode != null && depsNode.isArray()) {
                for (JsonNode depNode : depsNode) {
                    String depId = depNode.asText();
                    if (!stepIds.contains(depId)) {
                        throw new IllegalArgumentException(
                            "Step " + stepId + " depends on non-existent step: " + depId
                        );
                    }
                }
            }
        }

        // Check for circular dependencies
        checkCircularDependencies(definition, stepIds);

        log.info("Workflow definition validated. Steps: {}", steps);
    }

    /**
     * Detect circular dependencies using DFS.
     */
    private void checkCircularDependencies(JsonNode definition, Set<String> stepIds) {
        JsonNode stepsNode = definition.get("steps");
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (JsonNode stepNode : stepsNode) {
            String stepId = stepNode.get("id").asText();
            if (!visited.contains(stepId)) {
                if (hasCycle(stepId, stepsNode, visited, recStack)) {
                    throw new IllegalArgumentException("Circular dependency detected in workflow");
                }
            }
        }
    }

    private boolean hasCycle(String stepId, JsonNode stepsNode, Set<String> visited, Set<String> recStack) {
        visited.add(stepId);
        recStack.add(stepId);

        JsonNode currentStep = null;
        for (JsonNode step : stepsNode) {
            if (step.get("id").asText().equals(stepId)) {
                currentStep = step;
                break;
            }
        }

        if (currentStep != null) {
            JsonNode depsNode = currentStep.get("dependencies");
            if (depsNode != null && depsNode.isArray()) {
                for (JsonNode depNode : depsNode) {
                    String depId = depNode.asText();
                    if (!visited.contains(depId)) {
                        if (hasCycle(depId, stepsNode, visited, recStack)) {
                            return true;
                        }
                    } else if (recStack.contains(depId)) {
                        return true;
                    }
                }
            }
        }

        recStack.remove(stepId);
        return false;
    }

    private WorkflowResponse mapToResponse(WorkflowEntity entity) {
        return WorkflowResponse.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .definition(entity.getDefinition())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
