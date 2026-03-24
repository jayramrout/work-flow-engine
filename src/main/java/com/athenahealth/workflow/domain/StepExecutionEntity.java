package com.athenahealth.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents the execution state of a single step within a workflow execution.
 * Tracks all attempts, errors, and status transitions for that step.
 */
@Entity
@Table(name = "step_executions", indexes = {
    @Index(name = "idx_se_execution_id", columnList = "execution_id"),
    @Index(name = "idx_se_step_id",      columnList = "step_id"),
    @Index(name = "idx_se_status",       columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;  // WorkflowExecutionEntity.id

    @Column(nullable = false)
    private String stepId;  // ID from workflow definition

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false)
    private Integer maxRetries;

    /**
     * Execution history for this step:
     * [
     *   {
     *     "attempt": 1,
     *     "status": "COMPLETED",
     *     "startedAt": "2026-03-21T...",
     *     "completedAt": "2026-03-21T...",
     *     "result": {...}
     *   },
     *   {
     *     "attempt": 2,
     *     "status": "FAILED",
     *     "startedAt": "2026-03-21T...",
     *     "completedAt": "2026-03-21T...",
     *     "error": "Connection timeout"
     *   }
     * ]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode executionHistory;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = StepStatus.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
