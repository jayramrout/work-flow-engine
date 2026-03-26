package com.athenahealth.workflow.e2e;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only bean overrides that eliminate external broker connections.
 *
 * <p>The application's {@code RabbitMQConfig} registers its own
 * {@code rabbitListenerContainerFactory} bean, which means Spring Boot's
 * {@code spring.rabbitmq.listener.simple.auto-startup} property has no effect
 * on it. This configuration replaces that factory with one that has
 * {@code autoStartup=false}, so the {@code @RabbitListener} container on
 * {@code WorkerService} never tries to open a TCP connection to a broker.
 *
 * <p>Tasks are delivered to {@code WorkerService#processTask} directly via
 * the {@code @MockBean RabbitTemplate} wired in {@link WorkflowEndToEndTest}.
 */
@TestConfiguration
class InMemoryInfraConfig {

    /**
     * Overrides {@code RabbitMQConfig.rabbitListenerContainerFactory()}.
     *
     * <p>The only change from the application's version is
     * {@code factory.setAutoStartup(false)}, which prevents the
     * {@code SimpleMessageListenerContainer} from starting during context
     * refresh and therefore prevents any attempt to connect to a RabbitMQ broker.
     *
     * <p>Requires {@code spring.main.allow-bean-definition-overriding=true}
     * (set in {@link WorkflowEndToEndTest}'s {@code @TestPropertySource}).
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAutoStartup(false);   // ← the entire point of this override
        return factory;
    }
}

