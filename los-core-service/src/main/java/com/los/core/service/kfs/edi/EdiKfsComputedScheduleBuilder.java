package com.los.core.service.kfs.edi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a daily reducing-balance repayment schedule for EDI KFS when Encore LMS data
 * is unavailable (e.g. auth failure or account not yet opened).
 */
public final class EdiKfsComputedScheduleBuilder {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(IST);

    private EdiKfsComputedScheduleBuilder() {
    }

    /**
     * @param tenureDays number of daily instalments (EDI stores tenure magnitude in days)
     * @param startDate  first instalment date (typically sanction date + 1 day)
     */
    public static List<Map<String, Object>> buildRawSchedule(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            int tenureDays,
            BigDecimal dailyInstallment,
            LocalDate startDate) {
        if (principal == null || annualRatePercent == null || tenureDays <= 0 || startDate == null) {
            return List.of();
        }

        BigDecimal installment = dailyInstallment;
        if (installment == null || installment.compareTo(BigDecimal.ZERO) <= 0) {
            installment = calculateDailyInstallment(principal, annualRatePercent, tenureDays);
        }

        MathContext mc = new MathContext(12);
        BigDecimal dailyRate = annualRatePercent.divide(BigDecimal.valueOf(36500), mc);
        BigDecimal outstanding = principal.setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> rows = new ArrayList<>(tenureDays);
        LocalDate date = startDate;

        for (int day = 1; day <= tenureDays; day++) {
            BigDecimal interest = outstanding.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalComponent;
            BigDecimal instAmount;

            if (day == tenureDays) {
                principalComponent = outstanding;
                instAmount = principalComponent.add(interest).setScale(2, RoundingMode.HALF_UP);
                outstanding = BigDecimal.ZERO;
            } else {
                instAmount = installment.setScale(2, RoundingMode.HALF_UP);
                principalComponent = instAmount.subtract(interest).max(BigDecimal.ZERO);
                outstanding = outstanding.subtract(principalComponent).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequenceNum", day);
            row.put("valueDateStr", DATE_FMT.format(date.atStartOfDay(IST).toInstant()));
            row.put("installmentAmount", instAmount.toPlainString());
            row.put("interestAmount", interest);
            row.put("principalAmount", principalComponent);
            row.put("balance", outstanding.toPlainString());
            row.put("status", "COMPUTED");
            rows.add(row);

            date = date.plusDays(1);
        }
        return rows;
    }

    public static List<EdiKfsScheduleRow> build(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            int tenureDays,
            BigDecimal dailyInstallment,
            LocalDate startDate) {
        return EdiKfsScheduleResolver.mapRows(
                buildRawSchedule(principal, annualRatePercent, tenureDays, dailyInstallment, startDate));
    }

    /** Constant daily instalment on reducing balance (365-day year). */
    public static BigDecimal calculateDailyInstallment(
            BigDecimal principal, BigDecimal annualRatePercent, int tenureDays) {
        if (principal == null || annualRatePercent == null || tenureDays <= 0) {
            return BigDecimal.ZERO;
        }
        MathContext mc = new MathContext(12);
        BigDecimal dailyRate = annualRatePercent.divide(BigDecimal.valueOf(36500), mc);
        if (dailyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureDays), 2, RoundingMode.HALF_UP);
        }
        BigDecimal onePlusR = BigDecimal.ONE.add(dailyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureDays, mc);
        return principal.multiply(dailyRate).multiply(onePlusRPowN)
                .divide(onePlusRPowN.subtract(BigDecimal.ONE), mc)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
