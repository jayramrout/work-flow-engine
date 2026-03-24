package com.athenahealth.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {
    private boolean enabled = true;
}