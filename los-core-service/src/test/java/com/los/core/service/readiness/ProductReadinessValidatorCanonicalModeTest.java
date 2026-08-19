package com.los.core.service.readiness;

import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductReadinessValidatorCanonicalModeTest {

    private final PolicyRequiredParameterExtractor extractor = new PolicyRequiredParameterExtractor();
    private final ProductReadinessValidator validator = new ProductReadinessValidator(extractor);

    @Test
    void canonicalMode_policyStudioSelected_allowsLiveRuleSetNull() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("WF")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .active(true)
                .bureauEnabled(true)
                .autoPullBureauAfterKycSuccess(true)
                .steps(java.util.List.of(Map.of("step", "PAN_VERIFY")))
                .build();

        UnderwritingScorecard sc = UnderwritingScorecard.builder()
                .id(UUID.randomUUID())
                .name("SC")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .active(true)
                .status("ACTIVE")
                .scorecardJson(Map.of())
                .hardRulesJson(Map.of())
                .thresholdsJson(Map.of())
                .build();

        Map<String, Object> studioRequired = Map.of("requiredParameterIds", java.util.List.of());

        Map<String, Object> result = validator.validate(
                "COMPANY",
                "TERM_LOAN",
                wf,
                null,
                sc,
                studioRequired,
                "00000000-0000-0000-0000-000000000001",
                "v1");

        assertThat(result.get("ready")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("READY");
        @SuppressWarnings("unchecked")
        java.util.List<String> gaps = (java.util.List<String>) result.get("gaps");
        assertThat(gaps).noneMatch(g -> String.valueOf(g).contains("No Live Rule Set"));
        @SuppressWarnings("unchecked")
        Map<String, Object> refs = (Map<String, Object>) result.get("references");
        assertThat(refs.get("policyStudioNotProductionAuthority")).isEqualTo(false);
    }
}

