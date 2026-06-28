package com.los.core.model.dto.request;

import lombok.Data;

@Data
public class ManualProcessOverrideRequest {
    private String processCode;
    private String failureCode;
    private String overrideReason;
    private String remarks;
    private String approvalReference;
    /** Required for underwriting rejection overrides — updated bureau score used in credit assessment. */
    private Integer manualBureauScore;
    /** Optional aggregate risk score; defaults to {@link #manualBureauScore} when omitted. */
    private Integer creditRiskScore;
}
