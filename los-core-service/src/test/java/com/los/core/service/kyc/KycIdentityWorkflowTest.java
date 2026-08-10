package com.los.core.service.kyc;

import com.los.core.model.enums.KycStepType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KycIdentityWorkflowTest {

    @Test
    void bureauIsNotKycIdentitySubStep() {
        assertFalse(KycIdentityWorkflow.isKycIdentitySubStep(KycStepType.BUREAU_PULL));
    }

    @Test
    void panIsKycIdentitySubStep() {
        assertTrue(KycIdentityWorkflow.isKycIdentitySubStep(KycStepType.PAN_VERIFY));
    }

    @Test
    void unknownStepNameIsTreatedAsEligibleForKycLoop() {
        assertTrue(KycIdentityWorkflow.isKycIdentitySubStepName("FUTURE_STEP_X"));
    }
}
