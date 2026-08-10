package com.los.core.model.enums;

public enum VkycStatus {
    NOT_STARTED,
    /** Backward-compatible alias for old rows. */
    PENDING,
    URL_GENERATED,
    INITIATED,
    CUSTOMER_JOINED,
    AGENT_APPROVED,
    AUDITOR_APPROVED,
    AUDITOR_REJECTED,
    COMPLETED,
    AUTO_DECLINED,
    ERROR,
    REJECTED,
    EXPIRED,
    FAILED
}
