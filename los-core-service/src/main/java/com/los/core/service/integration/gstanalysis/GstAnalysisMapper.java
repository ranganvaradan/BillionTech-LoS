package com.los.core.service.integration.gstanalysis;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Karza GST docs-upload-advance report {@code result} into underwriting OCR-shaped metrics.
 */
public final class GstAnalysisMapper {

    private GstAnalysisMapper() {
    }

    public static Map<String, Object> mapMetrics(JsonNode result) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (result == null || result.isNull() || result.isMissingNode()) {
            return metrics;
        }

        JsonNode current = result.path("current");
        JsonNode profile = result.path("profile");
        JsonNode businessSummary = current.path("businessSummary");
        JsonNode transactionSummary = current.path("transactionSummary");
        JsonNode turnoverAndCustomers = current.path("turnoverAndCustomers");
        JsonNode averages = current.path("averages");

        putText(metrics, "gstin", firstText(result.path("gstin"), profile.path("gstin")));
        putText(metrics, "legalName", text(profile.path("lgnm")));
        putText(metrics, "tradeName", text(profile.path("tradeNam")));
        putText(metrics, "reportType", text(result.path("reportType")));
        putText(metrics, "financialPeriod", text(current.path("financialPeriod")));

        BigDecimal annual = firstBd(
                businessSummary.path("gstTurnoverCyInvVal"),
                transactionSummary.path("turnover").path("ttlVal"),
                turnoverAndCustomers.path("grossTurnover"));
        if (annual != null) {
            metrics.put("annualGstTurnover", annual);
        }

        BigDecimal avgMonth = toBd(averages.path("avgmonthval"));
        if (avgMonth != null) {
            metrics.put("gstIncome", avgMonth.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP));
        } else {
            BigDecimal taxVal = firstBd(businessSummary.path("gstTurnoverCyTaxVal"), averages.path("avgmonthtax"));
            if (taxVal != null) {
                metrics.put("gstIncome", taxVal);
            }
        }

        BigDecimal avgGmv3m = averageLastMonthsGstr1(current.path("monthWiseSummary"), 3);
        if (avgGmv3m != null) {
            metrics.put("avgGmv3m", avgGmv3m);
        }

        metrics.put("active90days", isActiveLastPeriods(current.path("filingStatus"), 3) ? 1 : 0);
        return metrics;
    }

    public static List<Map<String, String>> collectDownloadLinks(JsonNode result, String gstin, String requestId) {
        List<Map<String, String>> links = new ArrayList<>();
        if (result == null || result.isNull()) {
            return links;
        }
        String gstSafe = safeToken(gstin != null ? gstin : text(result.path("gstin")), "GSTIN");
        String reqSafe = safeToken(requestId, "REQ");
        addLink(links, text(result.path("pdfDownloadLink")),
                "GST_ANALYSIS_" + gstSafe + "_" + reqSafe + ".pdf", "application/pdf");
        addLink(links, text(result.path("excelDownloadLink")),
                "GST_ANALYSIS_" + gstSafe + "_" + reqSafe + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return links;
    }

    static BigDecimal averageLastMonthsGstr1(JsonNode monthWiseSummary, int months) {
        if (monthWiseSummary == null || !monthWiseSummary.isArray() || monthWiseSummary.isEmpty()) {
            return null;
        }
        List<JsonNode> rows = new ArrayList<>();
        monthWiseSummary.forEach(rows::add);
        rows.sort(Comparator.comparing((JsonNode n) -> text(n.path("retPeriod")) == null
                ? ""
                : text(n.path("retPeriod"))).reversed());
        int take = Math.min(months, rows.size());
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = 0; i < take; i++) {
            BigDecimal v = toBd(rows.get(i).path("gstr1").path("ttlVal"));
            if (v != null) {
                sum = sum.add(v);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    static boolean isActiveLastPeriods(JsonNode filingStatus, int periods) {
        if (filingStatus == null || !filingStatus.isArray() || filingStatus.isEmpty()) {
            return false;
        }
        List<JsonNode> rows = new ArrayList<>();
        filingStatus.forEach(rows::add);
        rows.sort(Comparator.comparing((JsonNode n) -> text(n.path("retPeriod")) == null
                ? ""
                : text(n.path("retPeriod"))).reversed());
        int take = Math.min(periods, rows.size());
        for (int i = 0; i < take; i++) {
            JsonNode statuses = rows.get(i).path("status");
            if (!statuses.isArray()) {
                continue;
            }
            for (JsonNode st : statuses) {
                String type = firstText(st.path("rtntype"), st.path("returnTy"));
                String status = text(st.path("status"));
                if (type == null || status == null) {
                    continue;
                }
                boolean gstr = "GSTR1".equalsIgnoreCase(type) || "GSTR3B".equalsIgnoreCase(type);
                if (gstr && "Filed".equalsIgnoreCase(status)) {
                    return true;
                }
            }
        }
        return false;
    }

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

    private static void putText(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) {
            m.put(key, value.trim());
        }
    }

    private static BigDecimal firstBd(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode n : nodes) {
            BigDecimal bd = toBd(n);
            if (bd != null) {
                return bd;
            }
        }
        return null;
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
}
