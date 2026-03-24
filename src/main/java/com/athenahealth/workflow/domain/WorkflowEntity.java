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
 * Represents a workflow definition stored persistently.
 * The definition is stored as a JSON DAG that describes the workflow structure.
 */
@Entity
@Table(name = "workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * JSON representation of the DAG:
     * {
     *   "steps": [
     *     {
     *       "id": "step-a",
     *       "type": "fetch_data",
     *       "input": {...},
     *       "dependencies": [],
     *       "retryConfig": {
     *         "maxAttempts": 3,
     *         "initialDelayMs": 1000,
     *         "backoffMultiplier": 2.0
     *       }
     *     },
     *     ...
     *   ]
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode definition;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
