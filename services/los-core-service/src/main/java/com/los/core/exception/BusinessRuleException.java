package com.los.core.exception;

import java.util.Map;

public class BusinessRuleException extends RuntimeException {

    private final String reason;
    private final String action;
    private final Map<String, Object> context;

    public BusinessRuleException(String message) {
        super(message);
        this.reason = null;
        this.action = null;
        this.context = null;
    }

    public BusinessRuleException(String message, String reason, String action, Map<String, Object> context) {
        super(message);
        this.reason = reason;
        this.action = action;
        this.context = context;
    }

    public String getReason() {
        return reason;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}
