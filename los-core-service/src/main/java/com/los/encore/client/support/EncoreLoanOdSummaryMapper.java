package com.los.encore.client.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps {@code GET api/loan-od-accounts/{id}} JSON into the shape expected by
 * {@link com.los.lms.support.EncoreSummaryNormalizer} when webservices findSummaries fails.
 */
public final class EncoreLoanOdSummaryMapper {

    private EncoreLoanOdSummaryMapper() {
    }

    public static Map<String, Object> toSummaryRow(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        putText(row, "accountId", root, "accountId");
        putText(row, "accountBalance", root, "accountBalance");
        putText(row, "operationalStatus", root, "operationalStatus", "displayAccountStatus", "displayStatus");
        putText(row, "normalInterestRate", root, "normalInterestRate", "effectiveInterestRate");
        putText(row, "daysPastDue", root, "daysPastDue", "effectiveDpd", "riskDpd");
        putText(row, "totalDemandDue", root, "totalDemandDue", "payOffAndDueAmount");
        putText(row, "overdueAmount", root, "overdueAmount", "totalDemandDue");
        putText(row, "installmentAmount", root, "installmentAmount", "equatedInstallment");
        putText(row, "amount", root, "amount");
        if (root.has("fees") && root.get("fees").isArray()) {
            row.put("fees", root.get("fees"));
        }
        if (root.has("accountStatement") && root.get("accountStatement").isArray()) {
            row.put("accountStatementEntries", root.get("accountStatement"));
        }
        if (root.has("compositeStatement") && root.get("compositeStatement").isArray()) {
            row.put("compositeStatement", root.get("compositeStatement"));
        }
        return row;
    }

    private static void putText(Map<String, Object> row, String targetKey, JsonNode root, String... sourceKeys) {
        for (String key : sourceKeys) {
            JsonNode n = root.path(key);
            if (!n.isMissingNode() && !n.isNull()) {
                row.put(targetKey, n.asText());
                return;
            }
        }
    }
}
