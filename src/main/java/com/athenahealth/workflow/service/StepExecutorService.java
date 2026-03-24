package com.athenahealth.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service that executes the actual business logic of workflow steps.
 * In a real system, this would dispatch to various handlers based on step type.
 * For this MVP, we'll simulate step execution.
 */
@Service
@Slf4j
public class StepExecutorService {

    /**
     * Execute a step based on its type.
     * This is a mock implementation that simulates step execution.
     * In production, this would dispatch to appropriate handlers.
     */
    public JsonNode executeStep(String stepType, JsonNode input) {
        log.info("Executing step of type: {}", stepType);

        // Simulate different step types
        switch (stepType) {
            case "fetch_data":
                return simulateFetchData(input);
            case "process_data":
                return simulateProcessData(input);
            case "validate_data":
                return simulateValidateData(input);
            case "aggregate":
                return simulateAggregate(input);
            case "publish":
                return simulatePublish(input);
            default:
                return simulateGenericStep(input, stepType);
        }
    }

    private JsonNode simulateFetchData(JsonNode input) {
        // Simulate fetching data
        try {
            Thread.sleep(100);  // Simulate work
            log.debug("Fetched data successfully");
            return input;  // Echo input as output
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fetch interrupted", e);
        }
    }

    private JsonNode simulateProcessData(JsonNode input) {
        try {
            Thread.sleep(150);
            log.debug("Processed data successfully");
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Process interrupted", e);
        }
    }

    private JsonNode simulateValidateData(JsonNode input) {
        try {
            Thread.sleep(100);
            log.debug("Validated data successfully");
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Validate interrupted", e);
        }
    }

    private JsonNode simulateAggregate(JsonNode input) {
        try {
            Thread.sleep(200);
            log.debug("Aggregated results successfully");
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Aggregate interrupted", e);
        }
    }

    private JsonNode simulatePublish(JsonNode input) {
        try {
            Thread.sleep(100);
            log.debug("Published results successfully");
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Publish interrupted", e);
        }
    }

    private JsonNode simulateGenericStep(JsonNode input, String stepType) {
        try {
            Thread.sleep(100);
            log.debug("Executed step type: {}", stepType);
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Step execution interrupted", e);
        }
    }
}
