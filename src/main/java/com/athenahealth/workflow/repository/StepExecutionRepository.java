package com.athenahealth.workflow.repository;

import com.athenahealth.workflow.domain.StepExecutionEntity;
import com.athenahealth.workflow.domain.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, Long> {
    List<StepExecutionEntity> findByExecutionId(Long executionId);
    Optional<StepExecutionEntity> findByExecutionIdAndStepId(Long executionId, String stepId);
    List<StepExecutionEntity> findByExecutionIdAndStatus(Long executionId, StepStatus status);
    List<StepExecutionEntity> findByStatusAndExecutionIdIn(StepStatus status, List<Long> executionIds);
}
