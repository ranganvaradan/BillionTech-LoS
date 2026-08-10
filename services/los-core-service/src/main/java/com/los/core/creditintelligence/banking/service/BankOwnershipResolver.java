package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.OwnershipMatchStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Resolve account holder name vs borrower name from application (optional).
 */
@Component
public class BankOwnershipResolver {

    public record OwnershipResult(
            OwnershipMatchStatus status,
            BigDecimal score,
            String method) {
    }

    public OwnershipResult resolve(String holderName, String borrowerName) {
        if (holderName == null || holderName.isBlank()
                || borrowerName == null || borrowerName.isBlank()) {
            return new OwnershipResult(OwnershipMatchStatus.UNKNOWN, null, "NAME_MISSING");
        }
        String a = normalizeName(holderName);
        String b = normalizeName(borrowerName);
        if (a.equals(b)) {
            return new OwnershipResult(OwnershipMatchStatus.MATCH, BigDecimal.ONE, "EXACT_MATCH");
        }
        // Token overlap
        String[] ta = a.split(" ");
        String[] tb = b.split(" ");
        int overlap = 0;
        for (String x : ta) {
            for (String y : tb) {
                if (x.equals(y) && x.length() > 1) {
                    overlap++;
                    break;
                }
            }
        }
        double score = ta.length == 0 ? 0 : (double) overlap / Math.max(ta.length, tb.length);
        BigDecimal bd = BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
        if (score >= 0.75) {
            return new OwnershipResult(OwnershipMatchStatus.PROBABLE_MATCH, bd, "TOKEN_OVERLAP");
        }
        if (score >= 0.4) {
            return new OwnershipResult(OwnershipMatchStatus.PROBABLE_MATCH, bd, "PARTIAL_TOKEN_OVERLAP");
        }
        if (score > 0) {
            return new OwnershipResult(OwnershipMatchStatus.MISMATCH, bd, "LOW_TOKEN_OVERLAP");
        }
        return new OwnershipResult(OwnershipMatchStatus.MISMATCH, BigDecimal.ZERO, "NO_OVERLAP");
    }

    static String normalizeName(String name) {
        return name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("\\b(MR|MRS|MS|M/S|M/S\\.|SMT|SHRI|DR)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
