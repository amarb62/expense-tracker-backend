package com.amar.expense_tracker.statement.service;

import com.amar.expense_tracker.config.RabbitMQConfig;
import com.amar.expense_tracker.statement.event.StatementUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatementEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUploaded(StatementUploadedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.STATEMENT_PROCESSING_EXCHANGE,
                RabbitMQConfig.STATEMENT_PROCESSING_ROUTING_KEY,
                event);
    }
}
