package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.CcOverdueInput;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.PaymentHistoryMonthInput;
import com.los.core.creditintelligence.bureau.service.BureauMetricService.ScalarEvaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure Equifax derived-metric calculators used by {@link BureauMetricService}.
 */
final class BureauDerivedMetricCalculator {

    private BureauDerivedMetricCalculator() {
    }

    record AsOfResolution(LocalDate asOf, String source) {}

    static AsOfResolution resolveAsOf(
            LocalDate reportDate, Map<String, Object> reportData, boolean enquiryWindow) {
        LocalDate evalAsOf = parseEvaluationAsOf(reportData);
        if (enquiryWindow) {
            if (evalAsOf != null) {
                return new AsOfResolution(evalAsOf, "EVALUATION_AS_OF");
            }
            if (reportDate != null) {
                return new AsOfResolution(reportDate, "REPORT_DATE");
            }
        } else {
            if (reportDate != null) {
                return new AsOfResolution(reportDate, "REPORT_DATE");
            }
            if (evalAsOf != null) {
                return new AsOfResolution(evalAsOf, "EVALUATION_AS_OF");
            }
        }
        return new AsOfResolution(LocalDate.now(), "WALL_CLOCK_FALLBACK");
    }

    static LocalDate parseEvaluationAsOf(Map<String, Object> reportData) {
        if (reportData == null) {
            return null;
        }
        Object raw = reportData.get("evaluationAsOf");
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        if (s.length() >= 10 && s.charAt(4) == '-') {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
        return com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor.parseFlexibleDate(s);
    }

    static ScalarEvaluation inquiriesCurrentMonth(List<LocalDate> dates, LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("window", "CURRENT_MONTH");
        if (asOf == null) {
            evidence.put("reason", "ASOF_MISSING");
            return di(evidence);
        }
        YearMonth current = YearMonth.from(asOf);
        evidence.put("asOf", asOf.toString());
        evidence.put("asOfMonth", current.toString());
        if (dates == null) {
            evidence.put("reason", "Inquiries missing");
            return di(evidence);
        }
        int count = 0;
        for (LocalDate d : dates) {
            if (d != null && YearMonth.from(d).equals(current)) {
                count++;
            }
        }
        evidence.put("count", count);
        return pass(count, evidence);
    }

    static ScalarEvaluation inquiriesLast3Months(List<LocalDate> dates, LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("window", "TRAILING_3_CALENDAR_MONTHS");
        if (asOf == null) {
            evidence.put("reason", "ASOF_MISSING");
            return di(evidence);
        }
        LocalDate start = asOf.minusMonths(3).withDayOfMonth(1);
        evidence.put("asOf", asOf.toString());
        evidence.put("windowStart", start.toString());
        if (dates == null) {
            evidence.put("reason", "Inquiries missing");
            return di(evidence);
        }
        int count = 0;
        for (LocalDate d : dates) {
            if (d != null && !d.isBefore(start) && !d.isAfter(asOf)) {
                count++;
            }
        }
        evidence.put("count", count);
        return pass(count, evidence);
    }

    static ScalarEvaluation dpdPlusCount(
            List<PaymentHistoryMonthInput> rows, LocalDate asOf, int windowMonths, int threshold) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("threshold", threshold);
        evidence.put("windowMonths", windowMonths);
        evidence.put("grain", "DISTINCT_ACCOUNT_MONTH");
        evidence.put("authority", "DaysPastDue numeric only");
        if (windowMonths < 1) {
            throw new IllegalArgumentException("windowMonths must be >= 1");
        }
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        YearMonth asOfYm = YearMonth.from(effectiveAsOf);
        YearMonth earliest = asOfYm.minusMonths(windowMonths - 1L);
        evidence.put("asOf", effectiveAsOf.toString());
        evidence.put("asOfMonth", asOfYm.toString());
        evidence.put("earliestMonth", earliest.toString());
        evidence.put("windowSemantics", "INCLUSIVE_YEARMONTH_TRAILING_INCLUDING_ASOF_MONTH");

        if (rows == null || rows.isEmpty()) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return di(evidence);
        }
        Set<String> keys = new HashSet<>();
        boolean anyValidPeriod = false;
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        for (PaymentHistoryMonthInput row : rows) {
            if (row == null) {
                continue;
            }
            String ref = row.tradelineRef() != null ? row.tradelineRef() : "";
            if (row.period() == null) {
                excluded.add(Map.of("ref", ref, "reason", "MALFORMED_OR_MISSING_PERIOD"));
                continue;
            }
            anyValidPeriod = true;
            YearMonth period = row.period();
            if (period.isBefore(earliest)) {
                excluded.add(Map.of("ref", ref, "period", period.toString(), "reason", "BEFORE_WINDOW"));
                continue;
            }
            if (period.isAfter(asOfYm)) {
                excluded.add(Map.of("ref", ref, "period", period.toString(), "reason", "AFTER_ASOF_MONTH"));
                continue;
            }
            if (row.dpd() == null) {
                excluded.add(Map.of("ref", ref, "period", period.toString(), "reason", "DPD_MISSING"));
                continue;
            }
            if (row.dpd() < threshold) {
                excluded.add(Map.of(
                        "ref", ref, "period", period.toString(), "dpd", row.dpd(), "reason", "BELOW_THRESHOLD"));
                continue;
            }
            String key = ref + "|" + period;
            if (keys.add(key)) {
                included.add(Map.of("ref", ref, "period", period.toString(), "dpd", row.dpd(), "key", key));
            }
        }
        if (!anyValidPeriod) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return di(evidence, included, excluded);
        }
        evidence.put("count", keys.size());
        evidence.put("includedCount", included.size());
        return pass(keys.size(), evidence, included, excluded);
    }

    static ScalarEvaluation monthsSinceLastDelinquency(List<PaymentHistoryMonthInput> rows, LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now();
        YearMonth asOfYm = YearMonth.from(effectiveAsOf);
        evidence.put("asOf", effectiveAsOf.toString());
        evidence.put("asOfMonth", asOfYm.toString());
        if (rows == null || rows.isEmpty()) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return di(evidence);
        }
        boolean anyPeriod = false;
        YearMonth latestDq = null;
        for (PaymentHistoryMonthInput row : rows) {
            if (row == null || row.period() == null) {
                continue;
            }
            anyPeriod = true;
            if (row.dpd() != null && row.dpd() > 0) {
                if (latestDq == null || row.period().isAfter(latestDq)) {
                    latestDq = row.period();
                }
            }
        }
        if (!anyPeriod) {
            evidence.put("reason", "PAYMENT_HISTORY_MISSING");
            return di(evidence);
        }
        if (latestDq == null) {
            evidence.put("reason", "NO_DELINQUENCY");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("v", null);
            return new ScalarEvaluation(
                    BureauMetricOutcome.PASS.name(), null, "OK", evidence, List.of(), List.of(), value);
        }
        int months = monthDiff(latestDq, asOfYm);
        evidence.put("lastDelinquencyMonth", latestDq.toString());
        evidence.put("months", months);
        return pass(months, evidence);
    }

    static ScalarEvaluation ccOverdueAmount(List<CcOverdueInput> inputs) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("aggregation", "MAX");
        evidence.put("nullOverdueTreatedAsZero", true);
        if (inputs == null) {
            evidence.put("reason", "No tradelines");
            return di(evidence);
        }
        BigDecimal max = BigDecimal.ZERO;
        int ccCount = 0;
        List<Object> included = new ArrayList<>();
        for (CcOverdueInput in : inputs) {
            if (in == null || in.duplicate() || !in.creditCard()) {
                continue;
            }
            ccCount++;
            BigDecimal od = in.overdueAmount() == null ? BigDecimal.ZERO : in.overdueAmount();
            if (od.compareTo(max) > 0) {
                max = od;
            }
            included.add(Map.of("overdue", od.toPlainString()));
        }
        evidence.put("ccTradelineCount", ccCount);
        if (ccCount == 0) {
            evidence.put("reason", "NO_CC_TRADELINES");
        }
        return pass(max, evidence, included, List.of());
    }

    static ScalarEvaluation ccUtilisation(List<CiBureauTradeline> tradelines) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("eligible", "CREDIT_CARD + LIVE + not duplicate");
        evidence.put("unit", "RATIO");
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        BigDecimal sumBal = BigDecimal.ZERO;
        BigDecimal sumLimit = BigDecimal.ZERO;
        int eligible = 0;
        for (CiBureauTradeline t : tradelines) {
            if (t == null || t.getDuplicateOfTradelineId() != null) {
                continue;
            }
            if (!isCreditCard(t) || !Boolean.TRUE.equals(t.getIsLive())) {
                continue;
            }
            eligible++;
            if (t.getHighCredit() == null) {
                evidence.put("reason", "CREDIT_LIMIT_MISSING");
                evidence.put("missingLimitRef", refOf(t));
                return di(evidence);
            }
            BigDecimal bal = t.getCurrentBalance() != null ? t.getCurrentBalance() : BigDecimal.ZERO;
            sumBal = sumBal.add(bal);
            sumLimit = sumLimit.add(t.getHighCredit());
        }
        evidence.put("eligibleCount", eligible);
        evidence.put("sumBalance", sumBal.toPlainString());
        evidence.put("sumLimit", sumLimit.toPlainString());
        if (eligible == 0 || sumLimit.compareTo(BigDecimal.ZERO) == 0) {
            evidence.put("reason", eligible == 0 ? "NO_ELIGIBLE_CC" : "SUM_LIMIT_ZERO");
            return di(evidence);
        }
        BigDecimal ratio = sumBal.divide(sumLimit, 8, RoundingMode.HALF_UP);
        evidence.put("ratio", ratio.toPlainString());
        return pass(ratio, evidence);
    }

    static ScalarEvaluation panDistinctCount(Map<String, Object> reportData) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("valueContainsPanStrings", false);
        if (reportData == null
                || (!reportData.containsKey("panIds") && !reportData.containsKey("panId"))) {
            evidence.put("reason", "NO_PAN_EXTRACTION");
            return di(evidence);
        }
        Set<String> distinct = new HashSet<>();
        Object listObj = reportData.get("panIds");
        if (listObj instanceof List<?> list) {
            for (Object o : list) {
                String n = normalizePan(o);
                if (n != null) {
                    distinct.add(n);
                }
            }
        }
        if (distinct.isEmpty() && reportData.containsKey("panId")) {
            String n = normalizePan(reportData.get("panId"));
            if (n != null) {
                distinct.add(n);
            }
        }
        if (distinct.isEmpty()) {
            evidence.put("reason", "NO_PAN_NODES");
            return pass(0, evidence);
        }
        evidence.put("distinctCount", distinct.size());
        return pass(distinct.size(), evidence);
    }

    static ScalarEvaluation vintageOldestAndAverage(
            List<CiBureauTradeline> tradelines, LocalDate asOf, boolean oldest) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("includeClosed", true);
        evidence.put("skipNullOpenDates", true);
        evidence.put("skipFutureOpenDates", true);
        evidence.put("monthDiff", "YearMonth");
        if (asOf == null) {
            evidence.put("reason", "ASOF_MISSING");
            return di(evidence);
        }
        YearMonth asOfYm = YearMonth.from(asOf);
        evidence.put("asOf", asOf.toString());
        evidence.put("asOfMonth", asOfYm.toString());
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        List<Integer> ages = new ArrayList<>();
        List<Object> included = new ArrayList<>();
        List<Object> excluded = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t == null) {
                continue;
            }
            String ref = refOf(t);
            if (t.getDuplicateOfTradelineId() != null) {
                excluded.add(Map.of("ref", ref, "reason", "DUPLICATE"));
                continue;
            }
            if (t.getOpenedDate() == null) {
                excluded.add(Map.of("ref", ref, "reason", "NULL_OPEN_DATE"));
                continue;
            }
            if (t.getOpenedDate().isAfter(asOf)) {
                excluded.add(Map.of("ref", ref, "reason", "FUTURE_OPEN_DATE"));
                continue;
            }
            int months = monthDiff(YearMonth.from(t.getOpenedDate()), asOfYm);
            ages.add(months);
            included.add(Map.of("ref", ref, "months", months, "openedDate", t.getOpenedDate().toString()));
        }
        if (ages.isEmpty()) {
            evidence.put("reason", "NO_OPENABLE_TRADELINES");
            return di(evidence, included, excluded);
        }
        if (oldest) {
            int max = ages.stream().mapToInt(Integer::intValue).max().orElse(0);
            evidence.put("oldestMonths", max);
            return pass(max, evidence, included, excluded);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int a : ages) {
            sum = sum.add(BigDecimal.valueOf(a));
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(ages.size()), 2, RoundingMode.HALF_UP);
        evidence.put("accountCount", ages.size());
        evidence.put("averageMonths", avg.toPlainString());
        return pass(avg, evidence, included, excluded);
    }

    static ScalarEvaluation overdueAmountNonCc(List<CiBureauTradeline> tradelines) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("aggregation", "SUM");
        evidence.put("filter", "non-CC overdueAmount > 0");
        evidence.put("explanatory", true);
        evidence.put("notBreException", true);
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t == null || t.getDuplicateOfTradelineId() != null || isCreditCard(t)) {
                continue;
            }
            BigDecimal od = t.getOverdueAmount();
            if (od != null && od.compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(od);
                included.add(Map.of("ref", refOf(t), "overdue", od.toPlainString()));
            }
        }
        evidence.put("accountCount", included.size());
        return pass(sum, evidence, included, List.of());
    }

    static ScalarEvaluation overdueAgeMonths(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("aggregation", "MAX");
        evidence.put("explanatory", true);
        evidence.put("ageFrom", "overdue EVENT month from payment history");
        if (asOf == null) {
            evidence.put("reason", "ASOF_MISSING");
            return di(evidence);
        }
        YearMonth asOfYm = YearMonth.from(asOf);
        evidence.put("asOfMonth", asOfYm.toString());
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        Integer maxAge = null;
        int overdueAccounts = 0;
        int missingEvent = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t == null || t.getDuplicateOfTradelineId() != null || isCreditCard(t)) {
                continue;
            }
            BigDecimal od = t.getOverdueAmount();
            if (od == null || od.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            overdueAccounts++;
            YearMonth eventYm = overdueEventYm(historyOf(t, history), true);
            if (eventYm == null) {
                missingEvent++;
                continue;
            }
            int age = monthDiff(eventYm, asOfYm);
            if (maxAge == null || age > maxAge) {
                maxAge = age;
            }
            included.add(Map.of("ref", refOf(t), "eventYm", eventYm.toString(), "ageMonths", age));
        }
        evidence.put("overdueNonCcCount", overdueAccounts);
        evidence.put("missingEventCount", missingEvent);
        if (overdueAccounts == 0) {
            evidence.put("reason", "NO_NON_CC_OVERDUE");
            return pass(0, evidence, included, List.of());
        }
        if (maxAge == null) {
            evidence.put("reason", "NO_EVENT_DATE");
            return di(evidence, included, List.of());
        }
        return pass(maxAge, evidence, included, List.of());
    }

    static ScalarEvaluation creditAfterOverdueExists(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            LocalDate asOf) {
        Map<String, Object> evidence = creditAfterEvidenceBase(asOf);
        YearMonth globalEvent = globalMaxOverdueEvent(tradelines, history);
        if (globalEvent == null) {
            evidence.put("reason", "NO_OVERDUE_EVENT");
            return pass(false, evidence);
        }
        LocalDate eventDate = globalEvent.atDay(1);
        evidence.put("globalMaxOverdueEventYm", globalEvent.toString());
        boolean exists = false;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : safeTls(tradelines)) {
            if (t.getDuplicateOfTradelineId() != null || t.getOpenedDate() == null) {
                continue;
            }
            if (t.getOpenedDate().isAfter(eventDate)) {
                exists = true;
                included.add(Map.of("ref", refOf(t), "openedDate", t.getOpenedDate().toString()));
            }
        }
        evidence.put("exists", exists);
        return pass(exists, evidence, included, List.of());
    }

    static ScalarEvaluation creditAfterOverdueCleanHistoryMonths(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            LocalDate asOf) {
        Map<String, Object> evidence = creditAfterEvidenceBase(asOf);
        evidence.put("notAccountSafeForBre", true);
        evidence.put("explanatoryHelper", true);
        evidence.put("note", "MAX consecutive CLEAN streak on any later loan after global max overdue event; "
                + "NOT account-safe for BRE exception 1∧2∧3∧4");
        YearMonth globalEvent = globalMaxOverdueEvent(tradelines, history);
        if (globalEvent == null) {
            evidence.put("reason", "NO_OVERDUE_EVENT");
            return pass(0, evidence);
        }
        LocalDate eventDate = globalEvent.atDay(1);
        evidence.put("globalMaxOverdueEventYm", globalEvent.toString());
        int maxStreak = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : safeTls(tradelines)) {
            if (t.getDuplicateOfTradelineId() != null || t.getOpenedDate() == null) {
                continue;
            }
            if (!t.getOpenedDate().isAfter(eventDate)) {
                continue;
            }
            int streak = BureauCleanMonth.maxConsecutiveCleanStreak(
                    historyOf(t, history), YearMonth.from(t.getOpenedDate()));
            if (streak > maxStreak) {
                maxStreak = streak;
            }
            included.add(Map.of("ref", refOf(t), "cleanStreak", streak));
        }
        evidence.put("maxCleanHistoryMonths", maxStreak);
        return pass(maxStreak, evidence, included, List.of());
    }

    static ScalarEvaluation nonCcOverdueExceptionViolationCount(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("exception", "1∧2∧3∧4");
        evidence.put("condition1", "ageMonths > 12");
        evidence.put("condition2", "new loan opened after overdue event");
        evidence.put("condition3", "ALL later loans have >= 6 consecutive CLEAN months from first reported");
        evidence.put("condition4", "overdueAmount < 1500");
        evidence.put("cleanPredicate", "BureauCleanMonth");
        if (asOf == null) {
            evidence.put("reason", "ASOF_MISSING");
            return di(evidence);
        }
        YearMonth asOfYm = YearMonth.from(asOf);
        evidence.put("asOfMonth", asOfYm.toString());
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        List<CiBureauTradeline> nonDup = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t != null && t.getDuplicateOfTradelineId() == null) {
                nonDup.add(t);
            }
        }
        int violations = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : nonDup) {
            if (isCreditCard(t)) {
                continue;
            }
            BigDecimal od = t.getOverdueAmount();
            if (od == null || od.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ref", refOf(t));
            row.put("overdueAmount", od.toPlainString());
            YearMonth eventYm = overdueEventYm(historyOf(t, history), true);
            if (eventYm == null) {
                violations++;
                row.put("fails", true);
                row.put("reason", "NO_EVENT_DATE");
                included.add(row);
                continue;
            }
            int age = monthDiff(eventYm, asOfYm);
            boolean c1 = age > 12;
            LocalDate eventDate = eventYm.atDay(1);
            List<CiBureauTradeline> later = new ArrayList<>();
            for (CiBureauTradeline other : nonDup) {
                if (other == t || other.getOpenedDate() == null) {
                    continue;
                }
                if (other.getOpenedDate().isAfter(eventDate)) {
                    later.add(other);
                }
            }
            boolean c2 = !later.isEmpty();
            boolean c3 = true;
            if (c2) {
                for (CiBureauTradeline laterTl : later) {
                    int clean = BureauCleanMonth.consecutiveCleanFromFirstReported(
                            historyOf(laterTl, history), YearMonth.from(laterTl.getOpenedDate()));
                    if (clean < 6) {
                        c3 = false;
                        row.put("failedLaterLoan", refOf(laterTl));
                        row.put("laterCleanFromStart", clean);
                        break;
                    }
                }
            } else {
                c3 = false;
            }
            boolean c4 = od.compareTo(new BigDecimal("1500")) < 0;
            boolean pass = c1 && c2 && c3 && c4;
            row.put("eventYm", eventYm.toString());
            row.put("ageMonths", age);
            row.put("condition1", c1);
            row.put("condition2", c2);
            row.put("condition3", c3);
            row.put("condition4", c4);
            row.put("laterLoanCount", later.size());
            if (!pass) {
                violations++;
                row.put("fails", true);
            }
            included.add(row);
        }
        evidence.put("violationCount", violations);
        return pass(violations, evidence, included, List.of());
    }

    static ScalarEvaluation suitFiledAccountCount(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("starOrBlankNotAffirmative", true);
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        int count = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t == null || t.getDuplicateOfTradelineId() != null) {
                continue;
            }
            boolean flag = Boolean.TRUE.equals(t.getSuitFiled());
            boolean phSuit = false;
            for (PaymentHistoryMonthInput row : historyOf(t, history)) {
                if (BureauCleanMonth.isAffirmativeSuit(row.suitFiledStatus())) {
                    phSuit = true;
                    break;
                }
            }
            if (flag || phSuit) {
                count++;
                included.add(Map.of("ref", refOf(t), "tradelineFlag", flag, "paymentHistorySuit", phSuit));
            }
        }
        evidence.put("count", count);
        return pass(count, evidence, included, List.of());
    }

    static ScalarEvaluation equifaxAdverseAccountCount(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history,
            EquifaxRetailPaymentStatusVocabulary.Family family) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("starOrBlankNotAffirmative", true);
        evidence.put("accountCountedOnce", true);
        evidence.put("family", family.name());
        evidence.put("providerCodes", EquifaxRetailPaymentStatusVocabulary.codes(family));
        evidence.put("providerProvenance", EquifaxRetailPaymentStatusVocabulary.EVIDENCE);
        if (family == EquifaxRetailPaymentStatusVocabulary.Family.LOSS) {
            evidence.put("providerCode", "LOSS");
            evidence.put("canonicalConcept", "LSS");
        }
        if (family == EquifaxRetailPaymentStatusVocabulary.Family.PWOS) {
            evidence.put("notDerivedFromSettlementAmount", true);
            evidence.put("notDerivedFromGenericWriteOff", true);
        }
        if (tradelines == null) {
            evidence.put("reason", "NO_TRADELINES");
            return di(evidence);
        }
        int count = 0;
        List<Object> included = new ArrayList<>();
        for (CiBureauTradeline t : tradelines) {
            if (t == null || t.getDuplicateOfTradelineId() != null) {
                continue;
            }
            boolean accountHit = EquifaxRetailPaymentStatusVocabulary.matches(t.getAccountStatus(), family);
            String monthHit = null;
            for (PaymentHistoryMonthInput row : historyOf(t, history)) {
                if (row == null) {
                    continue;
                }
                // PaymentStatus only (providerRawStatus). AssetClassificationStatus must not
                // token-collide (e.g. ACS "Loss" vs PaymentStatus LOSS).
                if (EquifaxRetailPaymentStatusVocabulary.matches(row.providerRawStatus(), family)) {
                    monthHit = EquifaxRetailPaymentStatusVocabulary.token(row.providerRawStatus());
                    break;
                }
            }
            if (accountHit || monthHit != null) {
                count++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ref", refOf(t));
                row.put("accountLevel", accountHit);
                row.put("historyToken", monthHit);
                included.add(row);
            }
        }
        evidence.put("count", count);
        return pass(count, evidence, included, List.of());
    }

    static YearMonth overdueEventYm(List<PaymentHistoryMonthInput> rows, boolean fallbackAnyObservation) {
        YearMonth latestAdverse = null;
        YearMonth latestAny = null;
        for (PaymentHistoryMonthInput row : BureauCleanMonth.safe(rows)) {
            if (row == null || row.period() == null) {
                continue;
            }
            if (latestAny == null || row.period().isAfter(latestAny)) {
                latestAny = row.period();
            }
            boolean dpdHit = row.dpd() != null && row.dpd() > 0;
            boolean adverse = BureauCleanMonth.isAdverseMonthly(row.providerRawStatus())
                    || BureauCleanMonth.isAdverseMonthly(row.assetClassificationStatus());
            if (dpdHit || adverse) {
                if (latestAdverse == null || row.period().isAfter(latestAdverse)) {
                    latestAdverse = row.period();
                }
            }
        }
        if (latestAdverse != null) {
            return latestAdverse;
        }
        return fallbackAnyObservation ? latestAny : null;
    }

    static YearMonth globalMaxOverdueEvent(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        YearMonth max = null;
        for (CiBureauTradeline t : safeTls(tradelines)) {
            if (t.getDuplicateOfTradelineId() != null || isCreditCard(t)) {
                continue;
            }
            BigDecimal od = t.getOverdueAmount();
            if (od == null || od.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            YearMonth event = overdueEventYm(historyOf(t, history), true);
            if (event != null && (max == null || event.isAfter(max))) {
                max = event;
            }
        }
        return max;
    }

    static List<PaymentHistoryMonthInput> flattenHistory(
            List<CiBureauTradeline> tradelines,
            Map<UUID, List<PaymentHistoryMonthInput>> history) {
        List<PaymentHistoryMonthInput> rows = new ArrayList<>();
        for (CiBureauTradeline t : safeTls(tradelines)) {
            rows.addAll(historyOf(t, history));
        }
        return rows;
    }

    static List<PaymentHistoryMonthInput> toInputs(CiBureauTradeline t, List<CiBureauPaymentHistory> ph) {
        List<PaymentHistoryMonthInput> rows = new ArrayList<>();
        if (ph == null) {
            return rows;
        }
        String ref = refOf(t);
        for (CiBureauPaymentHistory row : ph) {
            if (row == null) {
                continue;
            }
            rows.add(PaymentHistoryMonthInput.of(
                    ref, row.getMonth(), row.getDpd(),
                    row.getProviderRawStatus(), row.getAssetClassificationStatus(), row.getSuitFiledStatus()));
        }
        return rows;
    }

    static List<LocalDate> inquiryDates(Map<String, Object> reportData) {
        if (reportData == null || !(reportData.get("inquiries") instanceof List<?> list)) {
            return null;
        }
        List<LocalDate> dates = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                dates.add(com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor
                        .parseFlexibleDate(m.get("inquiryDate") != null
                                ? String.valueOf(m.get("inquiryDate")) : null));
            } else if (o instanceof LocalDate ld) {
                dates.add(ld);
            }
        }
        return dates;
    }

    static boolean isCreditCard(CiBureauTradeline t) {
        return t != null && BureauProductCategory.CREDIT_CARD.name().equals(t.getProductCategory());
    }

    static int monthDiff(YearMonth from, YearMonth to) {
        return (int) ChronoUnit.MONTHS.between(from, to);
    }

    static String refOf(CiBureauTradeline t) {
        if (t.getId() != null) {
            return t.getId().toString();
        }
        return t.getProviderTradelineRef() != null ? t.getProviderTradelineRef() : t.getSourceReference();
    }

    private static List<PaymentHistoryMonthInput> historyOf(
            CiBureauTradeline t, Map<UUID, List<PaymentHistoryMonthInput>> history) {
        if (t == null || history == null || t.getId() == null) {
            return List.of();
        }
        List<PaymentHistoryMonthInput> rows = history.get(t.getId());
        return rows != null ? rows : List.of();
    }

    private static List<CiBureauTradeline> safeTls(List<CiBureauTradeline> tradelines) {
        return tradelines != null ? tradelines : List.of();
    }

    private static Map<String, Object> creditAfterEvidenceBase(LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("explanatory", true);
        evidence.put("notAccountSafeForBre", true);
        if (asOf != null) {
            evidence.put("asOf", asOf.toString());
        }
        return evidence;
    }

    private static String normalizePan(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    private static ScalarEvaluation pass(Object v, Map<String, Object> evidence) {
        return pass(v, evidence, List.of(), List.of());
    }

    private static ScalarEvaluation pass(
            Object v, Map<String, Object> evidence, List<Object> included, List<Object> excluded) {
        return new ScalarEvaluation(
                BureauMetricOutcome.PASS.name(), v, "OK", evidence, included, excluded, null);
    }

    private static ScalarEvaluation di(Map<String, Object> evidence) {
        return di(evidence, List.of(), List.of());
    }

    private static ScalarEvaluation di(
            Map<String, Object> evidence, List<Object> included, List<Object> excluded) {
        return new ScalarEvaluation(
                BureauMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                evidence, included, excluded, null);
    }
}
