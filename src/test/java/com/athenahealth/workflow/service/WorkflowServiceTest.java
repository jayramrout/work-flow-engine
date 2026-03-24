package com.athenahealth.workflow.service;

import com.athenahealth.workflow.domain.WorkflowEntity;
import com.athenahealth.workflow.dto.WorkflowRequest;
import com.athenahealth.workflow.dto.WorkflowResponse;
import com.athenahealth.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createWorkflow_validDefinition_persistsAndReturnsResponse() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {
                  "steps": [
                    {"id": "fetch", "type": "fetch_data", "dependencies": []},
                    {"id": "process", "type": "process_data", "dependencies": ["fetch"]}
                  ]
                }
                """);

        WorkflowRequest request = WorkflowRequest.builder()
                .name("valid-workflow")
                .description("Valid DAG")
                .definition(definition)
                .build();

        when(workflowRepository.save(any(WorkflowEntity.class))).thenAnswer(invocation -> {
            WorkflowEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            entity.setCreatedAt(Instant.parse("2026-03-24T00:00:00Z"));
            entity.setUpdatedAt(Instant.parse("2026-03-24T00:00:01Z"));
            return entity;
        });

        WorkflowResponse response = workflowService.createWorkflow(request);

        assertEquals(101L, response.getId());
        assertEquals("valid-workflow", response.getName());
        assertEquals(definition, response.getDefinition());

        ArgumentCaptor<WorkflowEntity> captor = ArgumentCaptor.forClass(WorkflowEntity.class);
        verify(workflowRepository).save(captor.capture());
        assertEquals("valid-workflow", captor.getValue().getName());
        assertEquals("Valid DAG", captor.getValue().getDescription());
        assertEquals(definition, captor.getValue().getDefinition());
    }

    @Test
    void createWorkflow_missingDependency_throwsAndDoesNotPersist() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {
                  "steps": [
                    {"id": "process", "type": "process_data", "dependencies": ["fetch"]}
                  ]
                }
                """);

        WorkflowRequest request = WorkflowRequest.builder()
                .name("invalid-workflow")
                .description("Invalid DAG")
                .definition(definition)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> workflowService.createWorkflow(request));

        assertTrue(ex.getMessage().contains("depends on non-existent step"));
        verify(workflowRepository, never()).save(any(WorkflowEntity.class));
    }
}

