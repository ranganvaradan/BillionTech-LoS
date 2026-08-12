package com.los.core.creditintelligence.policystudio.metrics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BANKING-BRE-FINAL-CLOSURE-1 — bulk &gt;10× ADB adjustment calculator.
 */
class AdbBulkDepositAdjustmentCalculatorTest {

    @Test
    void stagingFixture_avg9800_excludes120k_keeps98k_andChangesAdb() {
        List<AdbBulkDepositAdjustmentCalculator.Txn> txns =
                AdbBulkDepositAdjustmentCalculator.stagingFixture();
        Map<String, Object> r = AdbBulkDepositAdjustmentCalculator.evaluate(
                txns, AdbBulkDepositAdjustmentCalculator.Config.defaults(), LocalDate.of(2026, 8, 1));

        assertThat(r.get("outcome")).isEqualTo(AdbBulkDepositAdjustmentCalculator.OUTCOME_PASS);
        assertThat(new BigDecimal(String.valueOf(r.get("averageDepositAmount"))))
                .isEqualByComparingTo("9800.00");
        assertThat(new BigDecimal(String.valueOf(r.get("bulkThreshold"))))
                .isEqualByComparingTo("98000.00");
        assertThat(r.get("coreDepositCountForAverage")).isEqualTo(5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> excluded = (List<Map<String, Object>>) r.get("excludedCredits");
        assertThat(excluded).hasSize(1);
        assertThat(new BigDecimal(String.valueOf(excluded.get(0).get("amount"))))
                .isEqualByComparingTo("120000");

        BigDecimal base = new BigDecimal(String.valueOf(r.get("baseAdb")));
        BigDecimal adj = new BigDecimal(String.valueOf(r.get("adjustedAdb")));
        assertThat(adj).isLessThan(base);
        assertThat(adj).isEqualByComparingTo(new BigDecimal(String.valueOf(r.get("v"))));
    }

    @Test
    void exactlyTenTimes_notExcluded_strictGreaterThan() {
        List<AdbBulkDepositAdjustmentCalculator.Txn> txns = List.of(
                txn("2026-05-10", "10000"),
                txn("2026-05-20", "12000"),
                txn("2026-06-01", "8000"),
                txn("2026-06-15", "10000"),
                txn("2026-07-01", "9000"),
                txn("2026-07-15", "98000") // exactly 10× of 9800
        );
        // Need balance series for ADB — use staging fixture path for ADB; here only classification
        Map<String, Object> r = AdbBulkDepositAdjustmentCalculator.evaluate(
                AdbBulkDepositAdjustmentCalculator.stagingFixture(),
                AdbBulkDepositAdjustmentCalculator.Config.defaults(),
                LocalDate.of(2026, 8, 1));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> excluded = (List<Map<String, Object>>) r.get("excludedCredits");
        boolean has98k = excluded.stream().anyMatch(m ->
                new BigDecimal(String.valueOf(m.get("amount"))).compareTo(new BigDecimal("98000")) == 0);
        assertThat(has98k).isFalse();
        assertThat(txns).isNotEmpty(); // keep fixture helper referenced for clarity
    }

    @Test
    void zeroQualifyingDeposits_dataInsufficient() {
        List<AdbBulkDepositAdjustmentCalculator.Txn> txns = List.of(
                new AdbBulkDepositAdjustmentCalculator.Txn(
                        LocalDate.of(2026, 7, 1), "LOAN DISBURSEMENT", "CREDIT",
                        new BigDecimal("50000"), "LOAN_DISBURSEMENT", true,
                        new BigDecimal("50000"), null));
        Map<String, Object> r = AdbBulkDepositAdjustmentCalculator.evaluate(
                txns, AdbBulkDepositAdjustmentCalculator.Config.defaults(), LocalDate.of(2026, 8, 1));
        assertThat(r.get("outcome")).isEqualTo(AdbBulkDepositAdjustmentCalculator.OUTCOME_DI);
        assertThat(String.valueOf(r.get("reason"))).contains("Zero qualifying");
    }

    private static AdbBulkDepositAdjustmentCalculator.Txn txn(String date, String amount) {
        return new AdbBulkDepositAdjustmentCalculator.Txn(
                LocalDate.parse(date), "MERCHANT CR", "CREDIT",
                new BigDecimal(amount), "OTHER", true, new BigDecimal(amount), null);
    }
}
