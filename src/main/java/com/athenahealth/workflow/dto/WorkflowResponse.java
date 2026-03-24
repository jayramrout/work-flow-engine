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
public class WorkflowResponse {
    private Long id;
    private String name;
    private String description;
    private JsonNode definition;
    private Instant createdAt;
    private Instant updatedAt;
}

