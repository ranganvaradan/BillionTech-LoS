package com.los.lms.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.lms.entity.LmsAccountSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Encore {@code findSummaries} JSON fields into {@link LmsAccountSummary}, tolerating key variants
 * between raw vendor JSON and the richer {@code EncoreFindSummariesDto} shape produced in bl-core facades.
 */
public final class EncoreSummaryNormalizer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EncoreSummaryNormalizer() {
    }

    public static void applyEncoreRowToAccountSummary(LmsAccountSummary entity, Map<String, Object> row) {
        if (entity == null || row == null || row.isEmpty()) {
            return;
        }
        firstBigDecimal(row,
                "accountBalance",
                "principalDue",
                "payOffPrincipalDue",
                "loanAmount"
        ).ifPresent(entity::setOutstandingPrincipal);

        firstString(row,
                "operationalStatus",
                "loanStatus",
                "accountStatus"
        ).ifPresent(entity::setLoanStatus);

        firstInteger(row, "daysPastDue", "dpd").ifPresent(entity::setDpd);

        firstBigDecimal(row, "overdueAmount", "totalDemandDue").ifPresent(entity::setOverdueAmount);

        firstLocalDate(row, "nextRepaymentDate", "nextEmiDate").ifPresent(entity::setNextEmiDate);

        firstBigDecimal(row, "installmentAmount", "nextInstallmentAmount").ifPresent(entity::setNextEmiAmount);

        applyFeeBucketsFromEncoreRow(entity, row);
        applyAccountStatementEntriesFromEncoreRow(entity, row);

        entity.setLastSyncedAt(Instant.now());
    }

    private static void applyAccountStatementEntriesFromEncoreRow(LmsAccountSummary entity, Map<String, Object> row) {
        if (!row.containsKey("accountStatementEntries")) {
            return;
        }
        Object raw = row.get("accountStatementEntries");
        if (raw == null) {
            entity.setEncoreAccountStatementEntriesJson(null);
            return;
        }
        try {
            entity.setEncoreAccountStatementEntriesJson(JSON.writeValueAsString(raw));
        } catch (Exception e) {
            entity.setEncoreAccountStatementEntriesJson(String.valueOf(raw));
        }
    }

    /**
     * Mirrors bl-core {@code EncoreServiceFacadeImpl#toFindSummariesDto} fee aggregation
     * ({@code fees[]} → blFeeDue / lenderFeeDue) plus {@code totalFeeDue} override on BL bucket.
     */
    private static void applyFeeBucketsFromEncoreRow(LmsAccountSummary entity, Map<String, Object> row) {
        if (!row.containsKey("fees")) {
            firstBigDecimal(row, "totalFeeDue").ifPresent(entity::setFeeBlDue);
            return;
        }
        Object feesObj = row.get("fees");
        BigDecimal billionLoansFee = BigDecimal.ZERO;
        BigDecimal totalLenderFees = BigDecimal.ZERO;
        if (feesObj instanceof List<?> feeList) {
            for (Object feeObject : feeList) {
                if (!(feeObject instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> fee = new java.util.LinkedHashMap<>();
                rawMap.forEach((k, v) -> fee.put(String.valueOf(k), v));
                Object p1 = fee.get("param1");
                if (p1 == null) {
                    continue;
                }
                String param1 = p1.toString();
                String ref = Optional.ofNullable(fee.get("reference")).map(Object::toString).orElse("");
                BigDecimal amount1 = parseAmount(fee.get("amount1"));
                if (amount1 == null) {
                    continue;
                }
                if ("Billion Loans Fee".equalsIgnoreCase(param1)) {
                    if ("false".equalsIgnoreCase(ref)) {
                        billionLoansFee = billionLoansFee.add(amount1);
                    }
                } else {
                    if ("false".equalsIgnoreCase(ref)) {
                        totalLenderFees = totalLenderFees.add(amount1);
                    }
                }
                if ("Processing Fee".equalsIgnoreCase(param1)) {
                    billionLoansFee = billionLoansFee.add(amount1);
                }
            }
        }
        entity.setFeeBlDue(billionLoansFee.setScale(0, RoundingMode.FLOOR));
        entity.setFeeLenderDue(totalLenderFees);
        firstBigDecimal(row, "totalFeeDue").ifPresent(entity::setFeeBlDue);
    }

    private static BigDecimal parseAmount(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SafeVarargs
    private static Optional<BigDecimal> firstBigDecimal(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (!row.containsKey(k) || row.get(k) == null) {
                continue;
            }
            try {
                return Optional.of(new BigDecimal(String.valueOf(row.get(k))));
            } catch (NumberFormatException ignored) {
                // try next key
            }
        }
        return Optional.empty();
    }

    @SafeVarargs
    private static Optional<String> firstString(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (!row.containsKey(k) || row.get(k) == null) {
                continue;
            }
            String s = String.valueOf(row.get(k)).trim();
            if (!s.isEmpty()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    @SafeVarargs
    private static Optional<Integer> firstInteger(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (!row.containsKey(k) || row.get(k) == null) {
                continue;
            }
            Object v = row.get(k);
            if (v instanceof Number n) {
                return Optional.of(n.intValue());
            }
            try {
                return Optional.of(Integer.parseInt(String.valueOf(v).trim()));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return Optional.empty();
    }

    @SafeVarargs
    private static Optional<LocalDate> firstLocalDate(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (!row.containsKey(k) || row.get(k) == null) {
                continue;
            }
            Object v = row.get(k);
            if (v instanceof LocalDate ld) {
                return Optional.of(ld);
            }
            if (v instanceof java.util.Date d) {
                return Optional.of(LocalDate.ofInstant(d.toInstant(), ZoneId.systemDefault()));
            }
            if (v instanceof Instant i) {
                return Optional.of(LocalDate.ofInstant(i, ZoneId.systemDefault()));
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                if (s.length() >= 10 && s.charAt(4) == '-') {
                    return Optional.of(LocalDate.parse(s.substring(0, 10)));
                }
                return Optional.of(LocalDate.parse(s));
            } catch (DateTimeParseException ignored) {
                // try next key
            }
        }
        return Optional.empty();
    }
}
