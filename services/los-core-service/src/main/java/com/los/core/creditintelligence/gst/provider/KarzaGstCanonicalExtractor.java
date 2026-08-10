package com.los.core.creditintelligence.gst.provider;

import com.los.core.creditintelligence.gst.domain.GstReturnType;
import com.los.core.creditintelligence.gst.util.GstFilingStatusNormalizer;
import com.los.core.creditintelligence.gst.util.GstPeriodUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts canonical GST registration / periods / financials from Karza GST analysis payload.
 * Reads {@code fullResponse.result} or {@code result} Map. Never invents amounts.
 * Parser version: {@link #PARSER_VERSION}.
 */
public final class KarzaGstCanonicalExtractor {

    public static final String PARSER_VERSION = "KARZA_GST_PARSER_V1";

    public record ExtractedRegistration(
            String gstin,
            String legalName,
            String tradeName,
            String registrationStatus,
            LocalDate registrationDate,
            String taxpayerType,
            String stateCode,
            Map<String, Object> metadata) {
    }

    public record ExtractedPeriod(
            String returnType,
            String periodYyyyMm,
            String financialYear,
            String filingStatus,
            Integer filingDelayDays,
            String rawStatus,
            BigDecimal taxableTurnover,
            boolean turnoverPresent,
            Map<String, Object> metadata) {
    }

    public record ExtractionResult(
            List<ExtractedRegistration> registrations,
            List<ExtractedPeriod> periods,
            String parserVersion) {
    }

    private KarzaGstCanonicalExtractor() {
    }

    @SuppressWarnings("unchecked")
    public static ExtractionResult extract(Map<String, Object> parsedDataOrFull) {
        Map<String, Object> root = parsedDataOrFull != null ? parsedDataOrFull : Map.of();
        Map<String, Object> result = resolveResult(root);
        if (result.isEmpty()) {
            return new ExtractionResult(List.of(), List.of(), PARSER_VERSION);
        }

        ExtractedRegistration reg = extractRegistration(result, root);
        List<ExtractedPeriod> periods = new ArrayList<>();
        periods.addAll(extractFromFilingStatus(result));
        periods.addAll(extractFromMonthWiseSummary(result));
        periods = mergePeriods(periods);

        List<ExtractedRegistration> regs = new ArrayList<>();
        if (reg != null && reg.gstin() != null && !reg.gstin().isBlank()) {
            regs.add(reg);
        }
        // Multi-GSTIN: look for additional gstins array if present
        Object multi = result.get("gstins");
        if (multi instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> mm = (Map<String, Object>) m;
                    String g = str(mm.get("gstin"));
                    if (g != null && regs.stream().noneMatch(r -> g.equalsIgnoreCase(r.gstin()))) {
                        regs.add(new ExtractedRegistration(
                                g.toUpperCase(Locale.ROOT),
                                str(mm.get("legalName")),
                                str(mm.get("tradeName")),
                                normalizeRegStatus(str(mm.get("sts") != null ? mm.get("sts") : mm.get("status"))),
                                parseDate(str(mm.get("rgdt") != null ? mm.get("rgdt") : mm.get("registrationDate"))),
                                str(mm.get("dty") != null ? mm.get("dty") : mm.get("taxpayerType")),
                                str(mm.get("stj") != null ? mm.get("stj") : mm.get("stateCode")),
                                Map.of("multiGstin", true)));
                    }
                }
            }
        }

        return new ExtractionResult(regs, periods, PARSER_VERSION);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveResult(Map<String, Object> root) {
        Object full = root.get("fullResponse");
        if (full instanceof Map<?, ?> fr) {
            Object r = ((Map<String, Object>) fr).get("result");
            if (r instanceof Map<?, ?> rm) {
                return (Map<String, Object>) rm;
            }
        }
        Object result = root.get("result");
        if (result instanceof Map<?, ?> rm) {
            return (Map<String, Object>) rm;
        }
        // already a Karza result node (has profile/current)
        if (root.containsKey("profile") || root.containsKey("current") || root.containsKey("gstin")) {
            return root;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static ExtractedRegistration extractRegistration(Map<String, Object> result, Map<String, Object> root) {
        Map<String, Object> profile = mapOf(result.get("profile"));
        String gstin = firstNonBlank(
                str(result.get("gstin")),
                str(profile.get("gstin")),
                str(root.get("gstin")));
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase(Locale.ROOT);
        }
        String legal = firstNonBlank(str(profile.get("lgnm")), str(profile.get("legalName")));
        String trade = firstNonBlank(str(profile.get("tradeNam")), str(profile.get("tradeName")));
        String sts = firstNonBlank(str(profile.get("sts")), str(profile.get("status")));
        LocalDate regDate = parseDate(firstNonBlank(str(profile.get("rgdt")), str(profile.get("registrationDate"))));
        String taxpayer = firstNonBlank(str(profile.get("dty")), str(profile.get("taxpayerType")));
        String state = firstNonBlank(str(profile.get("stj")), str(profile.get("stateCode")));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reportType", str(result.get("reportType")));
        if (result.get("current") instanceof Map<?, ?> cur) {
            Object fp = ((Map<?, ?>) cur).get("financialPeriod");
            if (fp != null) {
                meta.put("financialPeriod", String.valueOf(fp));
            }
        }
        return new ExtractedRegistration(
                gstin, legal, trade, normalizeRegStatus(sts), regDate, taxpayer, state, meta);
    }

    @SuppressWarnings("unchecked")
    private static List<ExtractedPeriod> extractFromFilingStatus(Map<String, Object> result) {
        List<ExtractedPeriod> out = new ArrayList<>();
        Map<String, Object> current = mapOf(result.get("current"));
        Object fs = current.get("filingStatus");
        if (!(fs instanceof List<?> rows)) {
            return out;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowObj;
            Optional<String> period = GstPeriodUtils.parseMmyyyyToYyyyMm(str(row.get("retPeriod")));
            if (period.isEmpty()) {
                continue;
            }
            String yyyyMm = period.get();
            String fy = GstPeriodUtils.financialYearLabel(yyyyMm);
            Object statusArr = row.get("status");
            if (!(statusArr instanceof List<?> statuses)) {
                continue;
            }
            for (Object stObj : statuses) {
                if (!(stObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> st = (Map<String, Object>) stObj;
                String type = firstNonBlank(str(st.get("rtntype")), str(st.get("returnTy")), str(st.get("returnType")));
                String rawStatus = str(st.get("status"));
                Integer delay = toInt(st.get("delay") != null ? st.get("delay") : st.get("delayDays"));
                GstFilingStatusNormalizer.NormalizedFiling norm =
                        GstFilingStatusNormalizer.normalize(rawStatus, delay);
                GstReturnType rt = mapReturnType(type);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", "filingStatus");
                meta.put("rawReturnType", type);
                out.add(new ExtractedPeriod(
                        rt.name(),
                        yyyyMm,
                        fy,
                        norm.status().name(),
                        norm.delayDays(),
                        rawStatus,
                        null,
                        false,
                        meta));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<ExtractedPeriod> extractFromMonthWiseSummary(Map<String, Object> result) {
        List<ExtractedPeriod> out = new ArrayList<>();
        Map<String, Object> current = mapOf(result.get("current"));
        Object mws = current.get("monthWiseSummary");
        if (!(mws instanceof List<?> rows)) {
            return out;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowObj;
            Optional<String> period = GstPeriodUtils.parseMmyyyyToYyyyMm(str(row.get("retPeriod")));
            if (period.isEmpty()) {
                continue;
            }
            String yyyyMm = period.get();
            String fy = GstPeriodUtils.financialYearLabel(yyyyMm);

            Map<String, Object> gstr1 = mapOf(row.get("gstr1"));
            BigDecimal gstr1Val = toBd(gstr1.get("ttlVal"));
            if (gstr1Val != null || !gstr1.isEmpty()) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", "monthWiseSummary");
                boolean present = gstr1.containsKey("ttlVal");
                out.add(new ExtractedPeriod(
                        GstReturnType.GSTR1.name(),
                        yyyyMm,
                        fy,
                        present ? "FILED" : "UNKNOWN",
                        null,
                        null,
                        present ? gstr1Val : null,
                        present,
                        meta));
            }

            Map<String, Object> gstr3b = mapOf(row.get("gstr3b"));
            BigDecimal gstr3bVal = toBd(gstr3b.get("ttlVal"));
            if (gstr3bVal != null || gstr3b.containsKey("ttlVal")) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", "monthWiseSummary");
                boolean present = gstr3b.containsKey("ttlVal");
                out.add(new ExtractedPeriod(
                        GstReturnType.GSTR3B.name(),
                        yyyyMm,
                        fy,
                        present ? "FILED" : "UNKNOWN",
                        null,
                        null,
                        present ? gstr3bVal : null,
                        present,
                        meta));
            }
        }
        return out;
    }

    /**
     * Merge filing-status rows with month-wise financials for same period+type.
     * Prefer financial amounts from monthWiseSummary; prefer filing status from filingStatus.
     */
    private static List<ExtractedPeriod> mergePeriods(List<ExtractedPeriod> periods) {
        Map<String, ExtractedPeriod> byKey = new LinkedHashMap<>();
        for (ExtractedPeriod p : periods) {
            String key = p.returnType() + "|" + p.periodYyyyMm();
            ExtractedPeriod existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, p);
                continue;
            }
            boolean preferNewStatus = "filingStatus".equals(String.valueOf(p.metadata().get("source")));
            boolean preferNewAmount = p.turnoverPresent();
            String status = preferNewStatus ? p.filingStatus() : existing.filingStatus();
            Integer delay = preferNewStatus && p.filingDelayDays() != null
                    ? p.filingDelayDays() : existing.filingDelayDays();
            String raw = preferNewStatus ? p.rawStatus() : existing.rawStatus();
            BigDecimal amt = preferNewAmount ? p.taxableTurnover() : existing.taxableTurnover();
            boolean present = preferNewAmount ? p.turnoverPresent() : existing.turnoverPresent();
            if (!preferNewAmount && existing.turnoverPresent()) {
                amt = existing.taxableTurnover();
                present = true;
            }
            if (preferNewAmount && !existing.turnoverPresent()) {
                amt = p.taxableTurnover();
                present = p.turnoverPresent();
            }
            if (!preferNewStatus) {
                status = existing.filingStatus();
                delay = existing.filingDelayDays();
                raw = existing.rawStatus();
            }
            Map<String, Object> meta = new LinkedHashMap<>(existing.metadata());
            meta.putAll(p.metadata());
            meta.put("merged", true);
            byKey.put(key, new ExtractedPeriod(
                    p.returnType(), p.periodYyyyMm(), p.financialYear(),
                    status, delay, raw, amt, present, meta));
        }
        return new ArrayList<>(byKey.values());
    }

    private static GstReturnType mapReturnType(String raw) {
        if (raw == null || raw.isBlank()) {
            return GstReturnType.OTHER;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return switch (u) {
            case "GSTR1", "GSTR-1" -> GstReturnType.GSTR1;
            case "GSTR3B", "GSTR-3B", "GSTR3-B" -> GstReturnType.GSTR3B;
            case "GSTR2B", "GSTR-2B" -> GstReturnType.GSTR2B;
            case "GSTR9", "GSTR-9" -> GstReturnType.GSTR9;
            case "GSTR9C", "GSTR-9C" -> GstReturnType.GSTR9C;
            default -> GstReturnType.OTHER;
        };
    }

    private static String normalizeRegStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("active") || "a".equals(s)) {
            return "ACTIVE";
        }
        if (s.contains("cancel") || s.contains("inactive") || "i".equals(s) || "c".equals(s)) {
            return "INACTIVE";
        }
        if (s.contains("suspend")) {
            return "SUSPENDED";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (DateTimeFormatter f : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(o).trim().replace(",", "");
            if (s.isBlank()) {
                return null;
            }
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
