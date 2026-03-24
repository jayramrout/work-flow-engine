package com.athenahealth.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {
    private boolean enabled = true;
    private long queuePollIntervalMs = 1000L;
    private int maxRetryAttempts = 3;
    private double retryBackoffMultiplier = 2.0;
    private long initialRetryDelayMs = 1000L;
}

