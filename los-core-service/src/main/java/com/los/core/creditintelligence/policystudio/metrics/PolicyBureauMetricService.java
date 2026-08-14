package com.los.core.creditintelligence.policystudio.metrics;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.BureauStatusNormalizer;
import com.los.core.creditintelligence.bureau.service.BureauStatusNormalizer.CanonicalStatus;
import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Studio bureau BRE metric helpers. CLEAN history requires customer vocabulary resolution.
 */
public class PolicyBureauMetricService {

    public static final String OUTCOME_PASS = "PASS";
    public static final String OUTCOME_DI = "DATA_INSUFFICIENT";

    private final BureauStatusNormalizer statusNormalizer;

    public PolicyBureauMetricService(BureauStatusNormalizer statusNormalizer) {
        this.statusNormalizer = statusNormalizer != null ? statusNormalizer : new BureauStatusNormalizer();
    }

    public PolicyBureauMetricService() {
        this(new BureauStatusNormalizer());
    }

    public record PaymentMonth(YearMonth month, int dpd) {}
    public record TradelineInput(
            String productCategory,
            String statusRaw,
            boolean creditCard,
            BigDecimal overdueAmount,
            BigDecimal writeOffAmount,
            List<PaymentMonth> paymentHistory,
            LocalDate openDate,
            boolean creditAfterOverdue
    ) {}
    public record InquiryInput(LocalDate date) {}

    public Map<String, Object> maxDpd6m(List<TradelineInput> tradelines, LocalDate asOf) {
        // Single authority: BureauMetricService.evaluateMaxDpd (YearMonth trailing window).
        BureauMetricService shared = new BureauMetricService(null, null);
        if (tradelines == null) {
            return shared.evaluateMaxDpd(List.of(), asOf, 6).toStudioMap();
        }
        List<BureauMetricService.PaymentHistoryMonthInput> rows = new ArrayList<>();
        int i = 0;
        for (TradelineInput t : tradelines) {
            String ref = "studio-" + (i++);
            if (t.paymentHistory() == null || t.paymentHistory().isEmpty()) {
                continue;
            }
            for (PaymentMonth pm : t.paymentHistory()) {
                if (pm == null) {
                    continue;
                }
                rows.add(new BureauMetricService.PaymentHistoryMonthInput(
                        ref, pm.month(), pm.dpd()));
            }
        }
        return shared.evaluateMaxDpd(rows, asOf, 6).toStudioMap();
    }

    public Map<String, Object> inquiriesCurrentMonth(List<InquiryInput> inquiries, EvaluationClock clock) {
        EvaluationClock c = clock != null ? clock
                : new FixedEvaluationClock(Instant.parse("2024-06-15T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate today = c.today();
        YearMonth current = YearMonth.from(today);
        if (inquiries == null) {
            return di("Inquiries missing");
        }
        int count = 0;
        for (InquiryInput i : inquiries) {
            if (i.date() != null && YearMonth.from(i.date()).equals(current)) {
                count++;
            }
        }
        Map<String, Object> r = pass(count);
        r.put("asOf", today.toString());
        r.put("clockZone", c.zone().getId());
        return r;
    }

    /** Trailing 3 calendar months (distinct from current month and from trailing 90 days). */
    public Map<String, Object> inquiriesLast3Months(List<InquiryInput> inquiries, EvaluationClock clock) {
        EvaluationClock c = clock != null ? clock
                : new FixedEvaluationClock(Instant.parse("2024-06-15T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate today = c.today();
        LocalDate start = today.minusMonths(3).withDayOfMonth(1);
        if (inquiries == null) {
            return di("Inquiries missing");
        }
        int count = 0;
        for (InquiryInput i : inquiries) {
            if (i.date() != null && !i.date().isBefore(start) && !i.date().isAfter(today)) {
                count++;
            }
        }
        Map<String, Object> r = pass(count);
        r.put("asOf", today.toString());
        r.put("windowStart", start.toString());
        r.put("clockZone", c.zone().getId());
        return r;
    }

    public Map<String, Object> consumerScore(Integer score) {
        if (score == null) {
            return di("Score missing");
        }
        return pass(score);
    }

    public Map<String, Object> consumerNtc(String statusRaw, Boolean explicitNtc) {
        return consumerNtc(null, statusRaw, explicitNtc, null);
    }

    /**
     * Studio NTC — delegates to {@link BureauMetricService#evaluateStatusNtc} (single authority).
     */
    public Map<String, Object> consumerNtc(
            Integer score,
            String statusRaw,
            Boolean explicitNtc,
            Boolean noRecordFound) {
        BureauMetricService shared = new BureauMetricService(null, null);
        return shared.evaluateStatusNtc(score, explicitNtc, noRecordFound, statusRaw).toStudioMap();
    }

    public Map<String, Object> writeoffCounts(List<TradelineInput> tradelines) {
        // Delegate to shared BureauMetricService calculator (single business implementation).
        BureauMetricService shared = new BureauMetricService(null, null);
        if (tradelines == null) {
            BureauMetricService.WriteoffCountResult di =
                    shared.evaluateWriteoffInputs(null, new LinkedHashMap<>());
            return Map.of(
                    BureauMetricService.WRITEOFF_NON_CC,
                    di.toStudioMap(BureauMetricService.WRITEOFF_NON_CC, null),
                    BureauMetricService.WRITEOFF_CC,
                    di.toStudioMap(BureauMetricService.WRITEOFF_CC, null));
        }
        List<BureauMetricService.WriteoffAccountInput> inputs = new ArrayList<>();
        int i = 0;
        for (TradelineInput t : tradelines) {
            String cat = t.productCategory();
            Boolean ccExplicit = t.creditCard();
            // Unknown product category must not silently count as non-CC when write-off is present.
            if (cat != null && "UNKNOWN".equalsIgnoreCase(cat.trim())) {
                ccExplicit = null;
            }
            inputs.add(BureauMetricService.WriteoffAccountInput.fromStudio(
                    "studio-" + (i++),
                    t.statusRaw(),
                    t.writeOffAmount(),
                    cat,
                    ccExplicit));
        }
        BureauMetricService.WriteoffCountResult r =
                shared.evaluateWriteoffInputs(inputs, new LinkedHashMap<>());
        return Map.of(
                BureauMetricService.WRITEOFF_NON_CC,
                r.toStudioMap(BureauMetricService.WRITEOFF_NON_CC, r.nonCcCount()),
                BureauMetricService.WRITEOFF_CC,
                r.toStudioMap(BureauMetricService.WRITEOFF_CC, r.ccCount()));
    }

    public Map<String, Object> overdueMetrics(List<TradelineInput> tradelines) {
        if (tradelines == null) {
            return Map.of(
                    "bureau.accounts.overdue_non_cc", di("No tradelines"),
                    "bureau.accounts.credit_card_overdue_max", di("No tradelines"));
        }
        int overdueNonCc = 0;
        BigDecimal ccMax = BigDecimal.ZERO;
        boolean anyCc = false;
        for (TradelineInput t : tradelines) {
            BigDecimal od = t.overdueAmount() == null ? BigDecimal.ZERO : t.overdueAmount();
            if (t.creditCard()) {
                anyCc = true;
                if (od.compareTo(ccMax) > 0) {
                    ccMax = od;
                }
            } else if (od.compareTo(BigDecimal.ZERO) > 0) {
                overdueNonCc++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bureau.accounts.overdue_non_cc", pass(overdueNonCc));
        out.put("bureau.accounts.credit_card_overdue_max", anyCc || !tradelines.isEmpty() ? pass(ccMax) : di("No CC tradelines"));
        return out;
    }

    public Map<String, Object> statusCounts(List<TradelineInput> tradelines) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (tradelines == null) {
            for (String code : List.of(
                    "bureau.accounts.settled_count", "bureau.accounts.restructured_count",
                    "bureau.accounts.legal_suit_count", "bureau.accounts.dbt_count",
                    "bureau.accounts.pwos_count", "bureau.accounts.lss_count",
                    "bureau.accounts.account_sold_count")) {
                out.put(code, di("No tradelines"));
            }
            return out;
        }
        Map<CanonicalStatus, Integer> counts = new LinkedHashMap<>();
        for (CanonicalStatus s : CanonicalStatus.values()) {
            counts.put(s, 0);
        }
        for (TradelineInput t : tradelines) {
            CanonicalStatus st = statusNormalizer.normalize(t.statusRaw());
            counts.put(st, counts.get(st) + 1);
        }
        out.put("bureau.accounts.settled_count", pass(counts.get(CanonicalStatus.SETTLED)));
        out.put("bureau.accounts.restructured_count", pass(counts.get(CanonicalStatus.RESTRUCTURED)));
        out.put("bureau.accounts.legal_suit_count", pass(counts.get(CanonicalStatus.LEGAL_SUIT)));
        out.put("bureau.accounts.dbt_count", pass(counts.get(CanonicalStatus.DBT)));
        out.put("bureau.accounts.pwos_count", pass(counts.get(CanonicalStatus.PWOS)));
        out.put("bureau.accounts.lss_count", pass(counts.get(CanonicalStatus.LSS)));
        out.put("bureau.accounts.account_sold_count", pass(counts.get(CanonicalStatus.ACCOUNT_SOLD)));
        return out;
    }

    public Map<String, Object> panCount(Integer panCount) {
        if (panCount == null) {
            return di("PAN count unknown");
        }
        return pass(panCount);
    }

    public Map<String, Object> overdueAgeMonths(Integer ageMonths) {
        if (ageMonths == null) {
            return di("Overdue age unknown");
        }
        return pass(ageMonths);
    }

    public Map<String, Object> creditAfterOverdueExists(List<TradelineInput> tradelines) {
        if (tradelines == null) {
            return di("No tradelines");
        }
        boolean exists = tradelines.stream().anyMatch(TradelineInput::creditAfterOverdue);
        return pass(exists);
    }

    /**
     * clean_history_months is DI until CLEAN vocabulary is customer-resolved.
     */
    public Map<String, Object> cleanHistoryMonths(Integer months, boolean cleanVocabularyResolved) {
        if (!cleanVocabularyResolved) {
            Map<String, Object> r = di("CLEAN definition requires customer resolution before executable");
            r.put("executable", false);
            r.put("placeholder", "CUSTOMER_CONFIRMATION_REQUIRED");
            return r;
        }
        if (months == null) {
            return di("Clean history months missing after CLEAN resolved");
        }
        return pass(months);
    }

    public Map<String, Object> pass(Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", OUTCOME_PASS);
        m.put("v", v);
        m.put("dataQualityStatus", "OK");
        return m;
    }

    public Map<String, Object> di(String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", OUTCOME_DI);
        m.put("v", null);
        m.put("dataQualityStatus", OUTCOME_DI);
        m.put("reason", reason);
        return m;
    }
}
