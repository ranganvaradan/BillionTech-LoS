package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
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
            Map<String, Object> provenance) {}

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
        }
        return out;
    }

    public static CollectionBundle fromBureauEntities(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            List<CiBureauPaymentHistory> histories,
            List<CiBureauInquiry> inquiries) {
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

        prov.put("tradelineRowCount", tlMaps.size());
        prov.put("paymentHistoryRowCount", phMaps.size());
        prov.put("inquiryRowCount", inqMaps.size());
        prov.put("tradelinesSourcePresent", tlPresent);
        prov.put("paymentHistorySourcePresent", phPresent);
        prov.put("inquiriesSourcePresent", inqPresent);

        return new CollectionBundle(tlMaps, phMaps, inqMaps, tlPresent, phPresent, inqPresent, prov);
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
        m.put("suit_filed", t.isSuitFiled());
        if (t.isSuitFiled()) {
            m.put("bureau.tradeline.suit_filed", true);
        }
        m.put("written_off", t.isWrittenOff());
        m.put("settled", t.isSettled());
        if (t.getIsLive() != null) {
            m.put("is_live", t.getIsLive());
        }
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
        put(m, "purpose", i.getPurpose());
        putMoney(m, "amount", i.getAmount());
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
