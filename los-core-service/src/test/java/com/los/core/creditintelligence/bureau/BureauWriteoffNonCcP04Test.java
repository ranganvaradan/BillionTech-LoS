package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BUREAU-INTEGRATION-P0-4 — writeoff_non_cc shared calculator goldens + Policy Test ↔ live parity.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BureauWriteoffNonCcP04Test {

    @Mock CiMetricResultRepository metricResultRepository;
    @Mock CiBureauPaymentHistoryRepository paymentHistoryRepository;

    private BureauMetricService live;
    private PolicyBureauMetricService studio;

    @BeforeEach
    void setUp() {
        live = new BureauMetricService(metricResultRepository, paymentHistoryRepository);
        studio = new PolicyBureauMetricService();
        lenient().when(metricResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void case1_noWriteoffs_realZero() {
        CiBureauReport report = report(true, "OK", 720, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.PERSONAL_LOAN, "ACTIVE", BigDecimal.ZERO, false),
                tl(BureauProductCategory.CREDIT_CARD, "ACTIVE", BigDecimal.ZERO, false));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.PASS.name());
        assertThat(r.nonCcCount()).isZero();
        assertThat(r.ccCount()).isZero();

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(false, "ACTIVE", BigDecimal.ZERO, "PERSONAL_LOAN"),
                studioTl(true, "ACTIVE", BigDecimal.ZERO, "CREDIT_CARD")));
        assertStudioPass(studioMap, 0, 0);
        assertParity(r, studioMap);
    }

    @Test
    void case2_oneNonCcWriteoff() {
        CiBureauReport report = report(true, "OK", 700, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.PERSONAL_LOAN, "WRITE-OFF", new BigDecimal("5000"), true));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.nonCcCount()).isEqualTo(1);
        assertThat(r.ccCount()).isZero();
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.PASS.name());

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(false, "WRITE-OFF", new BigDecimal("5000"), "PERSONAL_LOAN")));
        assertStudioPass(studioMap, 1, 0);
        assertParity(r, studioMap);
    }

    @Test
    void case3_ccOnlyWriteoff_doesNotIncrementNonCc() {
        CiBureauReport report = report(true, "OK", 700, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.CREDIT_CARD, "WRITE-OFF", new BigDecimal("2000"), true));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.nonCcCount()).isZero();
        assertThat(r.ccCount()).isEqualTo(1);

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(true, "WRITE-OFF", new BigDecimal("2000"), "CREDIT_CARD")));
        assertStudioPass(studioMap, 0, 1);
        assertParity(r, studioMap);
    }

    @Test
    void case4_mixed_onlyNonCcCounted() {
        CiBureauReport report = report(true, "OK", 700, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.CREDIT_CARD, "WRITE-OFF", new BigDecimal("1000"), true),
                tl(BureauProductCategory.AUTO_LOAN, "WRITE-OFF", new BigDecimal("9000"), true));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.nonCcCount()).isEqualTo(1);
        assertThat(r.ccCount()).isEqualTo(1);

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(true, "WRITE-OFF", new BigDecimal("1000"), "CREDIT_CARD"),
                studioTl(false, "WRITE-OFF", new BigDecimal("9000"), "AUTO_LOAN")));
        assertStudioPass(studioMap, 1, 1);
        assertParity(r, studioMap);
    }

    @Test
    void case5_multipleNonCc() {
        CiBureauReport report = report(true, "OK", 700, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.PERSONAL_LOAN, "LSS", new BigDecimal("1"), true),
                tl(BureauProductCategory.HOME_LOAN, "PWOS", new BigDecimal("2"), true),
                tl(BureauProductCategory.AUTO_LOAN, "WRITE OFF", new BigDecimal("3"), true));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.nonCcCount()).isEqualTo(3);
        assertThat(r.ccCount()).isZero();
    }

    @Test
    void case6_unknownAccountTypeOnWriteoff_failsClosed() {
        CiBureauReport report = report(true, "OK", 700, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.UNKNOWN, "WRITE-OFF", new BigDecimal("5000"), true));
        var r = live.computeWriteoffCounts(report, tls, Map.of());
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.nonCcCount()).isNull();

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(false, "WRITE-OFF", new BigDecimal("5000"), "UNKNOWN")));
        @SuppressWarnings("unchecked")
        Map<String, Object> nonCc = (Map<String, Object>) studioMap.get(BureauMetricService.WRITEOFF_NON_CC);
        assertThat(nonCc.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(nonCc.get("v")).isNull();
    }

    @Test
    void case7_missingWriteoffFields_failsClosed() {
        CiBureauReport report = report(true, "OK", 700, false);
        CiBureauTradeline missing = tl(BureauProductCategory.PERSONAL_LOAN, null, null, false);
        var r = live.computeWriteoffCounts(report, List.of(missing), Map.of());
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.nonCcCount()).isNull();

        Map<String, Object> studioMap = studio.writeoffCounts(List.of(
                studioTl(false, null, null, "PERSONAL_LOAN")));
        @SuppressWarnings("unchecked")
        Map<String, Object> nonCc = (Map<String, Object>) studioMap.get(BureauMetricService.WRITEOFF_NON_CC);
        assertThat(nonCc.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void case8_ntcNoHit_notCleanZero() {
        CiBureauReport report = report(false, "EMPTY", -1, true);
        report.setScore(-1);
        var r = live.computeWriteoffCounts(report, List.of(), Map.of("noRecordFound", true));
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.reason()).isEqualTo("NTC_NO_HIT");
        assertThat(r.nonCcCount()).isNull();
    }

    @Test
    void case9_providerUnavailable_notInventedZero() {
        CiBureauReport report = report(false, "MISSING", null, false);
        var r = live.computeWriteoffCounts(report, null, Map.of());
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.nonCcCount()).isNull();
    }

    @Test
    void case10_malformedExtraction_failsClosed() {
        CiBureauReport report = report(false, "FAILED", 710, false);
        var r = live.computeWriteoffCounts(report, List.of(), Map.of());
        assertThat(r.outcome()).isEqualTo(BureauMetricOutcome.DATA_INSUFFICIENT.name());
    }

    @Test
    void computeAndPersist_includesWriteoffMetrics() {
        CiBureauReport report = report(true, "OK", 720, false);
        List<CiBureauTradeline> tls = List.of(
                tl(BureauProductCategory.PERSONAL_LOAN, "ACTIVE", BigDecimal.ZERO, false));
        var results = live.computeAndPersist(report, tls, Map.of());
        assertThat(results.stream().map(m -> m.getMetricCode()))
                .contains(BureauMetricService.WRITEOFF_NON_CC, BureauMetricService.WRITEOFF_CC);
    }

    private static void assertStudioPass(Map<String, Object> studioMap, int nonCc, int cc) {
        @SuppressWarnings("unchecked")
        Map<String, Object> n = (Map<String, Object>) studioMap.get(BureauMetricService.WRITEOFF_NON_CC);
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) studioMap.get(BureauMetricService.WRITEOFF_CC);
        assertThat(n.get("outcome")).isEqualTo("PASS");
        assertThat(((Number) n.get("v")).intValue()).isEqualTo(nonCc);
        assertThat(((Number) c.get("v")).intValue()).isEqualTo(cc);
        assertThat(n.get("calculator")).isEqualTo(BureauMetricService.WRITEOFF_CALCULATOR);
    }

    private static void assertParity(
            BureauMetricService.WriteoffCountResult liveResult, Map<String, Object> studioMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> n = (Map<String, Object>) studioMap.get(BureauMetricService.WRITEOFF_NON_CC);
        assertThat(n.get("outcome")).isEqualTo(
                BureauMetricOutcome.PASS.name().equals(liveResult.outcome()) ? "PASS" : "DATA_INSUFFICIENT");
        if (liveResult.nonCcCount() == null) {
            assertThat(n.get("v")).isNull();
        } else {
            assertThat(((Number) n.get("v")).intValue()).isEqualTo(liveResult.nonCcCount());
        }
    }

    private static CiBureauReport report(boolean present, String status, Integer score, boolean ignored) {
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
                .score(score)
                .build();
    }

    private static CiBureauTradeline tl(
            BureauProductCategory cat, String status, BigDecimal amount, boolean writtenOff) {
        return CiBureauTradeline.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bureauReportId(UUID.randomUUID())
                .productCategory(cat.name())
                .accountStatus(status)
                .writtenOffAmount(amount)
                .writtenOff(writtenOff)
                .providerTradelineRef(UUID.randomUUID().toString())
                .build();
    }

    private static PolicyBureauMetricService.TradelineInput studioTl(
            boolean creditCard, String status, BigDecimal amount, String productCategory) {
        return new PolicyBureauMetricService.TradelineInput(
                productCategory, status, creditCard, null, amount, List.of(), null, false);
    }
}
