package com.billiontech.bankstatement.exception;

public class StatementProcessingException extends RuntimeException {
    public StatementProcessingException(String message) {
        super(message);
    }

    public StatementProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
