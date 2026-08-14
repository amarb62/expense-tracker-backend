package com.amar.expense_tracker.statement.mapper;

import com.amar.expense_tracker.account.dto.AccountType;
import com.amar.expense_tracker.common.util.JpaDateUtils;
import com.amar.expense_tracker.entity.Statements;
import com.amar.expense_tracker.statement.dto.StatementResponse;
import com.amar.expense_tracker.statement.dto.StatementStatus;
import org.springframework.stereotype.Component;

@Component
public class StatementMapper {

    public StatementResponse toResponse(Statements statement) {
        return new StatementResponse(
                statement.getId(),
                statement.getAccounts().getId(),
                statement.getFileName(),
                AccountType.valueOf(statement.getStatementType()),
                JpaDateUtils.toLocalDate(statement.getStatementStartDate()),
                JpaDateUtils.toLocalDate(statement.getStatementEndDate()),
                StatementStatus.valueOf(statement.getStatus()),
                statement.getUploadedAt().toInstant(),
                statement.getProcessedAt() != null ? statement.getProcessedAt().toInstant() : null,
                statement.getErrorMessage()
        );
    }
}
