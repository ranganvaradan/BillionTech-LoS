package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.domain.PolicyDifferenceClass;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.service.DualPolicyEvaluator;
import com.los.core.creditintelligence.validation.service.ValidationBundleLoader;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DualPolicyEvaluatorTest {

    @Test
    void caseB_showsLegacyDefaultDependent() {
        var bundle = new ValidationBundleLoader().load(ValidationCaseCode.CASE_B_LEGACY_DEFAULT);
        var result = new DualPolicyEvaluator().evaluate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), bundle);
        assertThat(result.comparisons()).anyMatch(c ->
                PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT.name().equals(c.getDifferenceClass()));
        assertThat(result.summary().get("authoritative")).isEqualTo(false);
    }

    @Test
    void caseA_mostlyMatch() {
        var bundle = new ValidationBundleLoader().load(ValidationCaseCode.CASE_A_STRONG);
        var result = new DualPolicyEvaluator().evaluate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), bundle);
        assertThat(result.comparisons()).isNotEmpty();
        assertThat(result.comparisons()).anyMatch(c ->
                PolicyDifferenceClass.MATCH.name().equals(c.getDifferenceClass()));
    }

    @Test
    void validationFlagDefaultFalse() {
        assertThat(new CreditIntelligenceProperties().getValidation().isEnabled()).isFalse();
    }
}
