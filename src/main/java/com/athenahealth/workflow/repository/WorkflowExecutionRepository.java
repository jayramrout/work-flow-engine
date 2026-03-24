package com.athenahealth.workflow.repository;

import com.athenahealth.workflow.domain.WorkflowExecutionEntity;
import com.athenahealth.workflow.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {
    List<WorkflowExecutionEntity> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
    List<WorkflowExecutionEntity> findByStatus(WorkflowStatus status);
    List<WorkflowExecutionEntity> findByWorkflowIdAndStatus(Long workflowId, WorkflowStatus status);
}
