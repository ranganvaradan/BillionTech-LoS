package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauRuleEvaluator;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BureauMetricServiceTest {

    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private CiBureauPaymentHistoryRepository paymentHistoryRepository;

    private BureauMetricService service;
    private final CanonicalBureauRuleEvaluator ruleEvaluator = new CanonicalBureauRuleEvaluator();

    @BeforeEach
    void setUp() {
        service = new BureauMetricService(metricResultRepository, paymentHistoryRepository);
    }

    @Test
    void liveUnsecured_missingExtraction_isDataInsufficient() {
        CiBureauReport report = baseReport(false, "MISSING");
        CiMetricResult r = service.computeLiveUnsecured(report, List.of());
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), r.getOutcome());
        assertNull(r.getValue());
    }

    @Test
    void liveUnsecured_emptyValidZero() {
        CiBureauReport report = baseReport(true, "EMPTY");
        CiMetricResult r = service.computeLiveUnsecured(report, List.of());
        assertEquals(BureauMetricOutcome.PASS.name(), r.getOutcome());
        assertEquals(0, ((Number) r.getValue().get("v")).intValue());
    }

    @Test
    void liveUnsecured_emptyWithoutEmptyStatus_isDataInsufficient() {
        CiBureauReport report = baseReport(true, "OK");
        CiMetricResult r = service.computeLiveUnsecured(report, List.of());
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), r.getOutcome());
        assertNull(r.getValue());
    }

    @Test
    void liveUnsecured_countsOnlyLiveUnsecuredNonDuplicateKnown() {
        CiBureauReport report = baseReport(true, "OK");
        UUID dupOf = UUID.randomUUID();
        List<CiBureauTradeline> tls = List.of(
                tl(true, false, BureauProductCategory.PERSONAL_LOAN, null),
                tl(true, false, BureauProductCategory.CREDIT_CARD, null),
                tl(true, true, BureauProductCategory.HOME_LOAN, null), // secured
                tl(false, false, BureauProductCategory.PERSONAL_LOAN, null), // not live
                tl(true, false, BureauProductCategory.UNKNOWN, null), // unknown product
                tl(true, null, BureauProductCategory.PERSONAL_LOAN, null), // unknown security
                tl(true, false, BureauProductCategory.PERSONAL_LOAN, dupOf) // duplicate
        );
        CiMetricResult r = service.computeLiveUnsecured(report, tls);
        assertEquals(2, ((Number) r.getValue().get("v")).intValue());
        assertTrue(r.getUnknownCount() >= 2);
    }

    @Test
    void obligation_doesNotInventEmi() {
        CiBureauReport report = baseReport(true, "OK");
        CiBureauTradeline withEmi = tl(true, false, BureauProductCategory.PERSONAL_LOAN, null);
        withEmi.setEmiAmount(new BigDecimal("5000"));
        CiBureauTradeline noEmi = tl(true, false, BureauProductCategory.CREDIT_CARD, null);
        noEmi.setEmiAmount(null);

        // use package-private path via computeAndPersist pieces — call computeMonthlyObligation through reflection-free public computeAndPersist
        // Directly test via computeAndPersist would persist; instead replicate obligation logic outcome:
        var results = List.of(withEmi, noEmi);
        // Invoke private via computing live then checking obligation using public computeAndPersist with mock save
        org.mockito.Mockito.when(metricResultRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        List<CiMetricResult> all = service.computeAndPersist(report, results, Map.of());
        CiMetricResult obl = all.stream()
                .filter(m -> BureauMetricService.TOTAL_MONTHLY_OBLIGATION.equals(m.getMetricCode()))
                .findFirst().orElseThrow();
        assertEquals("5000", String.valueOf(obl.getValue().get("v")));
        assertEquals("PARTIAL", obl.getDataQualityStatus());
        assertEquals(false, obl.getEvidence().get("emiInvented"));
    }

    @Test
    void maxDpd_withoutPaymentHistory_isDataInsufficientNotZero() {
        CiBureauReport report = baseReport(true, "OK");
        CiBureauTradeline t = tl(true, false, BureauProductCategory.PERSONAL_LOAN, null);
        t.setId(UUID.randomUUID());
        org.mockito.Mockito.when(metricResultRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(t.getId()))
                .thenReturn(List.of());

        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t),
                Map.of("dpd30Plus", 1, "dpd60Plus", 0));
        CiMetricResult dpd = all.stream()
                .filter(m -> BureauMetricService.MAX_DPD_12M.equals(m.getMetricCode()))
                .findFirst().orElseThrow();
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), dpd.getOutcome());
        assertNull(dpd.getValue());
        assertTrue(dpd.getEvidence().containsKey("aggregateDpdFlags"));
    }

    @Test
    void shadowRule_dataInsufficient() {
        CiBureauReport report = baseReport(false, "MISSING");
        CiMetricResult metric = service.computeLiveUnsecured(report, List.of());
        var eval = ruleEvaluator.evaluate(metric, 6);
        assertEquals("DATA_INSUFFICIENT", eval.outcome());
        assertNull(eval.value());
    }

    @Test
    void shadowRule_failWhenAboveThreshold() {
        CiMetricResult metric = CiMetricResult.builder()
                .metricCode(BureauMetricService.LIVE_UNSECURED)
                .metricVersion("V1")
                .outcome(BureauMetricOutcome.PASS.name())
                .value(Map.of("v", 8))
                .build();
        var eval = ruleEvaluator.evaluate(metric, 6);
        assertEquals("FAIL", eval.outcome());
        assertEquals(8, eval.value());
    }

    private static CiBureauReport baseReport(boolean present, String status) {
        return CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .subjectType("CONSUMER")
                .providerCode("EQUIFAX")
                .parserVersion("EQUIFAX_PARSER_V2")
                .normalizerVersion("BUREAU_NORMALIZER_V1")
                .tradelinesPresent(present)
                .tradelineExtractionStatus(status)
                .build();
    }

    private static CiBureauTradeline tl(boolean live, Boolean secured, BureauProductCategory cat, UUID dupOf) {
        return CiBureauTradeline.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bureauReportId(UUID.randomUUID())
                .productCategory(cat.name())
                .secured(secured)
                .isLive(live)
                .currentBalance(new BigDecimal("1000"))
                .duplicateOfTradelineId(dupOf)
                .providerTradelineRef(UUID.randomUUID().toString())
                .build();
    }
}
