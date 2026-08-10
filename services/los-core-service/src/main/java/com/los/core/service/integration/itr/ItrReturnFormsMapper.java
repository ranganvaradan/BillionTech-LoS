package com.los.core.service.integration.itr;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Karza ITR return-forms {@code result} JSON into underwriting OCR-shaped extract metrics.
 */
public final class ItrReturnFormsMapper {

    private ItrReturnFormsMapper() {
    }

    /**
     * Flattened metrics compatible with {@code CreditControlService.applyExtractedMap}
     * keys under {@code extractedData} / {@code mappedMetrics}.
     */
    public static Map<String, Object> mapMetrics(JsonNode result) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (result == null || result.isNull() || result.isMissingNode()) {
            return metrics;
        }

        JsonNode formDetails = result.path("formDetails");
        JsonNode general = result.path("generalInformation");
        JsonNode latestYr = pickLatestFinancialYear(result.path("financialInformation"));
        JsonNode latestItr = pickLatestItrFilled(result.path("itrFilled"));
        // Karza may return SUCCESS with empty/missing itrFilled or financialInformation — never NPE on null picks.
        JsonNode yr = latestYr != null ? latestYr : result.path("financialInformation").path(0);
        JsonNode itr = latestItr != null ? latestItr : result.path("itrFilled").path(0);

        putText(metrics, "assessmentYear", firstText(
                formDetails.path("assessmentYear"),
                yr.path("assessmentYear"),
                itr.path("annualYear")));
        putText(metrics, "financialYear", firstText(
                formDetails.path("financialYear"),
                yr.path("financialYear")));
        putText(metrics, "itrForm", firstText(
                formDetails.path("formName"),
                itr.path("itrForm")));
        putText(metrics, "panNumber", firstText(
                general.path("entityPan"),
                itr.path("pan")));
        putText(metrics, "entityName", text(general.path("entityName")));
        putText(metrics, "filingDate", firstText(
                itr.path("fillingDate"),
                itr.path("filingDate")));

        if (latestYr != null && !latestYr.isMissingNode() && !latestYr.isNull()) {
            JsonNode pl = latestYr.path("profitAndLoss");
            JsonNode bs = latestYr.path("balanceSheet");
            JsonNode ratios = latestYr.path("ratios");

            // Confirmed: ITR_INCOME ← totalRevenue
            putBd(metrics, "itrIncome", pl.path("totalRevenue"));
            putBd(metrics, "grossTotalIncome", pl.path("totalRevenue"));
            putBd(metrics, "pat", pl.path("profitAfterTax"));
            putBd(metrics, "ebitda", pl.path("ebitda"));
            putBd(metrics, "debtService", pl.path("interestExpense"));
            putBd(metrics, "tol", bs.path("totalLiability"));
            putBd(metrics, "tnw", bs.path("totalEquity"));
            putBd(metrics, "interestCoverage", ratios.path("liquidityRatios").path("interestCoverage"));
            putBd(metrics, "debtToEquity", ratios.path("solvencyRatios").path("debtEquity"));
        }

        return metrics;
    }

    /**
     * Download link candidates for report artifacts (PDF / Excel / per-year forms).
     * Each map entry: kind, url, fileName hint, contentType hint.
     */
    public static List<Map<String, String>> collectDownloadLinks(JsonNode result, String pan, String requestId) {
        List<Map<String, String>> links = new ArrayList<>();
        if (result == null || result.isNull()) {
            return links;
        }
        String panSafe = safeToken(pan, "PAN");
        String reqSafe = safeToken(requestId, "REQ");

        addLink(links, text(result.path("pdfDownloadLink")), "ITR_" + panSafe + "_" + reqSafe + ".pdf", "application/pdf");
        addLink(links, text(result.path("excelReportLink")), "ITR_" + panSafe + "_" + reqSafe + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        JsonNode filled = result.path("itrFilled");
        if (filled.isArray()) {
            for (JsonNode row : filled) {
                String ay = firstText(row.path("annualYear"), row.path("assessmentYear"));
                String ack = text(row.path("ackNo"));
                String other = text(row.path("otherFilesDownloadLink"));
                if (other != null && !other.isBlank()) {
                    addLink(links, other,
                            "ITR_" + safeToken(ay, "AY") + "_" + safeToken(ack, "ACK") + ".pdf",
                            "application/pdf");
                }
                JsonNode activities = row.path("activity");
                if (activities.isArray()) {
                    int i = 0;
                    for (JsonNode act : activities) {
                        String dl = text(act.path("downloadsStatus").path("downloadLink"));
                        if (dl != null && !dl.isBlank()) {
                            addLink(links, dl,
                                    "ITR_" + safeToken(ay, "AY") + "_" + safeToken(ack, "ACK")
                                            + (i > 0 ? "_" + i : "") + ".pdf",
                                    "application/pdf");
                            i++;
                        }
                    }
                }
            }
        }
        return links;
    }

    static JsonNode pickLatestItrFilled(JsonNode itrFilled) {
        if (itrFilled == null || !itrFilled.isArray() || itrFilled.isEmpty()) {
            return null;
        }
        List<JsonNode> rows = new ArrayList<>();
        itrFilled.forEach(n -> {
            if (n != null && !n.isNull() && !n.isMissingNode()) {
                rows.add(n);
            }
        });
        if (rows.isEmpty()) {
            return null;
        }
        rows.sort(Comparator.comparing(
                (JsonNode n) -> nullToEmpty(firstText(n.path("annualYear"), n.path("assessmentYear"), n.path("fillingDate")))
        ).reversed());
        return rows.get(0);
    }

    static JsonNode pickLatestFinancialYear(JsonNode financialInformation) {
        if (financialInformation == null || !financialInformation.isArray() || financialInformation.isEmpty()) {
            return null;
        }
        List<JsonNode> years = new ArrayList<>();
        financialInformation.forEach(n -> {
            if (n != null && !n.isNull() && !n.isMissingNode()) {
                years.add(n);
            }
        });
        if (years.isEmpty()) {
            return null;
        }
        years.sort(Comparator.comparing(ItrReturnFormsMapper::yearSortKey).reversed());
        return years.get(0);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String yearSortKey(JsonNode n) {
        String fy = text(n.path("financialYear"));
        if (fy != null) return fy;
        String ay = text(n.path("assessmentYear"));
        return ay != null ? ay : "";
    }

    private static void addLink(List<Map<String, String>> links, String url, String fileName, String contentType) {
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", url.trim());
        m.put("fileName", fileName);
        m.put("contentType", contentType);
        links.add(m);
    }

    private static void putBd(Map<String, Object> m, String key, JsonNode node) {
        BigDecimal bd = toBd(node);
        if (bd != null) {
            m.put(key, bd);
        }
    }

    private static void putText(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) {
            m.put(key, value.trim());
        }
    }

    private static BigDecimal toBd(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String t = node.asText(null);
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(t.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String t = node.asText(null);
        return t == null || t.isBlank() ? null : t;
    }

    private static String firstText(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode n : nodes) {
            String t = text(n);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static String safeToken(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    /** Convenience: deep-copy JsonNode tree into plain Map for storing in jsonb without password. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        if (node.isObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                m.put(e.getKey(), jsonToJava(e.getValue()));
            }
            return m;
        }
        Object java = jsonToJava(node);
        if (java instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of("value", java);
    }

    private static Object jsonToJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return jsonToMap(node);
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(jsonToJava(n)));
            return list;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.asText();
    }
}
