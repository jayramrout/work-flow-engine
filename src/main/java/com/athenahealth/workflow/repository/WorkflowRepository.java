package com.athenahealth.workflow.repository;

import com.athenahealth.workflow.domain.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, Long> {
    Optional<WorkflowEntity> findByName(String name);
}
