package com.athenahealth.workflow.controller;

import com.athenahealth.workflow.dto.WorkflowExecutionResponse;
import com.athenahealth.workflow.dto.WorkflowRequest;
import com.athenahealth.workflow.dto.WorkflowResponse;
import com.athenahealth.workflow.service.ExecutionService;
import com.athenahealth.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API endpoints for workflow management and execution.
 */
@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflows", description = "Workflow definition management and execution")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    /**
     * Create a new workflow definition.
     * POST /workflows
     */
    @Operation(summary = "Create a workflow", description = "Creates a new workflow definition from a DAG of steps with retry configuration.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workflow created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkflowResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid workflow definition", content = @Content)
    })
    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@RequestBody WorkflowRequest request) {
        try {
            WorkflowResponse response = workflowService.createWorkflow(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get workflow by ID.
     * GET /workflows/{id}
     */
    @Operation(summary = "Get workflow by ID", description = "Returns a single workflow definition.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkflowResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workflow not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(
            @Parameter(description = "Workflow ID", required = true, example = "1") @PathVariable Long id) {
        try {
            return ResponseEntity.ok(workflowService.getWorkflow(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get workflow by name.
     * GET /workflows/by-name/{name}
     */
    @Operation(summary = "Get workflow by name", description = "Returns a workflow definition looked up by its unique name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkflowResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workflow not found", content = @Content)
    })
    @GetMapping("/by-name/{name}")
    public ResponseEntity<WorkflowResponse> getWorkflowByName(
            @Parameter(description = "Workflow name", required = true, example = "sequential-pipeline") @PathVariable String name) {
        try {
            return ResponseEntity.ok(workflowService.getWorkflowByName(name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all workflows.
     * GET /workflows
     */
    @Operation(summary = "List all workflows", description = "Returns all workflow definitions registered in the system.")
    @ApiResponse(responseCode = "200", description = "List of workflows",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = WorkflowResponse.class))))
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listWorkflows());
    }

    /**
     * Submit a new execution of a workflow.
     * POST /workflows/{id}/executions
     */
    @Operation(summary = "Submit a workflow execution",
            description = "Triggers a new execution of the specified workflow. Steps are dispatched to workers based on their DAG dependencies.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Execution submitted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkflowExecutionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Workflow not found or invalid", content = @Content)
    })
    @PostMapping("/{id}/executions")
    public ResponseEntity<WorkflowExecutionResponse> submitWorkflow(
            @Parameter(description = "Workflow ID to execute", required = true, example = "1") @PathVariable Long id) {
        try {
            WorkflowExecutionResponse response = executionService.submitWorkflow(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get execution status.
     * GET /executions/{executionId}
     */
    @Operation(summary = "Get execution status",
            description = "Returns the current status of a workflow execution including all step statuses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Execution found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkflowExecutionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Execution not found", content = @Content)
    })
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<WorkflowExecutionResponse> getExecutionStatus(
            @Parameter(description = "Execution ID", required = true, example = "1") @PathVariable Long executionId) {
        try {
            return ResponseEntity.ok(executionService.getExecutionStatus(executionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get next executable steps for an execution.
     * GET /executions/{executionId}/next-steps
     */
    @Operation(summary = "Get next executable steps",
            description = "Returns the step IDs that are ready to execute — i.e. all their dependencies have completed successfully.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of ready step IDs",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "string", example = "process-a")))),
            @ApiResponse(responseCode = "404", description = "Execution not found", content = @Content)
    })
    @GetMapping("/executions/{executionId}/next-steps")
    public ResponseEntity<List<String>> getNextSteps(
            @Parameter(description = "Execution ID", required = true, example = "1") @PathVariable Long executionId) {
        try {
            List<String> steps = executionService.getNextExecutableSteps(executionId);
            return ResponseEntity.ok(steps);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Global error handler for validation exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
