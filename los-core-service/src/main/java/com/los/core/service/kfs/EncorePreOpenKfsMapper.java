package com.los.core.service.kfs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.encore.client.support.EncoreRepaymentScheduleParser;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Encore {@code findPreOpenSummary} / {@code findSummaries} JSON (stored on KFS charges)
 * into KFS financial figures.
 */
@Slf4j
public final class EncorePreOpenKfsMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EncorePreOpenKfsMapper() {
    }

    public record Figures(
            BigDecimal installmentAmount,
            BigDecimal totalRepayment,
            BigDecimal totalInterest,
            BigDecimal apr,
            BigDecimal processingFee,
            int installmentCount) {
    }

    public static Optional<Figures> fromCharges(Map<String, Object> charges, BigDecimal principal) {
        if (charges == null || principal == null) {
            return Optional.empty();
        }

        JsonNode root = null;
        Object raw = charges.get("encorePreOpenSummaryJson");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                root = MAPPER.readTree(String.valueOf(raw));
                if (root.isArray() && !root.isEmpty()) {
                    root = root.get(0);
                }
                if (!root.isObject()) {
                    root = null;
                }
            } catch (Exception e) {
                log.warn("Could not parse encorePreOpenSummaryJson for KFS: {}", e.getMessage());
            }
        }

        try {
            List<Map<String, Object>> schedule = root != null
                    ? resolveSchedule(root, charges)
                    : parseStoredSchedule(charges.get("encoreRepaymentScheduleJson"));
            if (schedule.isEmpty()) {
                return Optional.empty();
            }

            BigDecimal installment = firstInstallment(schedule, root);
            BigDecimal totalRepayment = sumInstallments(schedule);
            if (totalRepayment.compareTo(BigDecimal.ZERO) <= 0 && root != null) {
                totalRepayment = extractRootAmount(root,
                        "totalRepayment", "totalAmountPayable", "totalDemandDue", "payOffAmount")
                        .orElse(totalRepayment);
            }
            BigDecimal totalInterest = totalRepayment.subtract(principal).max(BigDecimal.ZERO);
            BigDecimal apr = root != null ? extractApr(root).orElse(null) : null;
            BigDecimal processingFee = root != null ? extractProcessingFee(root).orElse(null) : null;

            return Optional.of(new Figures(
                    installment,
                    totalRepayment,
                    totalInterest,
                    apr,
                    processingFee,
                    schedule.size()));
        } catch (Exception e) {
            log.warn("Could not map Encore LMS figures for KFS: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static List<Map<String, Object>> resolveSchedule(JsonNode root, Map<String, Object> charges) {
        List<Map<String, Object>> schedule = EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);
        if (!schedule.isEmpty()) {
            return schedule;
        }
        return parseStoredSchedule(charges.get("encoreRepaymentScheduleJson"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseStoredSchedule(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows;
            if (raw instanceof String s) {
                if (s.isBlank()) {
                    return List.of();
                }
                rows = MAPPER.readValue(s, new TypeReference<>() {});
            } else if (raw instanceof List<?> list) {
                rows = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        m.forEach((k, v) -> row.put(String.valueOf(k), v));
                        rows.add(row);
                    }
                }
            } else {
                rows = MAPPER.convertValue(raw, new TypeReference<>() {});
            }
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                Object installment = row.get("installmentAmount");
                if (installment == null) {
                    installment = row.get("amountDue");
                }
                if (installment == null) {
                    installment = row.get("amount1");
                }
                if (installment != null) {
                    row.put("installmentAmount", String.valueOf(installment));
                }
                normalized.add(row);
            }
            return normalized;
        } catch (Exception e) {
            log.debug("Could not parse encoreRepaymentScheduleJson for KFS figures: {}", e.getMessage());
            return List.of();
        }
    }

    private static BigDecimal firstInstallment(List<Map<String, Object>> schedule, JsonNode root) {
        Object fromSchedule = schedule.get(0).get("installmentAmount");
        if (fromSchedule != null) {
            BigDecimal parsed = tryToBigDecimal(fromSchedule);
            if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }
        if (root == null) {
            return BigDecimal.ZERO;
        }
        for (String key : List.of("installmentAmount", "emiAmount", "nextInstallmentAmount", "nextEmiAmount")) {
            if (root.has(key) && !root.get(key).isNull()) {
                BigDecimal parsed = tryToBigDecimal(root.get(key));
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    return parsed;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal sumInstallments(List<Map<String, Object>> schedule) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> row : schedule) {
            Object amt = row.get("installmentAmount");
            if (amt == null) {
                amt = row.get("amountDue");
            }
            if (amt != null) {
                sum = sum.add(tryToBigDecimal(amt));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static Optional<BigDecimal> extractRootAmount(JsonNode root, String... keys) {
        for (String key : keys) {
            if (root.has(key) && !root.get(key).isNull()) {
                BigDecimal parsed = tryToBigDecimal(root.get(key));
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    return Optional.of(parsed);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> extractApr(JsonNode root) {
        for (String key : List.of(
                "apr",
                "annualPercentageRate",
                "annualPercentRate",
                "effectiveApr",
                "effectiveAnnualPercentageRate",
                "xirr",
                "irr")) {
            if (root.has(key) && !root.get(key).isNull()) {
                BigDecimal parsed = tryToBigDecimal(root.get(key));
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    return Optional.of(parsed);
                }
            }
        }
        return Optional.empty();
    }

    /** Mirrors bl-core fee aggregation on {@code findPreOpenSummary} / {@code findSummaries} rows. */
    private static Optional<BigDecimal> extractProcessingFee(JsonNode root) {
        if (root.has("totalFeeDue") && !root.get("totalFeeDue").isNull()) {
            BigDecimal parsed = tryToBigDecimal(root.get("totalFeeDue"));
            if (parsed.compareTo(BigDecimal.ZERO) >= 0) {
                return Optional.of(parsed);
            }
        }
        JsonNode fees = root.path("fees");
        if (!fees.isArray()) {
            return Optional.empty();
        }
        BigDecimal processingFee = BigDecimal.ZERO;
        boolean found = false;
        for (JsonNode fee : fees) {
            String param1 = fee.path("param1").asText("");
            if (param1.isBlank()) {
                continue;
            }
            String ref = fee.path("reference").asText("");
            if (!"false".equalsIgnoreCase(ref)) {
                continue;
            }
            BigDecimal amount = tryToBigDecimal(fee.path("amount1"));
            if ("Processing Fee".equalsIgnoreCase(param1) || "Billion Loans Fee".equalsIgnoreCase(param1)) {
                processingFee = processingFee.add(amount);
                found = true;
            }
        }
        return found ? Optional.of(processingFee.setScale(2, RoundingMode.HALF_UP)) : Optional.empty();
    }

    private static BigDecimal tryToBigDecimal(Object value) {
        try {
            return toBigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof JsonNode node) {
            if (node.isNumber()) {
                return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            if (node.isTextual()) {
                return parseDecimalText(node.asText());
            }
            if (node.isObject() && node.has("magnitude")) {
                return BigDecimal.valueOf(node.get("magnitude").asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return parseDecimalText(String.valueOf(value));
    }

    /** Encore often returns {@code "14.25%"} or currency-formatted amounts. */
    static BigDecimal parseDecimalText(String raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        String cleaned = raw.trim()
                .replace("₹", "")
                .replace("Rs.", "")
                .replace("INR", "")
                .replace("%", "")
                .replace(",", "")
                .trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
    }
}
