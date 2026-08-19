package com.los.core.creditintelligence.policystudio.runtime.canonicallive;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluation;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluationService;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalLiveUnderwritingServiceTest {

    private CanonicalLiveUnderwritingService service;
    private CanonicalObservationalEvaluationService observational;
    private CreditIntelligenceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getCutover().setAllowCanonicalAuthority(true);
        observational = mock(CanonicalObservationalEvaluationService.class);
        service = new CanonicalLiveUnderwritingService(properties, observational);
    }

    @Test
    void authoritativeOnlyWhenCategoryAndFlag() {
        LoanApplication withCategory = LoanApplication.builder()
                .id(UUID.randomUUID())
                .selectedCustomerCategoryId(UUID.randomUUID())
                .loanProduct("TERM_LOAN")
                .build();
        LoanApplication withoutCategory = LoanApplication.builder()
                .id(UUID.randomUUID())
                .loanProduct("TERM_LOAN")
                .build();
        assertThat(service.isAuthoritativeFor(withCategory)).isTrue();
        assertThat(service.isAuthoritativeFor(withoutCategory)).isFalse();
        properties.getCutover().setAllowCanonicalAuthority(false);
        assertThat(service.isAuthoritativeFor(withCategory)).isFalse();
    }

    @Test
    void failClosedWhenFreezeNotExecutable() {
        UUID appId = UUID.randomUUID();
        when(observational.evaluate(appId)).thenReturn(CanonicalObservationalEvaluation.notExecutable(
                null, "unresolved", null,
                List.of("FROZEN_PACKAGE_NOT_RESOLVED"),
                List.of(), List.of(), Map.of(), null));
        assertThatThrownBy(() -> service.evaluateAuthoritative(appId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Canonical underwriting cannot execute");
    }

    @Test
    void mapsDataInsufficientWithoutLegacyFallback() {
        UUID appId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        CanonicalApplicationConfiguration freeze = new CanonicalApplicationConfiguration(
                appId, UUID.randomUUID(), 1, "CAT", UUID.randomUUID(), 1,
                UUID.randomUUID(), policyId, 1, "v1",
                UUID.randomUUID(), 1, true, false, UUID.randomUUID(),
                "EQUIFAX", null, null, LocalDate.of(2026, 8, 19), List.of(), null, Map.of());
        CanonicalPolicyResult policy = new CanonicalPolicyResult(
                policyId.toString(), "1", "gacat", LocalDate.of(2026, 8, 19),
                CanonicalPolicyResult.OverallOutcome.DATA_INSUFFICIENT,
                List.of(), List.of(), List.of("r1"), List.of(), Map.of(), Map.of());
        Map<String, Object> scorecard = new java.util.LinkedHashMap<>();
        scorecard.put("scorecardId", freeze.scorecardId().toString());
        scorecard.put("scorecardVersion", 1);
        scorecard.put("bandOutcome", "DATA_INSUFFICIENT");
        scorecard.put("numericalScore", 42);
        CanonicalObservationalEvaluation ev = new CanonicalObservationalEvaluation(
                "COMPLETED", List.of(), freeze, "hash", UUID.randomUUID(), null, policy,
                List.of(), List.of(Map.of("ruleId", "R1", "participates", true, "result", "DATA_INSUFFICIENT")),
                scorecard,
                new PolicyScorecardPrecedence.PrecedenceResult(
                        FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT,
                        List.of("POLICY_INSUFFICIENT_OR_REFER"), "POLICY_THEN_SCORECARD"),
                "DATA_INSUFFICIENT",
                CanonicalObservationalEvaluation.zeroLookups());
        when(observational.evaluate(appId)).thenReturn(ev);

        CanonicalLiveUnderwritingService.LiveOutcome live = service.evaluateAuthoritative(appId);
        assertThat(live.aggregateCreditDecision()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(live.policyRecommendation()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(live.riskScore()).isEqualTo(42);
        MultiRuleEvalResult multi = live.multi();
        assertThat(multi.aggregateCreditDecision()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(multi.hasAnyRule()).isTrue();
    }
}
