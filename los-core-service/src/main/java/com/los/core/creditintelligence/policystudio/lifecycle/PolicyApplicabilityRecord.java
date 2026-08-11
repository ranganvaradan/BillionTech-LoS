package com.los.core.creditintelligence.policystudio.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable applicability record for single-policy resolution.
 * Dimensions limited to those available in BillionTechLOS demos: product, facility,
 * borrower/customer type, segment, secured flag, program, loan amount band.
 */
public record PolicyApplicabilityRecord(
        UUID policyVersionId,
        String policyName,
        String policyVersion,
        String policyType,
        String businessStatus,
        List<String> products,
        String facilityType,
        String customerSegment,
        String borrowerType,
        List<String> borrowerTypes,
        String securedUnsecured,
        String programScheme,
        BigDecimal minLoanAmount,
        BigDecimal maxLoanAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String replacesVersion,
        String reasonForChange,
        String approvedBy,
        String checker,
        String createdBy,
        boolean productionAuthorityEnabled,
        Map<String, Object> metadata
) {
    public PolicyApplicabilityRecord {
        products = products == null ? List.of() : List.copyOf(products);
        borrowerTypes = BorrowerTypeScope.normalize(borrowerTypes, borrowerType);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> toBusinessView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyName", policyName);
        m.put("policyVersion", policyVersion);
        m.put("policyType", policyType);
        m.put("status", businessStatus);
        m.put("products", products);
        m.put("facilityType", facilityType);
        m.put("customerSegment", customerSegment);
        m.put("borrowerType", borrowerType);
        m.put("borrowerTypes", borrowerTypes);
        m.put("borrowerTypeSummary", BorrowerTypeScope.summaryLabel(borrowerTypes));
        m.put("securedUnsecured", securedUnsecured);
        m.put("programScheme", programScheme);
        m.put("minLoanAmount", minLoanAmount);
        m.put("maxLoanAmount", maxLoanAmount);
        m.put("effectiveFrom", effectiveFrom == null ? null : effectiveFrom.toString());
        m.put("effectiveUntil", effectiveUntil == null ? null : effectiveUntil.toString());
        m.put("replacesVersion", replacesVersion);
        m.put("reasonForChange", reasonForChange);
        m.put("approvedBy", approvedBy);
        m.put("checker", checker);
        m.put("createdBy", createdBy);
        m.put("productionAuthority", productionAuthorityEnabled ? "ENABLED" : "DISABLED");
        m.put("allowCanonicalAuthority", false);
        return m;
    }

    public boolean isInForceOn(LocalDate asOf) {
        if (asOf == null || effectiveFrom == null) {
            return false;
        }
        if (asOf.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveUntil == null || !asOf.isAfter(effectiveUntil);
    }

    /**
     * Shadow routing match.
     * POLICY-UX-2B null-match safety: when the policy constrains a dimension and the
     * application attribute is absent, result is NOT MATCH (not soft-null success).
     * Unconstrained policy dimensions (null / blank / empty products) still mean All.
     */
    public boolean matchesScope(ApplicationPolicyQuery q) {
        if (q == null) {
            return false;
        }
        if (products != null && !products.isEmpty() && !productsAreWildcard(products)) {
            String product = q.productCode() == null ? "" : q.productCode().trim().toUpperCase(Locale.ROOT);
            if (product.isBlank()) {
                return false;
            }
            boolean hit = products.stream()
                    .filter(Objects::nonNull)
                    .map(p -> p.trim().toUpperCase(Locale.ROOT))
                    .anyMatch(p -> p.equals(product) || p.equals("ALL") || p.equals("*"));
            if (!hit) {
                return false;
            }
        }
        if (!dimensionMatches(facilityType, q.facilityType())) {
            return false;
        }
        if (!dimensionMatches(customerSegment, q.customerSegment())) {
            return false;
        }
        if (!BorrowerTypeScope.matches(borrowerTypes, borrowerType, q.borrowerType())) {
            return false;
        }
        if (!dimensionMatches(securedUnsecured, q.securedUnsecured())) {
            return false;
        }
        if (!dimensionMatches(programScheme, q.programScheme())) {
            return false;
        }
        if (minLoanAmount != null || maxLoanAmount != null) {
            if (q.loanAmount() == null) {
                return false;
            }
            if (minLoanAmount != null && q.loanAmount().compareTo(minLoanAmount) < 0) {
                return false;
            }
            if (maxLoanAmount != null && q.loanAmount().compareTo(maxLoanAmount) > 0) {
                return false;
            }
        }
        return true;
    }

    /** Policy constrains a dimension → app value required and must equal. */
    private static boolean dimensionMatches(String policyValue, String appValue) {
        if (!notBlank(policyValue)) {
            return true; // All
        }
        if (!notBlank(appValue)) {
            return false; // constrained + missing app attribute → NOT MATCH
        }
        return policyValue.equalsIgnoreCase(appValue);
    }

    private static boolean productsAreWildcard(List<String> products) {
        return products.stream()
                .filter(Objects::nonNull)
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                .anyMatch(p -> p.equals("ALL") || p.equals("*"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public static PolicyApplicabilityRecord fromMap(Map<String, Object> m) {
        if (m == null) {
            throw new IllegalArgumentException("applicability map required");
        }
        List<String> products = new ArrayList<>();
        Object p = m.get("products");
        if (p instanceof List<?> list) {
            for (Object o : list) {
                products.add(String.valueOf(o));
            }
        } else if (m.get("productCode") != null) {
            products.add(String.valueOf(m.get("productCode")));
        }
        List<String> borrowerTypes = new ArrayList<>();
        Object btList = m.get("borrowerTypes");
        if (btList instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    borrowerTypes.add(String.valueOf(o));
                }
            }
        }
        return new PolicyApplicabilityRecord(
                m.get("policyVersionId") == null ? UUID.randomUUID()
                        : UUID.fromString(String.valueOf(m.get("policyVersionId"))),
                str(m, "policyName", "Policy"),
                str(m, "policyVersion", "v1"),
                str(m, "policyType", "CREDIT_POLICY"),
                str(m, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT),
                products,
                blankToNull(str(m, "facilityType", null)),
                blankToNull(str(m, "customerSegment", null)),
                blankToNull(str(m, "borrowerType", null)),
                borrowerTypes,
                blankToNull(str(m, "securedUnsecured", null)),
                blankToNull(str(m, "programScheme", null)),
                decimal(m.get("minLoanAmount")),
                decimal(m.get("maxLoanAmount")),
                date(m.get("effectiveFrom")),
                date(m.get("effectiveUntil")),
                blankToNull(str(m, "replacesVersion", null)),
                blankToNull(str(m, "reasonForChange", null)),
                blankToNull(str(m, "approvedBy", null)),
                blankToNull(str(m, "checker", null)),
                blankToNull(str(m, "createdBy", null)),
                Boolean.TRUE.equals(m.get("productionAuthorityEnabled")),
                m.get("metadata") instanceof Map<?, ?> meta
                        ? cast(meta) : Map.of()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? def : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate date(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        String s = String.valueOf(o);
        if (s.length() >= 10) {
            return LocalDate.parse(s.substring(0, 10));
        }
        return LocalDate.parse(s);
    }
}
