package com.amar.expense_tracker.auth.exception;

import com.amar.expense_tracker.common.exception.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
