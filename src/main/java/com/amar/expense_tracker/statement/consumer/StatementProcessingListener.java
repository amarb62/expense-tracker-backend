package com.amar.expense_tracker.statement.consumer;

import com.amar.expense_tracker.config.RabbitMQConfig;
import com.amar.expense_tracker.statement.event.StatementUploadedEvent;
import com.amar.expense_tracker.statement.service.StatementProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatementProcessingListener {

    private final StatementProcessingService statementProcessingService;

    @RabbitListener(queues = RabbitMQConfig.STATEMENT_PROCESSING_QUEUE)
    public void onStatementUploaded(StatementUploadedEvent event) {
        statementProcessingService.process(event);
    }
}
