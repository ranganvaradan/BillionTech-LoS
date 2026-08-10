package com.los.encore.client.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Encore {@code repaymentSchedule} arrays from a loan summary JSON object (bl-core
 * {@code EncoreServiceFacadeImpl#findRepaymentSchedules} shape) or from
 * {@code GET api/loan-od-accounts/{accountId}} REST responses.
 */
public final class EncoreRepaymentScheduleParser {

    private EncoreRepaymentScheduleParser() {
    }

    /**
     * @param summaryRoot first element returned by findSummaries for a single account (bl-core {@code findSummary}),
     *                    or the root object from {@code api/loan-od-accounts/{accountId}}
     */
    public static List<Map<String, Object>> parseFromSummaryRoot(JsonNode summaryRoot) {
        List<Map<String, Object>> schedules = new ArrayList<>();
        if (summaryRoot == null || !summaryRoot.isObject()) {
            return schedules;
        }
        JsonNode repaymentSchedule = summaryRoot.path("repaymentSchedule");
        if (!repaymentSchedule.isArray()) {
            return schedules;
        }
        for (JsonNode entry : repaymentSchedule) {
            Map<String, Object> scheduleEntry = new LinkedHashMap<>();
            scheduleEntry.put("sequenceNum", entry.path("sequenceNum").asInt(0));
            scheduleEntry.put("description", entry.path("description").asText(""));

            double installment = nodeAmount(entry.path("amount1"), 0);
            double amountDue = nodeAmount(entry.path("amount3"), installment);
            double balance = nodeAmount(entry.path("amount2"), 0);
            double part1 = nodeAmount(entry.path("part1"), 0);
            double part2 = nodeAmount(entry.path("part2"), 0);
            double part3 = nodeAmount(entry.path("part3"), 0);

            scheduleEntry.put("installmentAmount", formatAmount(installment));
            scheduleEntry.put("amountDue", formatAmount(amountDue));
            scheduleEntry.put("valueDateStr", nodeDateStr(entry));
            scheduleEntry.put("normalInterestRate", part1);
            scheduleEntry.put("principalRate", part2);
            scheduleEntry.put("penalInterestRate", part3);
            scheduleEntry.put("balance", formatAmount(balance));

            if (entry.path("part1").isObject() || entry.path("part2").isObject()) {
                scheduleEntry.put("interestAmount", part1);
                scheduleEntry.put("principalAmount", part2 > 0 ? part2 : Math.max(0, installment - part1));
            } else {
                double totalRatePct = part1 + part2;
                if (totalRatePct > 0 && installment > 0) {
                    scheduleEntry.put("interestAmount", installment * part1 / totalRatePct);
                    scheduleEntry.put("principalAmount", installment * part2 / totalRatePct);
                } else {
                    scheduleEntry.put("interestAmount", part1);
                    scheduleEntry.put("principalAmount", part2 > 0 ? part2 : installment);
                }
            }
            scheduleEntry.put("status", "FROM_ENCORE");
            schedules.add(scheduleEntry);
        }
        return schedules;
    }

    private static double nodeAmount(JsonNode node, double defaultVal) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultVal;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return defaultVal;
            }
        }
        if (node.isObject() && node.has("magnitude")) {
            return node.get("magnitude").asDouble(defaultVal);
        }
        return defaultVal;
    }

    private static String nodeDateStr(JsonNode entry) {
        String valueDateStr = entry.path("valueDateStr").asText("");
        if (!valueDateStr.isBlank()) {
            return valueDateStr;
        }
        return entry.path("valueDate").asText("");
    }

    private static String formatAmount(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
