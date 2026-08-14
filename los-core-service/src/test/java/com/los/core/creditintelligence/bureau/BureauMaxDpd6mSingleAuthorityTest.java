package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.MaxDpdEvaluation;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.PaymentHistoryMonthInput;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService.PaymentMonth;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService.TradelineInput;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUREAU-CERTIFICATION-P0-DPD-SINGLE-AUTHORITY — one YearMonth trailing-window calculator
 * shared by Policy Test and live for {@code bureau.max_dpd_6m}.
 *
 * <p>Business definition (documented in {@link BureauMetricService#evaluateMaxDpd}):
 * asOf month included; inclusive window [{@code YearMonth(asOf)−5}, {@code YearMonth(asOf)}];
 * future months excluded; exact lower bound included; missing history → DATA_INSUFFICIENT not 0.
 */
class BureauMaxDpd6mSingleAuthorityTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 14);
    /** Inclusive window for asOf 2026-08-14: 2026-03 .. 2026-08 */
    private static final YearMonth EARLIEST = YearMonth.of(2026, 3);
    private static final YearMonth ASOF_YM = YearMonth.of(2026, 8);

    private final BureauMetricService liveCalc = new BureauMetricService(null, null);
    private final PolicyBureauMetricService studio = new PolicyBureauMetricService();
    private final PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();

    private static final Map<String, Object> HARD_MAX_DPD_LTE_30 =
            PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30));

    @Test
    void midMonth_windowBounds_documented() {
        MaxDpdEvaluation empty = liveCalc.evaluateMaxDpd(List.of(
                row("t1", YearMonth.of(2026, 3), 10)
        ), ASOF, 6);
        assertThat(empty.asOfMonth()).isEqualTo(ASOF_YM);
        assertThat(empty.earliestMonth()).isEqualTo(EARLIEST);
        assertThat(empty.windowMonths()).isEqualTo(6);
    }

    @Test
    void lowerBoundary_exactCutoffMonth_included() {
        // 2026-03 is earliest; dpd 60 must drive max
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 3), 60),
                row("t1", YearMonth.of(2026, 5), 0)
        );
        assertParity(rows, 60, "PASS", List.of("2026-03"), List.of());
    }

    @Test
    void justOutside_lowerBoundary_excluded() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 2), 90), // before window
                row("t1", YearMonth.of(2026, 4), 5)
        );
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        assertThat(live.maxDpd()).isEqualTo(5);
        assertThat(live.excluded()).anyMatch(o -> String.valueOf(o).contains("BEFORE_WINDOW"));
        assertParity(rows, 5, "PASS", List.of("2026-04"), List.of("2026-02"));
    }

    @Test
    void asOfMonth_included() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 8), 45),
                row("t1", YearMonth.of(2026, 5), 0)
        );
        assertParity(rows, 45, "PASS", List.of("2026-08"), List.of());
    }

    @Test
    void futureRow_afterAsOfMonth_excluded() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 9), 99), // future
                row("t1", YearMonth.of(2026, 6), 10)
        );
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        assertThat(live.maxDpd()).isEqualTo(10);
        assertThat(live.excluded()).anyMatch(o -> String.valueOf(o).contains("AFTER_ASOF_MONTH"));
        assertParity(rows, 10, "PASS", List.of("2026-06"), List.of("2026-09"));
    }

    @Test
    void multipleRowsSameMonth_maxTaken() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 5), 15),
                row("t1", YearMonth.of(2026, 5), 40)
        );
        assertParity(rows, 40, "PASS", List.of("2026-05"), List.of());
    }

    @Test
    void multiTradeline_maxAcross() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("closed-tl", YearMonth.of(2026, 4), 30),
                row("active-tl", YearMonth.of(2026, 7), 55)
        );
        assertParity(rows, 55, "PASS", List.of("2026-04", "2026-07"), List.of());
    }

    @Test
    void closedAccountHistory_stillIncluded() {
        // No CC/closed filter when history exists (canonical shared semantics)
        List<PaymentHistoryMonthInput> rows = List.of(
                row("closed-cc", YearMonth.of(2026, 3), 70)
        );
        assertParity(rows, 70, "PASS", List.of("2026-03"), List.of());
    }

    @Test
    void missingDpd_skippedNotZero() {
        List<PaymentHistoryMonthInput> rows = List.of(
                new PaymentHistoryMonthInput("t1", YearMonth.of(2026, 5), null),
                row("t1", YearMonth.of(2026, 6), 12)
        );
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        assertThat(live.maxDpd()).isEqualTo(12);
        assertThat(live.excluded()).anyMatch(o -> String.valueOf(o).contains("DPD_MISSING"));
        assertThat(live.outcome()).isEqualTo("PASS");
    }

    @Test
    void malformedPeriod_skipped() {
        List<PaymentHistoryMonthInput> rows = List.of(
                PaymentHistoryMonthInput.of("t1", null, 99),
                row("t1", YearMonth.of(2026, 5), 8)
        );
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        assertThat(live.maxDpd()).isEqualTo(8);
        assertThat(live.excluded()).anyMatch(o -> String.valueOf(o).contains("MALFORMED"));
    }

    @Test
    void emptyHistory_dataInsufficient_notZero() {
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(List.of(), ASOF, 6);
        Map<String, Object> studioMap = studio.maxDpd6m(List.of(), ASOF);
        assertThat(live.outcome()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(live.maxDpd()).isNull();
        assertThat(studioMap.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(studioMap.get("v")).isNull();
    }

    @Test
    void historyPresentButOutsideWindow_passZero() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2025, 1), 90)
        );
        assertParity(rows, 0, "PASS", List.of(), List.of("2025-01"));
    }

    @Test
    void cleanHistory_zeroWithPassQuality() {
        List<PaymentHistoryMonthInput> rows = List.of(
                row("t1", YearMonth.of(2026, 5), 0),
                row("t1", YearMonth.of(2026, 6), 0)
        );
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        assertThat(live.outcome()).isEqualTo("PASS");
        assertThat(live.maxDpd()).isEqualTo(0);
        assertThat(live.quality()).isEqualTo("OK");
        assertParity(rows, 0, "PASS", List.of("2026-05", "2026-06"), List.of());
    }

    @Test
    void firstOfMonthLocalDate_normalizesSameAsYearMonth() {
        List<PaymentHistoryMonthInput> fromDates = List.of(
                PaymentHistoryMonthInput.of("t1", LocalDate.of(2026, 3, 1), 60),
                PaymentHistoryMonthInput.of("t1", LocalDate.of(2026, 8, 1), 10)
        );
        List<PaymentHistoryMonthInput> fromYm = List.of(
                row("t1", YearMonth.of(2026, 3), 60),
                row("t1", YearMonth.of(2026, 8), 10)
        );
        assertThat(liveCalc.evaluateMaxDpd(fromDates, ASOF, 6).maxDpd())
                .isEqualTo(liveCalc.evaluateMaxDpd(fromYm, ASOF, 6).maxDpd());
    }

    @Test
    void hardRule_boundaries() {
        assertThat(hardDecision(0)).isEqualTo(PolicyDslInterpreterV1.PASS);
        assertThat(hardDecision(30)).isEqualTo(PolicyDslInterpreterV1.PASS);
        assertThat(hardDecision(31)).isEqualTo(PolicyDslInterpreterV1.FAIL);
        assertThat(hardDecision(60)).isEqualTo(PolicyDslInterpreterV1.FAIL);
        assertThat(hardDecisionMissing()).isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);
    }

    @Test
    void hardRule_policyTestAndLiveMetricParity() {
        for (int dpd : List.of(0, 30, 31, 60)) {
            List<PaymentHistoryMonthInput> rows = List.of(row("t1", YearMonth.of(2026, 5), dpd));
            MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
            Map<String, Object> studioMap = studioFromRows(rows);
            assertThat(studioMap.get("v")).isEqualTo(live.maxDpd());
            String liveDecision = hardDecision(live.maxDpd());
            String studioDecision = hardDecisionFromMetricMap(studioMap);
            assertThat(studioDecision).isEqualTo(liveDecision);
        }
        Map<String, Object> missingStudio = studio.maxDpd6m(List.of(), ASOF);
        MaxDpdEvaluation missingLive = liveCalc.evaluateMaxDpd(List.of(), ASOF, 6);
        assertThat(missingLive.outcome()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(hardDecisionFromMetricMap(missingStudio))
                .isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);
    }

    @Test
    void provenance_explainsMaxDpd() {
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(List.of(
                row("tl-a", YearMonth.of(2026, 3), 20),
                row("tl-b", YearMonth.of(2026, 7), 60)
        ), ASOF, 6);
        Map<String, Object> ev = live.evidence();
        assertThat(ev.get("calculator")).isEqualTo(BureauMetricService.MAX_DPD_CALCULATOR);
        assertThat(ev.get("calculatorVersion")).isEqualTo(BureauMetricService.MAX_DPD_CALCULATOR_VERSION);
        assertThat(ev.get("asOf")).isEqualTo(ASOF.toString());
        assertThat(ev.get("windowMonths")).isEqualTo(6);
        assertThat(ev.get("maxDpd")).isEqualTo(60);
        assertThat(live.included()).anyMatch(o -> String.valueOf(o).contains("tl-b")
                && String.valueOf(o).contains("60"));
    }

    @Test
    void gacatHonesty_singleCalculator_notProduction() {
        var d = CanonicalParameterRegistry.shared().findById("bureau.max_dpd_6m").orElseThrow();
        assertThat(d.existingImplementationBinding()).isEqualTo("BureauMetricService.evaluateMaxDpd");
        assertThat(d.calculationSummary()).containsIgnoringCase("YearMonth");
        assertThat(d.capability().productionReady()).isFalse();
        Map<String, Object> exec = ParameterExecutabilitySupport.evaluate("bureau.max_dpd_6m");
        assertThat(exec.get("policyTestReady")).isEqualTo(true);
        assertThat(exec.get("productionReady")).isEqualTo(false);
        assertThat(exec.get("calculatorBinding")).isEqualTo("BureauMetricService.evaluateMaxDpd");
    }

    @Test
    void singleAuthority_noThirdCalculator() {
        // Studio delegates: same calculator id in evidence
        Map<String, Object> studioMap = studio.maxDpd6m(List.of(
                new TradelineInput("PL", "Active", false, null, null,
                        List.of(new PaymentMonth(YearMonth.of(2026, 5), 22)), null, false)
        ), ASOF);
        @SuppressWarnings("unchecked")
        Map<String, Object> ev = (Map<String, Object>) studioMap.get("evidence");
        assertThat(ev.get("calculator")).isEqualTo(BureauMetricService.MAX_DPD_CALCULATOR);
    }

    private void assertParity(
            List<PaymentHistoryMonthInput> rows,
            int expectedMax,
            String expectedOutcome,
            List<String> expectIncludedPeriods,
            List<String> expectExcludedPeriods) {
        MaxDpdEvaluation live = liveCalc.evaluateMaxDpd(rows, ASOF, 6);
        Map<String, Object> studioMap = studioFromRows(rows);
        assertThat(live.outcome()).isEqualTo(expectedOutcome);
        assertThat(live.maxDpd()).isEqualTo(expectedMax);
        assertThat(studioMap.get("outcome")).isEqualTo(expectedOutcome);
        assertThat(((Number) studioMap.get("v")).intValue()).isEqualTo(expectedMax);
        for (String p : expectIncludedPeriods) {
            assertThat(live.included()).anyMatch(o -> String.valueOf(o).contains(p));
        }
        for (String p : expectExcludedPeriods) {
            assertThat(live.excluded()).anyMatch(o -> String.valueOf(o).contains(p));
        }
    }

    private Map<String, Object> studioFromRows(List<PaymentHistoryMonthInput> rows) {
        // One tradeline per distinct ref; PaymentMonth cannot express null DPD — filter those for studio path
        Map<String, List<PaymentMonth>> byRef = new java.util.LinkedHashMap<>();
        for (PaymentHistoryMonthInput r : rows) {
            if (r.period() == null || r.dpd() == null) {
                continue;
            }
            byRef.computeIfAbsent(r.tradelineRef(), k -> new java.util.ArrayList<>())
                    .add(new PaymentMonth(r.period(), r.dpd()));
        }
        if (byRef.isEmpty() && (rows == null || rows.isEmpty())) {
            return studio.maxDpd6m(List.of(), ASOF);
        }
        List<TradelineInput> tls = byRef.entrySet().stream()
                .map(e -> new TradelineInput("PL", "Active", false, null, null, e.getValue(), null, false))
                .toList();
        return studio.maxDpd6m(tls, ASOF);
    }

    private String hardDecision(Integer maxDpd) {
        Map<String, Object> metrics = maxDpd == null
                ? Map.of()
                : Map.of("bureau.max_dpd_6m", maxDpd);
        return interp.evaluate(HARD_MAX_DPD_LTE_30,
                new PolicyDslInterpreterV1.EvaluationContext(
                        metrics, Map.of(), Map.of(), Map.of(), null, PolicyDslInterpreterV1.DATA_INSUFFICIENT));
    }

    private String hardDecisionMissing() {
        Map<String, Object> metric = new java.util.LinkedHashMap<>();
        metric.put("outcome", "DATA_INSUFFICIENT");
        metric.put("v", null);
        return interp.evaluate(HARD_MAX_DPD_LTE_30,
                new PolicyDslInterpreterV1.EvaluationContext(
                        Map.of("bureau.max_dpd_6m", metric),
                        Map.of(), Map.of(), Map.of(), null, PolicyDslInterpreterV1.DATA_INSUFFICIENT));
    }

    private String hardDecisionFromMetricMap(Map<String, Object> metricMap) {
        return interp.evaluate(HARD_MAX_DPD_LTE_30,
                new PolicyDslInterpreterV1.EvaluationContext(
                        Map.of("bureau.max_dpd_6m", metricMap),
                        Map.of(), Map.of(), Map.of(), null, PolicyDslInterpreterV1.DATA_INSUFFICIENT));
    }

    private static PaymentHistoryMonthInput row(String ref, YearMonth ym, int dpd) {
        return new PaymentHistoryMonthInput(ref, ym, dpd);
    }
}
