package com.athenahealth.workflow.controller;

import com.athenahealth.workflow.dto.WorkflowRequest;
import com.athenahealth.workflow.dto.WorkflowResponse;
import com.athenahealth.workflow.service.ExecutionService;
import com.athenahealth.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkflowController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
class WorkflowControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private ExecutionService executionService;


    @Test
    void createWorkflow_returnsCreatedWhenRequestIsValid() throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .name("controller-test")
                .description("controller integration test")
                .definition(objectMapper.readTree("""
                        {
                          "steps": [
                            {"id": "s1", "type": "fetch_data", "dependencies": []}
                          ]
                        }
                        """))
                .build();

        WorkflowResponse response = WorkflowResponse.builder()
                .id(1L)
                .name("controller-test")
                .description("controller integration test")
                .definition(request.getDefinition())
                .build();

        when(workflowService.createWorkflow(any(WorkflowRequest.class))).thenReturn(response);

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("controller-test"));
    }

    @Test
    void createWorkflow_returnsBadRequestWhenValidationFails() throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .name("bad")
                .description("bad workflow")
                .definition(objectMapper.readTree("""
                        {
                          "steps": [
                            {"id": "s1", "type": "fetch_data", "dependencies": ["missing"]}
                          ]
                        }
                        """))
                .build();

        when(workflowService.createWorkflow(any(WorkflowRequest.class)))
                .thenThrow(new IllegalArgumentException("invalid DAG"));

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

