package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauReportSummary;
import com.los.core.creditintelligence.bureau.domain.CiBureauScoringElement;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wave-3 canonical fact materialization helpers.
 * Emits exact GACAT IDs and collection contracts for the Wave-2 SafeDerived engine.
 * Does not invent source fields. Does not rename GACAT IDs.
 */
public final class CanonicalFactMaterializer {

    /** Collection key for tradeline records (execution fact key; not a new GACAT catalogue row). */
    public static final String TRADELINES = "bureau.tradelines";
    public static final String PAYMENT_HISTORY = "bureau.tradeline.payment_history";
    public static final String INQUIRIES = "bureau.inquiries";

    /** Sentinel: source acquired but collection not present — callers omit key. */
    public static final String ENTITY_MATERIALIZATION_PROVENANCE = "materializationProvenance";
    public static final String ENTITY_SOURCE_SNAPSHOT_VERSION = "sourceSnapshotVersion";
    public static final String ENTITY_BUREAU_REPORT_ID = "bureauReportId";

    private CanonicalFactMaterializer() {}

    public record CollectionBundle(
            List<Map<String, Object>> tradelines,
            List<Map<String, Object>> paymentHistory,
            List<Map<String, Object>> inquiries,
            boolean tradelinesSourcePresent,
            boolean paymentHistorySourcePresent,
            boolean inquiriesSourcePresent,
            Map<String, Object> provenance,
            Map<String, Object> exactScalars,
            List<Map<String, Object>> scoringElements,
            boolean scoringSourcePresent) {

        public CollectionBundle(
                List<Map<String, Object>> tradelines,
                List<Map<String, Object>> paymentHistory,
                List<Map<String, Object>> inquiries,
                boolean tradelinesSourcePresent,
                boolean paymentHistorySourcePresent,
                boolean inquiriesSourcePresent,
                Map<String, Object> provenance) {
            this(tradelines, paymentHistory, inquiries, tradelinesSourcePresent, paymentHistorySourcePresent,
                    inquiriesSourcePresent, provenance, Map.of(), List.of(), false);
        }
    }

    /**
     * Project remapped snapshot scalars onto exact GACAT IDs and merge collection facts.
     * Empty collections are emitted as empty lists when source present; omitted when source absent.
     */
    public static Map<String, Object> materializeExecutionFacts(
            Map<String, Object> snapshotOrMixedFacts,
            CollectionBundle collections) {
        Map<String, Object> out = CanonicalCompatibilityRegistry.projectExactCanonicalFacts(snapshotOrMixedFacts);
        if (collections == null) {
            return out;
        }
        if (collections.tradelinesSourcePresent()) {
            out.put(TRADELINES, collections.tradelines() == null ? List.of() : collections.tradelines());
        }
        if (collections.paymentHistorySourcePresent()) {
            out.put(PAYMENT_HISTORY,
                    collections.paymentHistory() == null ? List.of() : collections.paymentHistory());
        }
        if (collections.inquiriesSourcePresent()) {
            out.put(INQUIRIES, collections.inquiries() == null ? List.of() : collections.inquiries());
            out.put("bureau.inquiry", collections.inquiries() == null ? List.of() : collections.inquiries());
        }
        if (collections.scoringSourcePresent()) {
            out.put("bureau.scoring_element.code", codesOf(collections.scoringElements(), "code"));
            out.put("bureau.scoring_element.description", codesOf(collections.scoringElements(), "description"));
        }
        if (collections.exactScalars() != null) {
            for (Map.Entry<String, Object> e : collections.exactScalars().entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        overlayCollectionFieldLists(out, collections);
        return out;
    }

    public static CollectionBundle fromBureauEntities(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            List<CiBureauPaymentHistory> histories,
            List<CiBureauInquiry> inquiries) {
        return fromBureauEntities(report, tradelines, histories, inquiries, null, List.of());
    }

    public static CollectionBundle fromBureauEntities(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            List<CiBureauPaymentHistory> histories,
            List<CiBureauInquiry> inquiries,
            CiBureauReportSummary summary,
            List<CiBureauScoringElement> scoringElements) {
        Map<String, Object> prov = new LinkedHashMap<>();
        boolean reportPresent = report != null;
        if (reportPresent) {
            prov.put("bureauReportId", report.getId() == null ? null : report.getId().toString());
            if (report.getReportDate() != null) {
                prov.put("reportDate", report.getReportDate().toString());
            }
            if (report.getProviderCode() != null) {
                prov.put("provider", report.getProviderCode());
            }
        }
        List<CiBureauTradeline> tls = tradelines == null ? List.of() : tradelines;
        List<CiBureauPaymentHistory> ph = histories == null ? List.of() : histories;
        List<CiBureauInquiry> inq = inquiries == null ? List.of() : inquiries;

        // Source present: report exists (even if tradeline list empty)
        boolean tlPresent = reportPresent;
        boolean phPresent = reportPresent && (!tls.isEmpty() || !ph.isEmpty());
        // If report present but no tradelines and no PH rows → PH source absent vs empty:
        // Wave-3: if report says tradelinesPresent false and no rows → omit (absent).
        // If tradelines exist but all have zero PH → emit empty list (present empty).
        if (reportPresent && tls.isEmpty() && ph.isEmpty()) {
            if (!report.isTradelinesPresent()) {
                phPresent = false;
            } else {
                phPresent = true; // claimed present but empty extraction
            }
        } else if (reportPresent && !tls.isEmpty()) {
            phPresent = true; // tradelines loaded — PH may be empty list
        }

        boolean inqPresent = reportPresent;

        List<Map<String, Object>> tlMaps = new ArrayList<>();
        for (CiBureauTradeline t : tls) {
            tlMaps.add(tradelineRecord(t));
        }
        List<Map<String, Object>> phMaps = new ArrayList<>();
        for (CiBureauPaymentHistory h : ph) {
            phMaps.add(paymentHistoryObservation(h));
        }
        List<Map<String, Object>> inqMaps = new ArrayList<>();
        for (CiBureauInquiry i : inq) {
            inqMaps.add(inquiryRecord(i));
        }

        List<Map<String, Object>> scoringMaps = new ArrayList<>();
        List<CiBureauScoringElement> scoring = scoringElements == null ? List.of() : scoringElements;
        for (CiBureauScoringElement se : scoring) {
            Map<String, Object> row = new LinkedHashMap<>();
            put(row, "code", se.getCode());
            put(row, "description", se.getDescription());
            scoringMaps.add(row);
        }
        boolean scoringPresent = reportPresent && !scoringMaps.isEmpty();

        prov.put("tradelineRowCount", tlMaps.size());
        prov.put("paymentHistoryRowCount", phMaps.size());
        prov.put("inquiryRowCount", inqMaps.size());
        prov.put("scoringElementRowCount", scoringMaps.size());
        prov.put("tradelinesSourcePresent", tlPresent);
        prov.put("paymentHistorySourcePresent", phPresent);
        prov.put("inquiriesSourcePresent", inqPresent);
        prov.put("scoringSourcePresent", scoringPresent);

        Map<String, Object> scalars = reportScalars(report, summary);

        return new CollectionBundle(tlMaps, phMaps, inqMaps, tlPresent, phPresent, inqPresent, prov,
                scalars, scoringMaps, scoringPresent);
    }

    static Map<String, Object> reportScalars(CiBureauReport report, CiBureauReportSummary summary) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (report != null) {
            if (report.getScore() != null && report.getScore() >= 0) {
                m.put("bureau.score", report.getScore());
            }
            if (report.getReportDate() != null) {
                m.put("bureau.report.date", report.getReportDate().toString());
            }
            put(m, "bureau.score.name", report.getScoreType());
        }
        if (summary != null) {
            put(m, "bureau.hit_code", summary.getHitCode());
            put(m, "bureau.success_code", summary.getSuccessCode());
            put(m, "bureau.report_order_no", summary.getReportOrderNo());
            put(m, "bureau.score.name", firstNonBlank(summary.getScoreName(),
                    report != null ? report.getScoreType() : null));
            putInt(m, "bureau.summary.account_count", summary.getAccountCount());
            putInt(m, "bureau.summary.active_account_count", summary.getActiveAccountCount());
            putInt(m, "bureau.summary.writeoff_count", summary.getWriteoffCount());
            putMoney(m, "bureau.summary.total_past_due", summary.getTotalPastDue());
            put(m, "bureau.summary.most_severe_status_24m", summary.getMostSevereStatus24m());
            putMoney(m, "bureau.summary.total_balance", summary.getTotalBalance());
            putMoney(m, "bureau.summary.total_sanction", summary.getTotalSanction());
            putMoney(m, "bureau.summary.total_credit_limit", summary.getTotalCreditLimit());
            putMoney(m, "bureau.summary.total_monthly_payment", summary.getTotalMonthlyPayment());
            putMoney(m, "bureau.summary.highest_sanction", summary.getHighestSanction());
            putMoney(m, "bureau.summary.highest_balance", summary.getHighestBalance());
            putMoney(m, "bureau.summary.average_open_balance", summary.getAverageOpenBalance());
            putInt(m, "bureau.summary.age_of_oldest_trade_months", summary.getAgeOfOldestTradeMonths());
            putInt(m, "bureau.summary.open_trade_count", summary.getOpenTradeCount());
            putInt(m, "bureau.summary.past_due_account_count", summary.getPastDueAccountCount());
            putInt(m, "bureau.summary.zero_balance_account_count", summary.getZeroBalanceAccountCount());
            putMoney(m, "bureau.summary.highest_credit", summary.getHighestCredit());
            putMoney(m, "bureau.summary.total_high_credit", summary.getTotalHighCredit());
            putInt(m, "bureau.enquiry.summary.total", summary.getEnquiryTotal());
            putInt(m, "bureau.enquiry.summary.past_30d", summary.getEnquiryPast30d());
            putInt(m, "bureau.enquiry.summary.past_12m", summary.getEnquiryPast12m());
            putInt(m, "bureau.enquiry.summary.past_24m", summary.getEnquiryPast24m());
            if (summary.getEnquiryRecentDate() != null) {
                m.put("bureau.enquiry.summary.recent_date", summary.getEnquiryRecentDate().toString());
            }
            putInt(m, "bureau.recent.accounts_opened_90d", summary.getRecentAccountsOpened90d());
            putInt(m, "bureau.recent.accounts_updated_90d", summary.getRecentAccountsUpdated90d());
            putInt(m, "bureau.recent.accounts_delinquent_90d", summary.getRecentAccountsDelinquent90d());
            putInt(m, "bureau.recent.inquiries_90d", summary.getRecentInquiries90d());
            put(m, "bureau.report.time", summary.getReportTime());
            put(m, "bureau.enquiry.summary.purpose", summary.getEnquirySummaryPurpose());
            putMoney(m, "bureau.summary.all_lines_ever_written", summary.getAllLinesEverWritten());
            putMoney(m, "bureau.summary.all_lines_ever_written_9m", summary.getAllLinesEverWritten9m());
            putMoney(m, "bureau.summary.all_lines_ever_written_6m", summary.getAllLinesEverWritten6m());
            put(m, "bureau.summary.recent_account_narrative", summary.getRecentAccountNarrative());
            put(m, "bureau.summary.oldest_account_narrative", summary.getOldestAccountNarrative());
        }
        return m;
    }

    /** Test/helper: build PH observations from simple maps without inventing fields. */
    public static Map<String, Object> paymentHistoryObservation(CiBureauPaymentHistory h) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (h.getMonth() != null) {
            m.put("month", YearMonthKey.from(h.getMonth()));
            m.put("period", YearMonthKey.from(h.getMonth()));
        }
        if (h.getDpd() != null) {
            m.put("dpd", h.getDpd());
        }
        if (h.getStatus() != null) {
            m.put("paymentStatus", h.getStatus());
            m.put("status", h.getStatus());
        }
        if (h.getTradelineId() != null) {
            m.put("tradelineId", h.getTradelineId().toString());
            m.put("tradeline_ref", h.getTradelineId().toString());
        }
        if (h.getId() != null) {
            m.put("observationId", h.getId().toString());
        }
        if (h.getSourceReference() != null) {
            m.put("sourceReference", h.getSourceReference());
        }
        put(m, "suit_filed_status", h.getSuitFiledStatus());
        put(m, "asset_classification_status", h.getAssetClassificationStatus());
        m.put("estimated", h.isEstimated());
        return m;
    }

    public static Map<String, Object> tradelineRecord(CiBureauTradeline t) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (t.getId() != null) {
            m.put("tradelineId", t.getId().toString());
            m.put("tradeline_ref", t.getId().toString());
        }
        if (t.getProviderTradelineRef() != null) {
            m.put("providerTradelineRef", t.getProviderTradelineRef());
        }
        put(m, "account_type", firstNonBlank(t.getProductCategory(), t.getAccountTypeRaw()));
        put(m, "accountTypeRaw", t.getAccountTypeRaw());
        put(m, "productCategory", t.getProductCategory());
        put(m, "status", t.getAccountStatus());
        put(m, "accountStatus", t.getAccountStatus());
        if (t.getSecured() != null) {
            m.put("secured", t.getSecured());
        }
        put(m, "ownership", t.getOwnershipType());
        put(m, "lender", t.getLenderName());
        putMoney(m, "overdue_amount", t.getOverdueAmount());
        putMoney(m, "current_balance", t.getCurrentBalance());
        putMoney(m, "credit_limit", t.getHighCredit() != null ? t.getHighCredit() : t.getSanctionedAmount());
        putMoney(m, "high_credit", t.getHighCredit());
        putMoney(m, "sanctioned_amount", t.getSanctionedAmount());
        if (t.getOpenedDate() != null) {
            m.put("opened_date", t.getOpenedDate().toString());
        }
        if (t.getClosedDate() != null) {
            m.put("closed_date", t.getClosedDate().toString());
        }
        if (t.getLastReportedDate() != null) {
            m.put("date_reported", t.getLastReportedDate().toString());
        }
        if (t.getSuitFiled() != null) {
            m.put("suit_filed", t.getSuitFiled());
            m.put("bureau.tradeline.suit_filed", t.getSuitFiled());
        }
        if (t.getWilfulDefault() != null) {
            m.put("wilful_default", t.getWilfulDefault());
        }
        m.put("written_off", t.isWrittenOff());
        m.put("settled", t.isSettled());
        if (t.getIsLive() != null) {
            m.put("is_live", t.getIsLive());
        }
        putMoney(m, "emi", t.getEmiAmount());
        putMoney(m, "interest_rate", t.getInterestRate());
        if (t.getTenureMonths() != null) {
            m.put("tenure_months", t.getTenureMonths());
        }
        put(m, "asset_classification", t.getAssetClassification());
        put(m, "collateral_type", t.getCollateralType());
        putMoney(m, "collateral_value", t.getCollateralValue());
        putMoney(m, "write_off_amount", t.getWrittenOffAmount());
        putMoney(m, "settlement_amount", t.getSettlementAmount());
        putMoney(m, "last_payment_amount", t.getLastPaymentAmount());
        if (t.getLastPaymentDate() != null) {
            m.put("last_payment_date", t.getLastPaymentDate().toString());
        }
        put(m, "term_frequency", t.getTermFrequency());
        put(m, "dispute_code", t.getDisputeCode());
        put(m, "closure_reason", t.getClosureReason());
        return m;
    }

    public static Map<String, Object> inquiryRecord(CiBureauInquiry i) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (i.getId() != null) {
            m.put("inquiryId", i.getId().toString());
        }
        if (i.getInquiryDate() != null) {
            m.put("inquiry_date", i.getInquiryDate().toString());
            m.put("date", i.getInquiryDate().toString());
        }
        put(m, "member_name", i.getMemberName());
        put(m, "member", i.getMemberName());
        put(m, "purpose", i.getPurpose());
        putMoney(m, "amount", i.getAmount());
        put(m, "time", i.getInquiryTime());
        put(m, "inquiry_time", i.getInquiryTime());
        return m;
    }

    /** Resolve evaluationAsOf without wall-clock: explicit → report date → null. */
    public static LocalDate resolveEvaluationAsOf(LocalDate explicit, CiBureauReport report) {
        if (explicit != null) {
            return explicit;
        }
        if (report != null && report.getReportDate() != null) {
            return report.getReportDate();
        }
        return null;
    }

    /**
     * Resolve asOf: explicit → reportDate → latest payment-history month (business data) → null.
     * Never uses {@code LocalDate.now()}.
     */
    public static LocalDate resolveEvaluationAsOf(
            LocalDate explicit, CiBureauReport report, Map<String, Object> facts) {
        LocalDate base = resolveEvaluationAsOf(explicit, report);
        if (base != null) {
            return base;
        }
        return deriveAsOfFromPaymentHistory(facts);
    }

    @SuppressWarnings("unchecked")
    public static LocalDate deriveAsOfFromPaymentHistory(Map<String, Object> facts) {
        if (facts == null) {
            return null;
        }
        Object raw = facts.get(PAYMENT_HISTORY);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        LocalDate latest = null;
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            Object month = m.get("month");
            if (month == null) {
                month = m.get("period");
            }
            LocalDate d = parseYearMonthOrDate(month);
            if (d != null && (latest == null || d.isAfter(latest))) {
                latest = d;
            }
        }
        return latest;
    }

    private static LocalDate parseYearMonthOrDate(Object month) {
        if (month == null) {
            return null;
        }
        if (month instanceof LocalDate ld) {
            return ld;
        }
        String s = String.valueOf(month).trim();
        if (s.length() >= 7 && s.charAt(4) == '-') {
            try {
                int y = Integer.parseInt(s.substring(0, 4));
                int mo = Integer.parseInt(s.substring(5, 7));
                return LocalDate.of(y, mo, 1);
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Map<String, Object> provenanceEntityExtras(
            UUID snapshotId, Integer snapshotVersion, CollectionBundle collections) {
        Map<String, Object> e = new LinkedHashMap<>();
        if (snapshotId != null) {
            e.put(ENTITY_SOURCE_SNAPSHOT_VERSION, snapshotId.toString());
            e.put("snapshotId", snapshotId.toString());
        }
        if (snapshotVersion != null) {
            e.put("snapshotVersion", snapshotVersion);
        }
        if (collections != null && collections.provenance() != null) {
            e.put(ENTITY_MATERIALIZATION_PROVENANCE, collections.provenance());
            Object br = collections.provenance().get("bureauReportId");
            if (br != null) {
                e.put(ENTITY_BUREAU_REPORT_ID, br);
            }
        }
        return e;
    }

    private static void putInt(Map<String, Object> m, String k, Integer v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    private static List<Object> codesOf(List<Map<String, Object>> rows, String key) {
        List<Object> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Object v = row.get(key);
            if (v != null && !(v instanceof String s && s.isBlank())) {
                out.add(v);
            }
        }
        return out;
    }

    private static void overlayCollectionFieldLists(Map<String, Object> out, CollectionBundle collections) {
        if (collections.inquiriesSourcePresent() && collections.inquiries() != null) {
            putListIfAny(out, "bureau.inquiry.date", collections.inquiries(), "inquiry_date", "date");
            putListIfAny(out, "bureau.inquiry.purpose", collections.inquiries(), "purpose");
            putListIfAny(out, "bureau.inquiry.amount", collections.inquiries(), "amount");
            putListIfAny(out, "bureau.inquiry.member", collections.inquiries(), "member_name", "member");
            putListIfAny(out, "bureau.inquiry.time", collections.inquiries(), "inquiry_time", "time");
        }
        if (collections.tradelinesSourcePresent() && collections.tradelines() != null) {
            putListIfAny(out, "bureau.tradeline.account_type", collections.tradelines(), "account_type");
            putListIfAny(out, "bureau.tradeline.ownership", collections.tradelines(), "ownership");
            putListIfAny(out, "bureau.tradeline.lender", collections.tradelines(), "lender");
            putListIfAny(out, "bureau.tradeline.account_open_date", collections.tradelines(), "opened_date");
            putListIfAny(out, "bureau.tradeline.account_close_date", collections.tradelines(), "closed_date");
            putListIfAny(out, "bureau.tradeline.date_reported", collections.tradelines(), "date_reported");
            putListIfAny(out, "bureau.tradeline.sanction_amount", collections.tradelines(), "sanctioned_amount");
            putListIfAny(out, "bureau.tradeline.current_balance", collections.tradelines(), "current_balance");
            putListIfAny(out, "bureau.tradeline.overdue_amount", collections.tradelines(), "overdue_amount");
            putListIfAny(out, "bureau.tradeline.credit_limit", collections.tradelines(), "credit_limit", "high_credit");
            putListIfAny(out, "bureau.tradeline.emi", collections.tradelines(), "emi");
            putListIfAny(out, "bureau.tradeline.tenure_months", collections.tradelines(), "tenure_months");
            putListIfAny(out, "bureau.tradeline.interest_rate", collections.tradelines(), "interest_rate");
            putListIfAny(out, "bureau.tradeline.account_status", collections.tradelines(), "accountStatus", "status");
            putListIfAny(out, "bureau.tradeline.write_off_amount", collections.tradelines(), "write_off_amount");
            putListIfAny(out, "bureau.tradeline.settlement_amount", collections.tradelines(), "settlement_amount");
            putListIfAny(out, "bureau.tradeline.suit_filed", collections.tradelines(), "suit_filed");
            putListIfAny(out, "bureau.tradeline.wilful_default", collections.tradelines(), "wilful_default");
            putListIfAny(out, "bureau.tradeline.asset_classification", collections.tradelines(), "asset_classification");
            putListIfAny(out, "bureau.tradeline.collateral_type", collections.tradelines(), "collateral_type");
            putListIfAny(out, "bureau.tradeline.collateral_value", collections.tradelines(), "collateral_value");
            putListIfAny(out, "bureau.tradeline.secured_flag", collections.tradelines(), "secured");
            putListIfAny(out, "bureau.tradeline.last_payment_amount", collections.tradelines(), "last_payment_amount");
            putListIfAny(out, "bureau.tradeline.last_payment_date", collections.tradelines(), "last_payment_date");
            putListIfAny(out, "bureau.tradeline.term_frequency", collections.tradelines(), "term_frequency");
            putListIfAny(out, "bureau.tradeline.dispute_code", collections.tradelines(), "dispute_code");
            putListIfAny(out, "bureau.tradeline.closure_reason", collections.tradelines(), "closure_reason");
        }
        if (collections.paymentHistorySourcePresent() && collections.paymentHistory() != null) {
            putListIfAny(out, "bureau.tradeline.payment_status_month", collections.paymentHistory(), "paymentStatus", "status");
            putListIfAny(out, "bureau.tradeline.dpd_month", collections.paymentHistory(), "dpd");
            putListIfAny(out, "bureau.tradeline.suit_filed_month", collections.paymentHistory(), "suit_filed_status");
            putListIfAny(out, "bureau.tradeline.asset_classification_month", collections.paymentHistory(), "asset_classification_status");
        }
    }

    private static void putListIfAny(Map<String, Object> out, String canonicalId,
                                    List<Map<String, Object>> rows, String... keys) {
        List<Object> vals = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object v = null;
            for (String k : keys) {
                v = row.get(k);
                if (v != null && !(v instanceof String s && s.isBlank())) {
                    break;
                }
                v = null;
            }
            if (v != null) {
                vals.add(v);
            }
        }
        if (!vals.isEmpty()) {
            out.putIfAbsent(canonicalId, vals);
        }
    }

    private static void put(Map<String, Object> m, String k, Object v) {
        if (v != null && !(v instanceof String s && s.isBlank())) {
            m.put(k, v);
        }
    }

    private static void putMoney(Map<String, Object> m, String k, java.math.BigDecimal v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /** YYYY-MM string for SafeDerived month parsing. */
    public static final class YearMonthKey {
        private YearMonthKey() {}

        public static String from(LocalDate d) {
            if (d == null) return null;
            return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
        }
    }
}
