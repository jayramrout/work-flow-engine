package com.athenahealth.workflow.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message published to task queue when a step is ready for execution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long executionId;
    private String stepId;
    private String stepType;


    /**
     * Retry configuration for backoff calculation.
     */
    private RetryConfig retryConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetryConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer maxAttempts;
        private Long initialDelayMs;
        private Double backoffMultiplier;
    }
}
