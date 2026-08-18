package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.EquifaxRetailPaymentStatusVocabulary;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquifaxAdverseStatusProvenanceTest {

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
    void vocabulary_doesNotTreatAuctionedSettledOrWriteOffAsProvenFamilies() {
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("AS", EquifaxRetailPaymentStatusVocabulary.Family.PWOS));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("WOF", EquifaxRetailPaymentStatusVocabulary.Family.LOSS));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("SFWO", EquifaxRetailPaymentStatusVocabulary.Family.LOSS));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("SET", EquifaxRetailPaymentStatusVocabulary.Family.PWOS));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("RCV", EquifaxRetailPaymentStatusVocabulary.Family.DBT));
        assertTrue(EquifaxRetailPaymentStatusVocabulary.matches("RC", EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED));
        assertTrue(EquifaxRetailPaymentStatusVocabulary.matches("RCV", EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("*", EquifaxRetailPaymentStatusVocabulary.Family.DBT));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("STD", EquifaxRetailPaymentStatusVocabulary.Family.DBT));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.matches("SPM", EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED));
    }

    @Test
    void noTradelines_isDataInsufficient_notZero() {
        CiBureauReport report = baseReport();
        report.setTradelinesPresent(false);
        report.setTradelineExtractionStatus("MISSING");
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(), Map.of());
        for (String code : List.of(
                BureauMetricService.DBT_ACCOUNT_COUNT,
                BureauMetricService.PWOS_ACCOUNT_COUNT,
                BureauMetricService.LSS_ACCOUNT_COUNT,
                BureauMetricService.RESTRUCTURED_ACCOUNT_COUNT)) {
            CiMetricResult r = metric(all, code);
            assertEquals(BureauMetricOutcome.DATA_INSUFFICIENT.name(), r.getOutcome(), code);
            assertNull(r.getValue() == null ? null : r.getValue().get("v"), code);
        }
    }

    @Test
    void emptyTradelines_isZero() {
        CiBureauReport report = baseReport();
        report.setTradelineExtractionStatus("EMPTY");
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(), Map.of());
        assertEquals(0, intVal(metric(all, BureauMetricService.DBT_ACCOUNT_COUNT)));
    }

    @Test
    void starBlankStdSpm_doNotCount() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        stubPh(t.getId(), List.of(
                ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "*", "Standard", "*"),
                ph(t.getId(), LocalDate.of(2024, 2, 1), 0, "STD", "STD", null),
                ph(t.getId(), LocalDate.of(2024, 3, 1), 0, "SPM", "SPM", null),
                ph(t.getId(), LocalDate.of(2024, 4, 1), 30, "30+", "Standard", "*")));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(0, intVal(metric(all, BureauMetricService.DBT_ACCOUNT_COUNT)));
        assertEquals(0, intVal(metric(all, BureauMetricService.RESTRUCTURED_ACCOUNT_COUNT)));
        assertEquals(0, intVal(metric(all, BureauMetricService.PWOS_ACCOUNT_COUNT)));
        assertEquals(0, intVal(metric(all, BureauMetricService.LSS_ACCOUNT_COUNT)));
    }

    @Test
    void duplicateMonthlyCodes_countAccountOnce() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        stubPh(t.getId(), List.of(
                ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "DBT", null, null),
                ph(t.getId(), LocalDate.of(2024, 2, 1), 0, "DBT", null, null),
                ph(t.getId(), LocalDate.of(2024, 3, 1), 0, "DBT", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.DBT_ACCOUNT_COUNT)));
    }

    @Test
    void accountLevelAndHistory_doNotDoubleCount() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        t.setAccountStatus("DBT");
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "DBT", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.DBT_ACCOUNT_COUNT)));
    }

    @Test
    void closedAccountHistory_stillCounts() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        t.setAccountStatus("Closed Account");
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2023, 1, 1), 0, "LOSS", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.LSS_ACCOUNT_COUNT)));
        assertEquals("LOSS", metric(all, BureauMetricService.LSS_ACCOUNT_COUNT).getEvidence().get("providerCode"));
    }

    @Test
    void eachRestructuringCode_countsOncePerAccount() {
        for (String code : EquifaxRetailPaymentStatusVocabulary.RESTRUCTURED) {
            CiBureauReport report = baseReport();
            CiBureauTradeline t = tl();
            stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, code, null, null)));
            List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
            assertEquals(1, intVal(metric(all, BureauMetricService.RESTRUCTURED_ACCOUNT_COUNT)), code);
        }
    }

    @Test
    void pwos_notInferredFromSettlementAmountOrWof() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        t.setSettlementAmount(new BigDecimal("5000"));
        t.setWrittenOff(true);
        t.setWrittenOffAmount(new BigDecimal("5000"));
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "WOF", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(0, intVal(metric(all, BureauMetricService.PWOS_ACCOUNT_COUNT)));
        t = tl();
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "PWOS", null, null)));
        all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.PWOS_ACCOUNT_COUNT)));
    }

    @Test
    void assetClassificationLoss_doesNotCountAsPaymentStatusLoss() {
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "STD", "Loss", null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(0, intVal(metric(all, BureauMetricService.LSS_ACCOUNT_COUNT)));
    }

    @Test
    void rcIsExactToken_notSubstringOfRcv() {
        assertTrue(EquifaxRetailPaymentStatusVocabulary.matches("RC", EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED));
        assertTrue(EquifaxRetailPaymentStatusVocabulary.matches("RCV", EquifaxRetailPaymentStatusVocabulary.Family.RESTRUCTURED));
        assertFalse(EquifaxRetailPaymentStatusVocabulary.token("RCV").equals("RC"));
        CiBureauReport report = baseReport();
        CiBureauTradeline t = tl();
        stubPh(t.getId(), List.of(ph(t.getId(), LocalDate.of(2024, 1, 1), 0, "RCV", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(t), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.RESTRUCTURED_ACCOUNT_COUNT)));
    }

    @Test
    void duplicateTradeline_notDoubleCounted() {
        CiBureauReport report = baseReport();
        CiBureauTradeline primary = tl();
        CiBureauTradeline dup = tl();
        dup.setDuplicateOfTradelineId(primary.getId());
        stubPh(primary.getId(), List.of(ph(primary.getId(), LocalDate.of(2024, 1, 1), 0, "PWOS", null, null)));
        stubPh(dup.getId(), List.of(ph(dup.getId(), LocalDate.of(2024, 1, 1), 0, "PWOS", null, null)));
        List<CiMetricResult> all = service.computeAndPersist(report, List.of(primary, dup), Map.of());
        assertEquals(1, intVal(metric(all, BureauMetricService.PWOS_ACCOUNT_COUNT)));
    }

    @Test
    void producerDoesNotClaimUnprovenIds() {
        assertFalse(com.los.core.creditintelligence.policystudio.parameters.execution
                .BuiltInBureauMetricProducer.EMITTED_IDS.contains("bureau.account_sold_count"));
        assertFalse(com.los.core.creditintelligence.policystudio.parameters.execution
                .BuiltInBureauMetricProducer.EMITTED_IDS.contains("bureau.thin_file_indicator"));
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

    private static CiBureauTradeline tl() {
        return CiBureauTradeline.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bureauReportId(UUID.randomUUID())
                .productCategory(BureauProductCategory.PERSONAL_LOAN.name())
                .secured(false)
                .isLive(true)
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
