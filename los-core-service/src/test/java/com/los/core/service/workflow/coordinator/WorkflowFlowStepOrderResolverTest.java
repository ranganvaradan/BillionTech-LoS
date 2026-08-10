package com.los.core.service.workflow.coordinator;

import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.flow.step.FlowStepType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowFlowStepOrderResolverTest {

    @Test
    void v2TermLoanOrder_kycThenBureau() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("Individual - Term Loan")
                .borrowerType("INDIVIDUAL")
                .loanProduct("TERM_LOAN")
                .steps(List.of(
                        Map.of("step", "MOBILE_OTP", "order", 1),
                        Map.of("step", "BUREAU_PULL", "order", 6)
                ))
                .active(true)
                .version(1)
                .build();
        List<String> order = WorkflowFlowStepOrderResolver.resolve(c, WorkflowResolutionOptions.defaults());
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL), order);
    }

    @Test
    void v11PersonalLoanNoBureauRow_addsDefaultBureau() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("x")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "order", 1),
                        Map.of("step", "AADHAAR_OTP", "order", 2)
                ))
                .active(true)
                .version(1)
                .build();
        List<String> order = WorkflowFlowStepOrderResolver.resolve(c, WorkflowResolutionOptions.defaults());
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL), order);
    }

    @Test
    void emptySteps_usesFallback() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("empty")
                .borrowerType("X")
                .loanProduct("Y")
                .steps(List.of())
                .active(true)
                .version(1)
                .build();
        List<String> order = WorkflowFlowStepOrderResolver.resolve(c, WorkflowResolutionOptions.defaults());
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL), order);
    }

    @Test
    void whenBureauOmittedFromConfigAndAddBureauFalse_onlyKycWorkflow() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("n")
                .borrowerType("X")
                .loanProduct("Y")
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "order", 1)
                ))
                .active(true)
                .version(1)
                .build();
        var o = new WorkflowResolutionOptions(false, List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL));
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW), WorkflowFlowStepOrderResolver.resolve(c, o));
    }

    @Test
    void unknownStepKeyInRow_ignoredForFlowOrder_kycBlockStillFirst() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("n")
                .borrowerType("X")
                .loanProduct("Y")
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "order", 1),
                        Map.of("step", "FUTURE_STEP_X", "order", 2)
                ))
                .active(true)
                .version(1)
                .build();
        List<String> order = WorkflowFlowStepOrderResolver.resolve(c, WorkflowResolutionOptions.defaults());
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL), order);
    }

    @Test
    void bureauDisabled_removesBureauStepFromResolvedOrder() {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("no bureau")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .bureauEnabled(false)
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY", "order", 1),
                        Map.of("step", "BUREAU_PULL", "order", 2)))
                .active(true)
                .version(1)
                .build();
        assertEquals(List.of(FlowStepType.KYC_WORKFLOW), WorkflowFlowStepOrderResolver.resolve(c, WorkflowResolutionOptions.defaults()));
    }
}
