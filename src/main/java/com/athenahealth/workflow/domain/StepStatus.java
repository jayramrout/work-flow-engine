package com.athenahealth.workflow.domain;

public enum StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == RUNNING || this == RETRYING;
    }
}
