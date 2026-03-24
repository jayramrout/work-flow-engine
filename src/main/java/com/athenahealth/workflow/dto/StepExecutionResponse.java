package com.athenahealth.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepExecutionResponse {
    private Long id;
    private String stepId;
    private String status;  // StepStatus
    private Integer attemptCount;
    private Integer maxRetries;
    private String lastError;
    private JsonNode executionHistory;
    private Instant createdAt;
    private Instant completedAt;
}

