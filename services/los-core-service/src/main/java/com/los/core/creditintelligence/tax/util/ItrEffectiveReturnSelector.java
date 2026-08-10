package com.los.core.creditintelligence.tax.util;

import com.los.core.creditintelligence.tax.domain.ReturnVersionType;
import com.los.core.creditintelligence.tax.domain.TaxConstants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Policy {@link TaxConstants#ITR_EFFECTIVE_RETURN_SELECTION_V1}:
 * prefer latest revised/updated over original for the same assessment year.
 */
public final class ItrEffectiveReturnSelector {

    public record Candidate(
            String assessmentYear,
            ReturnVersionType versionType,
            LocalDate filingDate,
            String identityKey,
            Map<String, Object> metadata) {
    }

    public record SelectionResult(
            Candidate effective,
            Candidate original,
            String selectionBasis,
            String selectionVersion,
            List<Candidate> lineage,
            Map<String, Object> evidence) {
    }

    private ItrEffectiveReturnSelector() {
    }

    public static SelectionResult select(List<Candidate> candidates) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("policy", TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1);
        if (candidates == null || candidates.isEmpty()) {
            evidence.put("reason", "NO_CANDIDATES");
            return new SelectionResult(null, null, "NO_CANDIDATES",
                    TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1, List.of(), evidence);
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((Candidate c) -> versionRank(c.versionType())).reversed()
                .thenComparing(c -> c.filingDate() != null ? c.filingDate() : LocalDate.MIN,
                        Comparator.reverseOrder())
                .thenComparing(c -> c.identityKey() != null ? c.identityKey() : "", Comparator.reverseOrder()));

        Candidate effective = sorted.get(0);
        Candidate original = sorted.stream()
                .filter(c -> c.versionType() == ReturnVersionType.ORIGINAL
                        || c.versionType() == ReturnVersionType.UNKNOWN)
                .min(Comparator.comparing(c -> c.filingDate() != null ? c.filingDate() : LocalDate.MAX))
                .orElse(sorted.get(sorted.size() - 1));

        String basis;
        if (effective.versionType() == ReturnVersionType.REVISED
                || effective.versionType() == ReturnVersionType.UPDATED) {
            basis = "PREFER_LATEST_REVISED_OVER_ORIGINAL";
        } else if (effective.versionType() == ReturnVersionType.BELATED) {
            basis = "BELATED_ONLY_AVAILABLE";
        } else {
            basis = "ORIGINAL_OR_SINGLE";
        }
        evidence.put("candidateCount", sorted.size());
        evidence.put("effectiveVersion", effective.versionType() != null
                ? effective.versionType().name() : null);
        evidence.put("selectionBasis", basis);

        return new SelectionResult(effective, original, basis,
                TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1, List.copyOf(sorted), evidence);
    }

    public static int versionRank(ReturnVersionType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case REVISED -> 40;
            case UPDATED -> 35;
            case BELATED -> 20;
            case ORIGINAL -> 10;
            case UNKNOWN -> 5;
        };
    }

    public static ReturnVersionType detectVersionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReturnVersionType.UNKNOWN;
        }
        String u = raw.trim().toUpperCase();
        if (u.contains("REVISED") || u.contains("U/S 139(5)") || Objects.equals(u, "R")) {
            return ReturnVersionType.REVISED;
        }
        if (u.contains("UPDATED") || u.contains("U/S 139(8A)")) {
            return ReturnVersionType.UPDATED;
        }
        if (u.contains("BELATED") || u.contains("U/S 139(4)")) {
            return ReturnVersionType.BELATED;
        }
        if (u.contains("ORIGINAL") || u.contains("U/S 139(1)") || Objects.equals(u, "O")) {
            return ReturnVersionType.ORIGINAL;
        }
        return ReturnVersionType.UNKNOWN;
    }
}
