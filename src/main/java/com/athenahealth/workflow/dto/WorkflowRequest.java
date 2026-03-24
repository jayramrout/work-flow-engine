package com.athenahealth.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRequest {
    private String name;
    private String description;
    private JsonNode definition;  // The DAG definition
}

