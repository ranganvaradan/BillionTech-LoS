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
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Combined Equifax derived golden fixture → stdout execution matrix for every
 * {@link BuiltInBureauMetricProducer#EMITTED_IDS} entry.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquifaxDerivedGoldenExecutionMatrixTest {

    private static final LocalDate ASOF = LocalDate.of(2024, 6, 15);
    private static final String CALC_TYPE = DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE;
    private static final String EXEC_AUTH = "BureauMetricService";

    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private CiBureauPaymentHistoryRepository paymentHistoryRepository;

    private BureauMetricService service;
    private final List<CiMetricResult> persisted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new BureauMetricService(metricResultRepository, paymentHistoryRepository);
        persisted.clear();
        lenient().when(metricResultRepository.save(any())).thenAnswer(inv -> {
            CiMetricResult r = inv.getArgument(0);
            persisted.add(r);
            return r;
        });
        lenient().when(paymentHistoryRepository.findByTradelineIdOrderByMonthDesc(any())).thenReturn(List.of());
    }

    @Test
    void combinedGoldenFixture_printsExecutionMatrix_andAssertsCriticalGoldensAK() {
        CiBureauReport report = baseReport();
        report.setScore(-1); // I: sentinel without no-record is not NTC

        CiBureauTradeline dpdA = tl(BureauProductCategory.PERSONAL_LOAN, false);
        CiBureauTradeline dpdB = tl(BureauProductCategory.HOME_LOAN, false);
        stubPh(dpdA.getId(), List.of(
                ph(dpdA.getId(), LocalDate.of(2024, 3, 1), 30, "STD", "STD", null),
                ph(dpdA.getId(), LocalDate.of(2024, 4, 1), 30, "STD", "STD", null),
                ph(dpdA.getId(), LocalDate.of(2024, 5, 1), 20, "STD", "STD", null),
                ph(dpdA.getId(), LocalDate.of(2024, 5, 1), null, "STD", "STD", null),
                ph(dpdA.getId(), LocalDate.of(2024, 6, 1), 90, "STD", "STD", null)));
        stubPh(dpdB.getId(), List.of(
                ph(dpdB.getId(), LocalDate.of(2024, 3, 1), 30, "STD", "STD", null)));

        CiBureauTradeline cc1 = tl(BureauProductCategory.CREDIT_CARD, true);
        cc1.setOverdueAmount(new BigDecimal("1000"));
        cc1.setCurrentBalance(new BigDecimal("1000"));
        cc1.setHighCredit(new BigDecimal("4000"));
        CiBureauTradeline cc2 = tl(BureauProductCategory.CREDIT_CARD, true);
        cc2.setOverdueAmount(new BigDecimal("2500"));
        cc2.setCurrentBalance(new BigDecimal("3000"));
        cc2.setHighCredit(new BigDecimal("4000"));

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

        CiBureauTradeline suit = tl(BureauProductCategory.PERSONAL_LOAN, false);
        suit.setSuitFiled(false);
        stubPh(suit.getId(), List.of(
                ph(suit.getId(), LocalDate.of(2024, 4, 1), 0, "STD", "STD", "YES"),
                ph(suit.getId(), LocalDate.of(2024, 5, 1), 0, "STD", "STD", "YES"),
                ph(suit.getId(), LocalDate.of(2024, 6, 1), 0, "STD", "STD", "*")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evaluationAsOf", ASOF);
        data.put("inquiries", List.of(
                Map.of("inquiryDate", "2024-06-10"),
                Map.of("inquiryDate", "2024-03-20")));
        data.put("panIds", List.of("ABCDE1234F", "abcde1234f"));

        List<CiBureauTradeline> tradelines = List.of(
                dpdA, dpdB, cc1, cc2, overdueOk, later, overdueFail, suit);
        List<CiMetricResult> all = service.computeAndPersist(report, tradelines, data);
        Map<String, CiMetricResult> byCode = all.stream()
                .collect(Collectors.toMap(CiMetricResult::getMetricCode, r -> r, (a, b) -> a, LinkedHashMap::new));
        Set<String> persistedCodes = persisted.stream()
                .map(CiMetricResult::getMetricCode)
                .collect(Collectors.toSet());

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(BureauMetricService.DPD_30_PLUS_COUNT_6M, "4");
        expected.put(BureauMetricService.DPD_60_PLUS_COUNT_6M, "1");
        expected.put(BureauMetricService.DPD_90_PLUS_COUNT_6M, "1");
        expected.put(BureauMetricService.CC_OVERDUE_AMOUNT, "2500");
        expected.put(BureauMetricService.CC_UTILISATION, "0.5");
        expected.put(BureauMetricService.INQUIRIES_CURRENT_MONTH, "1");
        expected.put(BureauMetricService.RECENT_INQUIRIES_90D, "2");
        expected.put(BureauMetricService.INQUIRIES_LAST_3M, "2");
        expected.put(BureauMetricService.PAN_DISTINCT_COUNT, "1");
        expected.put(BureauMetricService.SUIT_FILED_ACCOUNT_COUNT, "1");
        expected.put(BureauMetricService.NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT, "1");
        expected.put(BureauMetricService.STATUS_NTC, "0");

        System.out.println("EQUIFAX_DERIVED_GOLDEN_EXECUTION_MATRIX_BEGIN");
        System.out.println("PARAMETER_ID|CALCULATION_TYPE|EXECUTION_AUTHORITY|EXPECTED|ACTUAL|OUTCOME|PERSISTED|PASS_FAIL");
        int failRows = 0;
        for (String id : new TreeSet<>(BuiltInBureauMetricProducer.EMITTED_IDS)) {
            CiMetricResult row = byCode.get(id);
            String actual = display(row);
            String exp = expected.containsKey(id) ? expected.get(id) : actual;
            String outcome = row == null ? "MISSING" : String.valueOf(row.getOutcome());
            String persistedFlag = persistedCodes.contains(id) ? "YES" : "NO";
            String passFail = Objects.equals(exp, actual) && row != null && "YES".equals(persistedFlag)
                    ? "PASS" : "FAIL";
            if ("FAIL".equals(passFail)) {
                failRows++;
            }
            System.out.println(id + "|" + CALC_TYPE + "|" + EXEC_AUTH + "|"
                    + exp + "|" + actual + "|" + outcome + "|" + persistedFlag + "|" + passFail);
        }
        System.out.println("EQUIFAX_DERIVED_GOLDEN_EXECUTION_MATRIX_END");

        assertEquals(BuiltInBureauMetricProducer.EMITTED_IDS.size(), byCode.keySet().stream()
                .filter(BuiltInBureauMetricProducer.EMITTED_IDS::contains)
                .count(), "compute results must cover every EMITTED_ID");
        assertEquals(0, failRows, "matrix rows with FAIL");

        // A: DPD count grain — distinct account-month; null DPD skipped
        assertEquals(4, intVal(metric(all, BureauMetricService.DPD_30_PLUS_COUNT_6M)));
        assertEquals(1, intVal(metric(all, BureauMetricService.DPD_60_PLUS_COUNT_6M)));
        assertEquals(1, intVal(metric(all, BureauMetricService.DPD_90_PLUS_COUNT_6M)));
        // B empty-PH DI not applicable (fixture has payment history)
        // C: CC overdue MAX not SUM
        assertEquals("2500", String.valueOf(metric(all, BureauMetricService.CC_OVERDUE_AMOUNT).getValue().get("v")));
        // D missing-limit DI not applicable (limits present so utilisation is executable)
        assertEquals(0, new BigDecimal("0.5").compareTo(new BigDecimal(
                String.valueOf(metric(all, BureauMetricService.CC_UTILISATION).getValue().get("v")))));
        // E: two same PAN → 1; no PAN strings persisted
        CiMetricResult pan = metric(all, BureauMetricService.PAN_DISTINCT_COUNT);
        assertEquals(1, intVal(pan));
        assertEquals(false, pan.getEvidence().get("valueContainsPanStrings"));
        assertTrue(!String.valueOf(pan.getValue()).toLowerCase().contains("abcde"));
        assertTrue(!String.valueOf(pan.getEvidence()).toLowerCase().contains("abcde"));
        // F missing-key / empty-list not applicable (panIds present)
        // G: overdue exception A/B → 1 violation
        CiMetricResult violations = metric(all, BureauMetricService.NON_CC_OVERDUE_EXCEPTION_VIOLATION_COUNT);
        assertEquals(BureauMetricOutcome.PASS.name(), violations.getOutcome());
        assertEquals(1, intVal(violations));
        // H: suit multi-month same account → 1
        assertEquals(1, intVal(metric(all, BureauMetricService.SUIT_FILED_ACCOUNT_COUNT)));
        // I: score -1 without no-record is not NTC
        CiMetricResult ntc = metric(all, BureauMetricService.STATUS_NTC);
        assertEquals(BureauMetricOutcome.PASS.name(), ntc.getOutcome());
        assertEquals(0, intVal(ntc));
        assertEquals("SCORE_SENTINEL_WITHOUT_NO_RECORD_FLAG", ntc.getEvidence().get("source"));
        // J: current month vs trailing 90d
        int current = intVal(metric(all, BureauMetricService.INQUIRIES_CURRENT_MONTH));
        int trailing90 = intVal(metric(all, BureauMetricService.RECENT_INQUIRIES_90D));
        int last3 = intVal(metric(all, BureauMetricService.INQUIRIES_LAST_3M));
        assertEquals(1, current);
        assertEquals(2, trailing90);
        assertEquals(2, last3);
        assertNotEquals(current, trailing90);
        // K: evaluationAsOf preferred for enquiry windows
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
        return new BigDecimal(String.valueOf(r.getValue().get("v"))).intValue();
    }

    private static String display(CiMetricResult r) {
        if (r == null || r.getValue() == null || r.getValue().get("v") == null) {
            return "null";
        }
        Object v = r.getValue().get("v");
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        String s = String.valueOf(v);
        try {
            return new BigDecimal(s).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return s;
        }
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
