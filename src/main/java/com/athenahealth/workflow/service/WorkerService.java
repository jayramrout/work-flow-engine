package com.athenahealth.workflow.service;

import com.athenahealth.workflow.config.RabbitMQConfig;
import com.athenahealth.workflow.messaging.WorkerTask;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Worker service that processes workflow tasks.
 * Listens to RabbitMQ queue, acquires distributed lock, executes step, and reports status.
 */
@Service
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkerService {

    private final ExecutionService executionService;
    private final StepExecutorService stepExecutorService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "step_lock:";
    private static final long LOCK_TIMEOUT_SECONDS = 30;

    /**
     * Process a workflow task from the queue.
     * This is called by RabbitMQ listener.
     */
    @RabbitListener(queues = RabbitMQConfig.TASK_QUEUE)
    public void processTask(WorkerTask task) {
        String lockKey = LOCK_PREFIX + task.getExecutionId() + ":" + task.getStepId();

        try {
            // Attempt to acquire distributed lock
            Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(LOCK_TIMEOUT_SECONDS));

            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.debug("Could not acquire lock for step. ExecutionId: {}, StepId: {}",
                    task.getExecutionId(), task.getStepId());
                // Don't retry - another worker has it
                return;
            }

            // Mark step as running
            int attemptCount = executionService.markStepRunning(task.getExecutionId(), task.getStepId());
            log.info("Acquired lock for step. ExecutionId: {}, StepId: {}, Attempt: {}",
                task.getExecutionId(), task.getStepId(), attemptCount);

            // Execute the step
            try {
                JsonNode result = stepExecutorService.executeStep(task.getStepType(), null);
                executionService.markStepCompleted(task.getExecutionId(), task.getStepId(), result);
                log.info("Step completed successfully. ExecutionId: {}, StepId: {}",
                    task.getExecutionId(), task.getStepId());
            } catch (Exception e) {
                log.error("Step execution failed. ExecutionId: {}, StepId: {}, Error: {}",
                    task.getExecutionId(), task.getStepId(), e.getMessage(), e);
                executionService.markStepFailed(task.getExecutionId(), task.getStepId(), e.getMessage());
            }

        } catch (Exception e) {
            log.error("Unexpected error processing task. ExecutionId: {}, StepId: {}",
                task.getExecutionId(), task.getStepId(), e);
        } finally {
            // Release lock
            redisTemplate.delete(lockKey);
        }
    }

}
