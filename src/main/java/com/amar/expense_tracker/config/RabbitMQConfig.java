package com.amar.expense_tracker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String STATEMENT_PROCESSING_EXCHANGE = "statement.processing.exchange";
    public static final String STATEMENT_PROCESSING_QUEUE = "statement.processing.queue";
    public static final String STATEMENT_PROCESSING_ROUTING_KEY = "statement.processing";

    @Bean
    public Queue statementProcessingQueue() {
        return QueueBuilder.durable(STATEMENT_PROCESSING_QUEUE).build();
    }

    @Bean
    public DirectExchange statementProcessingExchange() {
        return new DirectExchange(STATEMENT_PROCESSING_EXCHANGE);
    }

    @Bean
    public Binding statementProcessingBinding(Queue statementProcessingQueue,
                                               DirectExchange statementProcessingExchange) {
        return BindingBuilder.bind(statementProcessingQueue)
                .to(statementProcessingExchange)
                .with(STATEMENT_PROCESSING_ROUTING_KEY);
    }

    // Spring Boot's autoconfigured RabbitTemplate picks up any MessageConverter bean
    // present in the context, so no need to build the template ourselves.
    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
