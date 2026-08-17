package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquifaxDerivedMetricGoldenTest {

    private static final LocalDate ASOF = LocalDate.of(2024, 6, 15);

    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private CiBureauPaymentHistoryRepository paymentHistoryRepository;

    private BureauMetricService service;

    @BeforeEach
    void setUp() {
        service = new BureauMetricService(metricResultRepository, paymentHistoryRepository);
        lenient().when(metricResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(any())).thenReturn(List.of());
    }

    @Test
    void dpdCountGrain_distinctAccountMonth_nullDpdSkipped() {
        CiBureauReport report = baseReport();
        CiBureauTradeline a = tl(BureauProductCategory.PERSONAL_LOAN, false);
        CiBureauTradeline b = tl(BureauProductCategory.HOME_LOAN, false);
        stubPh(a.getId(), List.of(
                ph(a.getId(), LocalDate.of(2024, 3, 1), 30, "STD", "STD", null),
                ph(a.getId(), LocalDate.of(2024, 4, 1), 30, "STD", "STD", null),
                ph(a.getId(), LocalDate.of(2024, 5, 1), 20, "STD", "STD", null),
                ph(a.getId(), LocalDate.of(2024, 5, 1), null, "STD", "STD", null),
                ph(a.getId(), LocalDate.of(2024, 6, 1), 90, "STD", "STD", null)));
        stubPh(b.getId(), List.of(
                ph(b.getId(), LocalDate.of(2024, 3, 1), 30, "STD", "STD", null)));

        List<CiMetricResult> all = service.computeAndPersist(report, List.of(a, b), Map.of());
        // A: Mar30, Apr30, Jun90; B: Mar30 → 4 distinct account-months (Mar counted twice)
        assertEquals(4, intVal(metric(all, BureauMetricService.DPD_30_PLUS_COUNT_6M)));
        assertEquals(1, intVal(metric(all, BureauMetricService.DPD_60_PLUS_COUNT_6M)));
        assertEquals(1, intVal(metric(all, BureauMetricService.DPD_90_PLUS_COUNT_6M)));
    }

    @Test
    void emptyPaymentHistory_dpdCountsAreDataInsufficient() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        CiMetricResult dpd30 = metric(all, BureauMetricService.DPD_30_PLUS_COUNT_6M);
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), dpd30.getOutcome());
        assertNull(dpd30.getValue());
    }

    @Test
    void ccMaxOverdue_multiCard_usesMaxNotSum() {
        CiBureauReport report = baseReport();
        CiBureauTradeline cc1 = tl(BureauProductCategory.CREDIT_CARD, true);
        cc1.setOverdueAmount(new BigDecimal("1000"));
        CiBureauTradeline cc2 = tl(BureauProductCategory.CREDIT_CARD, true);
        cc2.setOverdueAmount(new BigDecimal("2500"));
        CiBureauTradeline pl = tl(BureauProductCategory.PERSONAL_LOAN, false);
        pl.setOverdueAmount(new BigDecimal("9000"));

        List<CiMetricResult> all = service.computeAndPersist(report, List.of(cc1, cc2, pl), Map.of());
        assertEquals("2500", String.valueOf(metric(all, BureauMetricService.CC_OVERDUE_AMOUNT).getValue().get("v")));
        assertEquals("9000", String.valueOf(metric(all, BureauMetricService.OVERDUE_AMOUNT).getValue().get("v")));
    }

    @Test
    void ccUtilisation_missingLimit_isDataInsufficient() {
        CiBureauReport report = baseReport();
        CiBureauTradeline cc = tl(BureauProductCategory.CREDIT_CARD, true);
        cc.setIsLive(true);
        cc.setCurrentBalance(new BigDecimal("1000"));
        cc.setHighCredit(null);
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(cc), Map.of());
        CiMetricResult util = metric(all, BureauMetricService.CC_UTILISATION);
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), util.getOutcome());
        assertEquals("CREDIT_LIMIT_MISSING", util.getEvidence().get("reason"));
    }

    @Test
    void panDistinct_normalizesAndDoesNotPersistPanStrings() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t),
                Map.of("panIds", List.of("abc", "ABC", " ", "xyz")));
        CiMetricResult pan = metric(all, BureauMetricService.PAN_DISTINCT_COUNT);
        assertEquals(2, intVal(pan));
        assertEquals(false, pan.getEvidence().get("valueContainsPanStrings"));
        assertNull(String.valueOf(pan.getValue()).matches("(?i).*abc.*") ? "leak" : null);
        assertTrue(!String.valueOf(pan.getValue()).toLowerCase().contains("abc"));
        assertTrue(!String.valueOf(pan.getEvidence()).toLowerCase().contains("abc"));
    }

    @Test
    void panMissingKey_isDataInsufficient_emptyListIsZero() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        CiMetricResult missing = metric(service.computeAndPersist(report, List.of(t), Map.of()),
                BureauMetricService.PAN_DISTINCT_COUNT);
        assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), missing.getOutcome());
        assertEquals("NO_PAN_EXTRACTION", missing.getEvidence().get("reason"));

        CiMetricResult empty = metric(service.computeAndPersist(report, List.of(t), Map.of("panIds", List.of())),
                BureauMetricService.PAN_DISTINCT_COUNT);
        assertEquals(BureauMetricOutcome.PASS.name(), empty.getOutcome());
        assertEquals(0, intVal(empty));
        assertEquals("NO_PAN_NODES", empty.getEvidence().get("reason"));
    }

    @Test
    void multiOverdueAB_violationAtLeastOne() {
        CiBureauReport report = baseReport();
        CiBureauTradeline overdueOk = tl(BureauProductCategory.PERSONAL_LOAN, false);
        overdueOk.setOverdueAmount(new BigDecimal("1000"));
        overdueOk.setOpenedDate(LocalDate.of(2020, 1, 1));
        CiBureauTradeline later = tl(BureauProductCategory.PERSONAL_LOAN, false);
        later.setOpenedDate(LocalDate.of(2023, 6, 1));
        later.setOverdueAmount(BigDecimal.ZERO);
        CiBureauTradeline overdueFail = tl(BureauProductCategory.HOME_LOAN, false);
        overdueFail.setOverdueAmount(new BigDecimal("2000"));
        overdueFail.setOpenedDate(LocalDate.of(2019, 1, 1));

        stubPh(overdueOk.getId(), List.of(
                ph(overdueOk.getId(), LocalDate.of(2023, 5, 1), 30, "STD", "STD", null)));
        List<CiBureauPaymentHistory> laterPh = new ArrayList<>();
        for (int m = 6; m <= 11; m++) {
            laterPh.add(ph(later.getId(), LocalDate.of(2023, m, 1), 0, "STD", "STD", null));
        }
        stubPh(later.getId(), laterPh);
        stubPh(overdueFail.getId(), List.of(
                ph(overdueFail.getId(), LocalDate.of(2024, 1, 1), 15, "STD", "STD", null)));

        List<CiMetricResult> all = service.computeAndPersist(
                report, List.of(overdueOk, later, overdueFail), Map.of());
        CiMetricResult violations = metric(all, BureauMetricService.NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT);
        assertEquals(BureauMetricOutcome.PASS.name(), violations.getOutcome());
        assertTrue(intVal(violations) >= 1);
        assertEquals(1, intVal(violations));
    }

    @Test
    void suitMultiMonthSameAccount_countsOnce() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        t.setSuitFiled(false);
        stubPh(t.getId(), List.of(
                ph(t.getId(), LocalDate.of(2024, 4, 1), 0, "STD", "STD", "YES"),
                ph(t.getId(), LocalDate.of(2024, 5, 1), 0, "STD", "STD", "YES"),
                ph(t.getId(), LocalDate.of(2024, 6, 1), 0, "STD", "STD", "*")));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.SUIT_FILED_ACCOUNT_COUNT)));
    }

    @Test
    void scoreMinusOne_withoutNoRecord_isNotNtc() {
        CiBureauReport report = baseReport();
        report.setScore(-1);
        CiMetricResult ntc = service.computeStatusNtc(report, Map.of());
        assertEquals(BureauMetricOutcome.PASS.name(), ntc.getOutcome());
        assertEquals(0, intVal(ntc));
        assertEquals("SCORE_SENTINEL_WITHOUT_NO_RECORD_FLAG", ntc.getEvidence().get("source"));
    }

    @Test
    void currentMonthInquiry_differsFromTrailing90d() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        Map<String, Object> data = Map.of(
                "inquiries", List.of(
                        Map.of("inquiryDate", "2024-06-10"),
                        Map.of("inquiryDate", "2024-03-20")));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), data);
        int current = intVal(metric(all, BureauMetricService.INQUIRIES_CURRENT_MONTH));
        int trailing90 = intVal(metric(all, BureauMetricService.RECENT_INQUIRIES_90D));
        int last3 = intVal(metric(all, BureauMetricService.INQUIRIES_LAST_3M));
        assertEquals(1, current);
        assertEquals(2, trailing90);
        assertEquals(2, last3);
        assertNotEquals(current, trailing90);
    }

    @Test
    void evaluationAsOf_overridesReportDateForEnquiries() {
        CiBureauReport report = baseReport();
        report.setReportDate(LocalDate.of(2025, 1, 1));
        CiBureauTradeline t = tl(BureauProductCategory.PERSONAL_LOAN, false);
        Map<String, Object> data = Map.of(
                "evaluationAsOf", ASOF,
                "inquiries", List.of(Map.of("inquiryDate", "2024-06-10")));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), data);
        assertEquals(1, intVal(metric(all, BureauMetricService.INQUIRIES_CURRENT_MONTH)));
        assertEquals("EVALUATION_AS_OF",
                metric(all, BureauMetricService.INQUIRIES_CURRENT_MONTH).getEvidence().get("asOfSource"));
    }

    private void stubPh(UUID tradelineId, List<CiBureauPaymentHistory> rows) {
        lenient().when(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(tradelineId)).thenReturn(rows);
    }

    private static CiMetricResult metric(List<CiMetricResult> all, String code) {
        return all.stream().filter(m -> code.equals(m.getMetricCode())).findFirst().orElseThrow();
    }

    private static int intVal(CiMetricResult r) {
        return ((Number) r.getValue().get("v")).intValue();
    }

    private static CiBureauReport baseReport() {
        return CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .subjectType("CONSUMER")
                .providerCode("EQUIFAX")
                .parserVersion("EQUIFAX_PARSER_V2")
                .normalizerVersion("BUREAU_NORMALIZER_V1")
                .reportDate(ASOF)
                .tradelinesPresent(true)
                .tradelineExtractionStatus("OK")
                .score(720)
                .build();
    }

    private static CiBureauTradeline tl(BureauProductCategory cat, boolean live) {
        return CiBureauTradeline.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bureauReportId(UUID.randomUUID())
                .productCategory(cat.name())
                .secured(false)
                .isLive(live)
                .currentBalance(new BigDecimal("1000"))
                .providerTradelineRef(UUID.randomUUID().toString())
                .build();
    }

    private static CiBureauPaymentHistory ph(
            UUID tradelineId, LocalDate month, Integer dpd, String raw, String acs, String suit) {
        return CiBureauPaymentHistory.builder()
                .tradelineId(tradelineId)
                .month(month)
                .dpd(dpd)
                .status(dpd != null && dpd > 0 ? "DPD" : "CURRENT")
                .providerRawStatus(raw)
                .assetClassificationStatus(acs)
                .suitFiledStatus(suit)
                .build();
    }
}
