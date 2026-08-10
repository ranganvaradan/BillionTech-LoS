package com.los.core.service.kyc;

import com.los.core.model.enums.StepOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KycMandatoryGroupEvaluatorTest {

    @Test
    void anyGroupPassesWhenOneStepSucceeds() {
        Map<String, Object> intakeConfig = Map.of(
                "mandatoryFieldGroups", List.of(Map.of(
                        "logic", "ANY",
                        "steps", List.of("AADHAAR_OTP", "VOTER_ID_VERIFY", "DL_VERIFY"))));
        List<Map<String, Object>> steps = List.of(
                Map.of("step", "AADHAAR_OTP", "mandatory", true),
                Map.of("step", "VOTER_ID_VERIFY", "mandatory", true),
                Map.of("step", "DL_VERIFY", "mandatory", true));

        Map<String, StepOutcome> outcomes = Map.of(
                "AADHAAR_OTP", StepOutcome.FAILURE,
                "VOTER_ID_VERIFY", StepOutcome.SUCCESS,
                "DL_VERIFY", StepOutcome.FAILURE);

        assertThat(KycMandatoryGroupEvaluator.hasMandatoryFailure(outcomes, steps, intakeConfig)).isFalse();
        assertThat(KycMandatoryGroupEvaluator.shouldHaltOnMandatoryFailure("AADHAAR_OTP", intakeConfig)).isFalse();
    }

    @Test
    void anyGroupFailsWhenAllMembersFail() {
        Map<String, Object> intakeConfig = Map.of(
                "mandatoryFieldGroups", List.of(Map.of(
                        "logic", "ANY",
                        "steps", List.of("AADHAAR_OTP", "VOTER_ID_VERIFY"))));
        List<Map<String, Object>> steps = List.of(
                Map.of("step", "AADHAAR_OTP", "mandatory", true),
                Map.of("step", "VOTER_ID_VERIFY", "mandatory", true));

        Map<String, StepOutcome> outcomes = Map.of(
                "AADHAAR_OTP", StepOutcome.FAILURE,
                "VOTER_ID_VERIFY", StepOutcome.FAILURE);

        assertThat(KycMandatoryGroupEvaluator.hasMandatoryFailure(outcomes, steps, intakeConfig)).isTrue();
    }

    @Test
    void mandatoryVideoKycDoesNotFailIdentityKycWhenPanAndVoterSucceed() {
        List<Map<String, Object>> steps = List.of(
                Map.of("step", "PAN_VERIFY", "mandatory", true),
                Map.of("step", "VOTER_ID_VERIFY", "mandatory", true),
                Map.of("step", "VIDEO_KYC", "mandatory", true));

        Map<String, StepOutcome> outcomes = Map.of(
                "PAN_VERIFY", StepOutcome.SUCCESS,
                "VOTER_ID_VERIFY", StepOutcome.SUCCESS);

        assertThat(KycMandatoryGroupEvaluator.hasMandatoryFailure(outcomes, steps, Map.of())).isFalse();
    }

    @Test
    void vkycAliasIsExcludedFromMandatoryIdentityFailure() {
        List<Map<String, Object>> steps = List.of(
                Map.of("step", "PAN_VERIFY", "mandatory", true),
                Map.of("step", "VKYC", "mandatory", true));

        Map<String, StepOutcome> outcomes = Map.of("PAN_VERIFY", StepOutcome.SUCCESS);

        assertThat(KycMandatoryGroupEvaluator.hasMandatoryFailure(outcomes, steps, Map.of())).isFalse();
        assertThat(KycIdentityWorkflow.isKycIdentitySubStepName("VKYC")).isFalse();
        assertThat(KycIdentityWorkflow.isKycIdentitySubStepName("VIDEO_KYC")).isFalse();
    }
}
