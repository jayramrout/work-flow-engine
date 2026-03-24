package com.athenahealth.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecutionResponse {
    private Long id;
    private Long workflowId;
    private String status;  // WorkflowStatus
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private List<StepExecutionResponse> steps;
}

