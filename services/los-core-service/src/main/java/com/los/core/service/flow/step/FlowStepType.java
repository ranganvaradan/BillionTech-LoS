package com.los.core.service.flow.step;

/**
 * String keys for {@link IStepExecutor#supports(String)}. Kept in one place for flow and tests.
 */
public final class FlowStepType {

    public static final String KYC_WORKFLOW = "KYC_WORKFLOW";
    public static final String VKYC = "VKYC";
    public static final String BUREAU_PULL = "BUREAU_PULL";
    public static final String ESIGN = "ESIGN";
    public static final String DISBURSE = "DISBURSE";

    private FlowStepType() {
    }
}
