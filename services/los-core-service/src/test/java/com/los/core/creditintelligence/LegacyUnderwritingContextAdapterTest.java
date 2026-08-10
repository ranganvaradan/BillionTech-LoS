package com.los.core.creditintelligence;

import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.LegacyUnderwritingContextAdapter;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyUnderwritingContextAdapterTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiUnderwritingFactRepository factRepository;
    @Mock
    private com.los.core.creditintelligence.core.repository.CiMetricResultRepository metricResultRepository;
    @Mock
    private com.los.core.creditintelligence.evaluation.PinnedMetricLookup pinnedMetricLookup;

    private LegacyUnderwritingContextAdapter adapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        adapter = new LegacyUnderwritingContextAdapter(
                snapshotRepository, factRepository, metricResultRepository,
                new com.los.core.creditintelligence.config.CreditIntelligenceProperties(),
                pinnedMetricLookup);
    }

    @Test
    void reconstructsScorecardFromCompatFacts() {
        UUID snapshotId = UUID.randomUUID();
        CiFactSnapshot snapshot = CiFactSnapshot.builder()
                .id(snapshotId)
                .status(SnapshotStatus.FROZEN.name())
                .metadata(Map.of(
                        "bureauSource", "PROVIDER",
                        "incomeSource", "PROVIDER",
                        "kycSource", "PROVIDER",
                        "kycOutcome", "PASS",
                        "effectiveState", "MH",
                        "effectiveCity", "Mumbai",
                        "applicationView", Map.of(
                                "requestedAmount", "500000",
                                "tenureMonths", 36,
                                "borrowerType", "INDIVIDUAL",
                                "loanProduct", "PERSONAL_LOAN",
                                "applicationNumber", "APP-1",
                                "personalInfo", Map.of("state", "MH", "city", "Mumbai"))))
                .build();

        List<CiUnderwritingFact> facts = List.of(
                fact(snapshotId, "bureau.consumer.score", Map.of("v", 750), FactClassification.VERIFIED.name()),
                fact(snapshotId, "compat.BUREAU_SCORE", Map.of("v", "750"), FactClassification.VERIFIED.name()),
                fact(snapshotId, "compat.MONTHLY_INCOME", Map.of("v", "90000"), FactClassification.VERIFIED.name()),
                fact(snapshotId, "compat.MONTHLY_OBLIGATION", Map.of("v", "15000"), FactClassification.VERIFIED.name()),
                fact(snapshotId, "compat.LIVE_UNSECURED_LOAN_COUNT", Map.of("v", "2"), FactClassification.DEFAULTED.name()),
                fact(snapshotId, "compat.KYC_SUCCESS", Map.of("v", "1"), FactClassification.VERIFIED.name()),
                fact(snapshotId, "kyc.identity_verified", Map.of("v", true), FactClassification.VERIFIED.name())
        );

        when(snapshotRepository.findById(snapshotId)).thenReturn(java.util.Optional.of(snapshot));
        when(factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId)).thenReturn(facts);

        LegacyUnderwritingContextAdapter.AdapterResult result = adapter.adapt(snapshotId);
        EffectiveUnderwritingContext ctx = result.context();

        assertEquals(750, ctx.effectiveBureauScore());
        assertTrue(ctx.kycPassEffective());
        assertEquals(0, new BigDecimal("90000").compareTo(ctx.effectiveIncome()));
        assertEquals(0, new BigDecimal("15000").compareTo(ctx.effectiveObligation()));
        assertEquals(0, new BigDecimal("750").compareTo(ctx.scorecard().get("BUREAU_SCORE")));
        assertEquals(0, new BigDecimal("2").compareTo(ctx.scorecard().get("LIVE_UNSECURED_LOAN_COUNT")));
        assertTrue(result.defaultedPaths().contains("compat.LIVE_UNSECURED_LOAN_COUNT"));
    }

    private static CiUnderwritingFact fact(UUID snapshotId, String path, Map<String, Object> value, String classification) {
        return CiUnderwritingFact.builder()
                .id(UUID.randomUUID())
                .snapshotId(snapshotId)
                .canonicalPath(path)
                .valueType("DECIMAL")
                .value(value)
                .classification(classification)
                .build();
    }
}
