package com.athenahealth.workflow.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for workflow task distribution.
 * Defines queues, exchanges, and bindings for worker task distribution.
 */
@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String TASK_QUEUE = "workflow.tasks";
    public static final String TASK_EXCHANGE = "workflow.exchange";
    public static final String TASK_ROUTING_KEY = "workflow.task";

    // Dead letter queue for failed tasks
    public static final String DLQ_QUEUE = "workflow.tasks.dlq";
    public static final String DLQ_EXCHANGE = "workflow.exchange.dlq";
    public static final String DLQ_ROUTING_KEY = "workflow.task.dlq";

    /**
     * JSON message converter — ensures messages are serialized/deserialized as JSON
     * rather than using Java serialization (which causes EOFException mismatches).
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate wired with the JSON converter so all outbound messages are JSON.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Listener container factory wired with the JSON converter so inbound messages
     * are deserialized from JSON — prevents "Error unmarshaling return header / EOFException".
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }

    /**
     * Main task queue - workers poll this for executable steps.
     */
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
            .build();
    }

    /**
     * Dead letter queue for tracking failed task deliveries.
     */
    @Bean
    public Queue dlQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    /**
     * Direct exchange for task routing.
     */
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    /**
     * Dead letter exchange.
     */
    @Bean
    public DirectExchange dlExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    /**
     * Binding: taskQueue -> taskExchange -> TASK_ROUTING_KEY
     */
    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(taskQueue)
            .to(taskExchange)
            .with(TASK_ROUTING_KEY);
    }

    /**
     * Binding: dlQueue -> dlExchange -> DLQ_ROUTING_KEY
     */
    @Bean
    public Binding dlBinding(Queue dlQueue, DirectExchange dlExchange) {
        return BindingBuilder.bind(dlQueue)
            .to(dlExchange)
            .with(DLQ_ROUTING_KEY);
    }
}
