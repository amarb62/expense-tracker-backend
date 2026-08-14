package com.amar.expense_tracker.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends ApiException {

    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
