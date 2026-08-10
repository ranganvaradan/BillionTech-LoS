package com.los.core.service.flow.step;

import java.util.Map;
import java.util.UUID;

/**
 * Pluggable, step-type–based execution (adapter around KYC, bureau, eSign, disburse, etc.).
 * No persistence: {@link com.los.core.service.loan.LoanApplicationFlowService} still owns status transitions.
 */
public interface IStepExecutor {

    /**
     * @param stepType a constant such as {@link FlowStepType#BUREAU_PULL}
     */
    boolean supports(String stepType);

    /**
     * @param context step-specific data (e.g. {@code kycPayload}, {@code signerInfo}); may be empty
     */
    StepResult execute(UUID applicationId, Map<String, Object> context);
}
