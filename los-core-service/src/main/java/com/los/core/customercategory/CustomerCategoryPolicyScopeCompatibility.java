package com.los.core.customercategory;

import com.los.core.creditintelligence.policystudio.lifecycle.BorrowerTypeScope;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Single authority for Customer Category ↔ Policy Studio Scope compatibility.
 * Configuration / governance only — does not enable live routing or Policy production authority.
 */
public final class CustomerCategoryPolicyScopeCompatibility {

    public static final String STATUS_COMPATIBLE = "COMPATIBLE";
    public static final String STATUS_INCOMPATIBLE = "INCOMPATIBLE";
    public static final String STATUS_NEEDS_CONTEXT = "NEEDS_ADDITIONAL_SCOPE_CONTEXT";

    public static final String POLICY_SCOPE_INCOMPATIBLE = "POLICY_SCOPE_INCOMPATIBLE";
    public static final String CUSTOMER_ROLE_NOT_COVERED = "CUSTOMER_ROLE_NOT_COVERED";
    public static final String ENTITY_TYPE_NOT_COVERED = "ENTITY_TYPE_NOT_COVERED";
    public static final String PRODUCT_NOT_COVERED = "PRODUCT_NOT_COVERED";
    public static final String AMOUNT_RANGE_NOT_COVERED = "AMOUNT_RANGE_NOT_COVERED";
    public static final String EFFECTIVE_PERIOD_NOT_COVERED = "EFFECTIVE_PERIOD_NOT_COVERED";
    public static final String ADDITIONAL_SCOPE_CONTEXT_REQUIRED = "ADDITIONAL_SCOPE_CONTEXT_REQUIRED";

    private CustomerCategoryPolicyScopeCompatibility() {}

    public record CategoryScope(
            String customerRole,
            String entityType,
            String loanProduct,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Instant effectiveFrom,
            Instant effectiveUntil
    ) {
        public static CategoryScope fromEntity(CustomerCategoryEntity e) {
            if (e == null) {
                return new CategoryScope(null, null, null, null, null, null, null);
            }
            return new CategoryScope(
                    e.getIntakeSegment(),
                    e.getBorrowerType(),
                    e.getLoanProduct(),
                    e.getMinAmount(),
                    e.getMaxAmount(),
                    e.getEffectiveFrom(),
                    e.getEffectiveUntil());
        }
    }

    public record ScopeCheck(String code, String label, boolean ok, String detail) {}

    public record Result(
            boolean compatible,
            String status,
            List<ScopeCheck> checks,
            List<String> reasons,
            Map<String, Object> evidence,
            String scopeSummary
    ) {}

    public static Result evaluate(CategoryScope category, CiPolicyApplicability policy) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(policy, "policy");

        List<ScopeCheck> checks = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();

        evidence.put("policyApplicabilityId", policy.getId());
        evidence.put("policyName", policy.getPolicyName());
        evidence.put("policyVersionLabel", policy.getPolicyVersionLabel());

        if (categoryContextAbsent(category)) {
            return new Result(
                    false,
                    STATUS_NEEDS_CONTEXT,
                    List.of(new ScopeCheck("categoryContext", "Category scope provided", false,
                            "Picker opened without Category dimensions")),
                    List.of(ADDITIONAL_SCOPE_CONTEXT_REQUIRED),
                    Map.copyOf(evidence),
                    scopeSummary(policy,
                            BorrowerTypeScope.normalize(policy.getBorrowerTypes(), policy.getBorrowerType()),
                            policy.getProducts() == null ? List.of() : policy.getProducts()));
        }

        // --- Customer Role ---
        List<String> policyRoles = extractOptionalStringList(policy, "customerRoles", "customerRole",
                "intakeSegments", "intakeSegment");
        // Policy Studio stores Borrower/Anchor on customerSegment (intake relationship), not MSME segment.
        if (policyRoles.isEmpty() && isIntakeRelationship(policy.getCustomerSegment())) {
            policyRoles = List.of(policy.getCustomerSegment().trim().toUpperCase(Locale.ROOT));
        }
        evidence.put("policyCustomerRoles", policyRoles);
        evidence.put("categoryCustomerRole", category.customerRole());
        boolean roleOk;
        String roleDetail;
        if (policyRoles.isEmpty()) {
            roleOk = true;
            roleDetail = "Policy has no Customer Role column/constraint — treated as unconstrained (ANY)";
        } else if (category.customerRole() == null || category.customerRole().isBlank()
                || MatchWildcard.isAny(category.customerRole())) {
            roleOk = true;
            roleDetail = "Category Customer Role is ANY / unset — no specific Role to cover";
        } else if (policyRoles.stream().anyMatch(MatchWildcard::isAny)
                || policyRoles.stream().anyMatch(r -> "ALL".equalsIgnoreCase(r))) {
            roleOk = true;
            roleDetail = "Policy Customer Role scope is ANY/ALL";
        } else {
            String catRole = category.customerRole().trim().toUpperCase(Locale.ROOT);
            roleOk = policyRoles.stream().anyMatch(r -> tokenKey(r).equals(tokenKey(catRole)));
            roleDetail = roleOk
                    ? "Category Customer Role covered by Policy metadata Role list"
                    : "Category Customer Role " + catRole + " not in Policy Role scope " + policyRoles;
            if (!roleOk) {
                reasons.add(CUSTOMER_ROLE_NOT_COVERED);
            }
        }
        checks.add(new ScopeCheck("customerRole", "Customer Role coverage", roleOk, roleDetail));

        // --- Entity Type ---
        List<String> policyEntityTypes = BorrowerTypeScope.normalize(policy.getBorrowerTypes(), policy.getBorrowerType());
        evidence.put("policyEntityTypes", policyEntityTypes.isEmpty() ? List.of("ANY") : policyEntityTypes);
        evidence.put("categoryEntityType", category.entityType());
        boolean entityOk;
        String entityDetail;
        if (category.entityType() == null || category.entityType().isBlank()
                || MatchWildcard.isAny(category.entityType())) {
            entityOk = true;
            entityDetail = "Category Entity Type is ANY / unset";
        } else if (BorrowerTypeScope.isAll(policyEntityTypes)) {
            entityOk = true;
            entityDetail = "Policy Entity Type scope is ANY/ALL";
        } else {
            entityOk = BorrowerTypeScope.matches(policy.getBorrowerTypes(), policy.getBorrowerType(), category.entityType());
            entityDetail = entityOk
                    ? "Category Entity Type covered"
                    : "Category Entity Type " + category.entityType()
                    + " not covered by Policy " + policyEntityTypes;
            if (!entityOk) {
                reasons.add(ENTITY_TYPE_NOT_COVERED);
            }
        }
        checks.add(new ScopeCheck("entityType", "Entity Type coverage", entityOk, entityDetail));

        // --- Product ---
        List<String> products = policy.getProducts() == null ? List.of() : policy.getProducts();
        evidence.put("policyProducts", products);
        evidence.put("categoryLoanProduct", category.loanProduct());
        boolean productOk;
        String productDetail;
        if (category.loanProduct() == null || category.loanProduct().isBlank()
                || MatchWildcard.isAny(category.loanProduct())) {
            productOk = true;
            productDetail = "Category Loan Product is ANY / unset";
        } else if (products.isEmpty()
                || products.stream().anyMatch(p -> MatchWildcard.isAny(p) || "ALL".equalsIgnoreCase(p))) {
            productOk = true;
            productDetail = "Policy product scope is ANY/ALL (empty or explicit)";
        } else {
            String want = tokenKey(category.loanProduct());
            productOk = products.stream().anyMatch(p -> tokenKey(p).equals(want));
            productDetail = productOk
                    ? "Category Loan Product covered"
                    : "Category Loan Product " + category.loanProduct()
                    + " not covered by Policy products " + products;
            if (!productOk) {
                reasons.add(PRODUCT_NOT_COVERED);
            }
        }
        checks.add(new ScopeCheck("product", "Loan Product coverage", productOk, productDetail));

        // --- Amount (full coverage required) ---
        BigDecimal pMin = policy.getMinLoanAmount();
        BigDecimal pMax = policy.getMaxLoanAmount();
        BigDecimal cMin = category.minAmount();
        BigDecimal cMax = category.maxAmount();
        evidence.put("policyAmount", Map.of(
                "min", pMin == null ? "UNBOUNDED" : pMin,
                "max", pMax == null ? "UNBOUNDED" : pMax));
        evidence.put("categoryAmount", Map.of(
                "min", cMin == null ? "UNBOUNDED" : cMin,
                "max", cMax == null ? "UNBOUNDED" : cMax));
        boolean amountOk = true;
        StringBuilder amountDetail = new StringBuilder();
        // Policy must cover entire Category range (partial overlap insufficient).
        if (cMin != null && pMin != null && pMin.compareTo(cMin) > 0) {
            amountOk = false;
            amountDetail.append("Policy min ").append(pMin).append(" > Category min ").append(cMin).append("; ");
        }
        if (cMax != null && pMax != null && pMax.compareTo(cMax) < 0) {
            amountOk = false;
            amountDetail.append("Policy max ").append(pMax).append(" < Category max ").append(cMax).append("; ");
        }
        // Category unbounded below requires Policy unbounded below
        if (cMin == null && pMin != null) {
            amountOk = false;
            amountDetail.append("Category unbounded below but Policy min=").append(pMin).append("; ");
        }
        if (cMax == null && pMax != null) {
            amountOk = false;
            amountDetail.append("Category unbounded above but Policy max=").append(pMax).append("; ");
        }
        if (amountOk) {
            amountDetail.append("Policy amount range fully covers Category amount range (inclusive)");
        } else {
            reasons.add(AMOUNT_RANGE_NOT_COVERED);
        }
        checks.add(new ScopeCheck("amountRange", "Amount range full coverage", amountOk, amountDetail.toString().trim()));

        // --- Effective period ---
        LocalDate pFrom = policy.getEffectiveFrom();
        LocalDate pUntil = policy.getEffectiveUntil();
        LocalDate cFrom = toLocalDate(category.effectiveFrom());
        LocalDate cUntil = toLocalDate(category.effectiveUntil());
        evidence.put("policyEffective", Map.of(
                "from", pFrom == null ? "UNBOUNDED" : pFrom.toString(),
                "until", pUntil == null ? "UNBOUNDED" : pUntil.toString()));
        evidence.put("categoryEffective", Map.of(
                "from", cFrom == null ? "UNBOUNDED" : cFrom.toString(),
                "until", cUntil == null ? "UNBOUNDED" : cUntil.toString()));
        boolean periodOk = true;
        StringBuilder periodDetail = new StringBuilder();
        if (cFrom != null && pFrom != null && cFrom.isBefore(pFrom)) {
            periodOk = false;
            periodDetail.append("Category starts before Policy effectiveFrom; ");
        }
        if (cFrom != null && pUntil != null && cFrom.isAfter(pUntil)) {
            periodOk = false;
            periodDetail.append("Category starts after Policy effectiveUntil; ");
        }
        if (cUntil != null && pUntil != null && cUntil.isAfter(pUntil)) {
            periodOk = false;
            periodDetail.append("Category ends after Policy effectiveUntil; ");
        }
        if (cUntil != null && pFrom != null && cUntil.isBefore(pFrom)) {
            periodOk = false;
            periodDetail.append("Category ends before Policy effectiveFrom; ");
        }
        // Category open-ended → Policy must also be open-ended (no hidden future invalidity)
        if (cUntil == null && pUntil != null) {
            periodOk = false;
            periodDetail.append("Category has no end date but Policy ends on ")
                    .append(pUntil)
                    .append(" — Policy must be open-ended or Category must end on/before Policy; ");
        }
        // If Policy has no effectiveFrom, treat as unconstrained start (catalogue rows may be draft)
        if (periodOk) {
            periodDetail.append("Category effective period falls within Policy Version effective period");
        } else {
            reasons.add(EFFECTIVE_PERIOD_NOT_COVERED);
        }
        checks.add(new ScopeCheck("effectivePeriod", "Effective period coverage", periodOk, periodDetail.toString().trim()));

        // --- Additional Policy-only dimensions ---
        List<String> extra = new ArrayList<>();
        if (nonBlank(policy.getCustomerSegment()) && !isIntakeRelationship(policy.getCustomerSegment())) {
            extra.add("customerSegment=" + policy.getCustomerSegment());
        }
        if (nonBlank(policy.getSecuredUnsecured())) {
            extra.add("securedUnsecured=" + policy.getSecuredUnsecured());
        }
        if (nonBlank(policy.getProgramScheme())) {
            extra.add("programScheme=" + policy.getProgramScheme());
        }
        if (nonBlank(policy.getFacilityType())) {
            extra.add("facilityType=" + policy.getFacilityType());
        }
        evidence.put("policyAdditionalScope", extra);
        boolean additionalOk = extra.isEmpty();
        String additionalDetail = additionalOk
                ? "No additional Policy-only scope constraints"
                : "Policy has additional scope Category cannot prove: " + extra;
        if (!additionalOk) {
            reasons.add(ADDITIONAL_SCOPE_CONTEXT_REQUIRED);
        }
        checks.add(new ScopeCheck("additionalScope", "Additional Policy scope context", additionalOk, additionalDetail));

        boolean hardFail = !(roleOk && entityOk && productOk && amountOk && periodOk);
        String status;
        if (hardFail) {
            status = STATUS_INCOMPATIBLE;
        } else if (!additionalOk) {
            status = STATUS_NEEDS_CONTEXT;
        } else {
            status = STATUS_COMPATIBLE;
        }
        boolean compatible = STATUS_COMPATIBLE.equals(status);
        return new Result(compatible, status, List.copyOf(checks), List.copyOf(reasons),
                Map.copyOf(evidence), scopeSummary(policy, policyEntityTypes, products));
    }

    public static Result evaluate(CustomerCategoryEntity category, CiPolicyApplicability policy) {
        return evaluate(CategoryScope.fromEntity(category), policy);
    }

    public static Result evaluateFromCatalogueRow(CategoryScope category, Map<String, Object> row) {
        CiPolicyApplicability a = applicabilityFromCatalogueRow(row);
        return evaluate(category, a);
    }

    public static CiPolicyApplicability applicabilityFromCatalogueRow(Map<String, Object> row) {
        if (row == null) {
            return CiPolicyApplicability.builder().policyName("?").policyVersionLabel("?").build();
        }
        @SuppressWarnings("unchecked")
        List<String> products = row.get("products") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        @SuppressWarnings("unchecked")
        List<String> borrowerTypes = row.get("borrowerTypes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String legacyBt = row.get("borrowerType") == null ? null : String.valueOf(row.get("borrowerType"));
        Map<String, Object> meta = new LinkedHashMap<>();
        if (row.get("metadata") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> meta.put(String.valueOf(k), v));
        }
        if (row.get("eligibilityDetail") instanceof Map<?, ?> m) {
            // expose eligibility keys for optional Role extraction
            Map<String, Object> ed = new LinkedHashMap<>();
            m.forEach((k, v) -> ed.put(String.valueOf(k), v));
            meta.put("_eligibilityDetail", ed);
        }
        return CiPolicyApplicability.builder()
                .id(asUuid(row.get("applicabilityId")))
                .policyDocumentId(asUuid(row.get("documentId")))
                .policyName(str(row.get("policyName"), "?"))
                .policyVersionLabel(str(row.get("policyVersion"), "?"))
                .businessStatus(str(row.get("status"), null))
                .products(new ArrayList<>(products))
                .borrowerTypes(new ArrayList<>(borrowerTypes))
                .borrowerType(legacyBt)
                .facilityType(blankToNull(str(row.get("facilityType"), null)))
                .customerSegment(blankToNull(str(row.get("customerSegment"), null)))
                .securedUnsecured(blankToNull(str(row.get("securedUnsecured"), null)))
                .programScheme(blankToNull(str(row.get("programScheme"), null)))
                .minLoanAmount(asDecimal(row.get("minLoanAmount")))
                .maxLoanAmount(asDecimal(row.get("maxLoanAmount")))
                .effectiveFrom(asLocalDate(row.get("effectiveFrom")))
                .effectiveUntil(asLocalDate(row.get("effectiveUntil")))
                .metadata(meta)
                .build();
    }

    public static String scopeSummary(CiPolicyApplicability policy, List<String> entityTypes, List<String> products) {
        String et = BorrowerTypeScope.isAll(entityTypes) ? "ANY Entity Type"
                : String.join(", ", entityTypes);
        String prod = products == null || products.isEmpty() ? "ANY Product" : String.join(", ", products);
        String amt = formatAmount(policy.getMinLoanAmount()) + " – " + formatAmount(policy.getMaxLoanAmount());
        String eff = (policy.getEffectiveFrom() == null ? "open" : policy.getEffectiveFrom().toString())
                + " → "
                + (policy.getEffectiveUntil() == null ? "open" : policy.getEffectiveUntil().toString());
        return et + " · " + prod + " · " + amt + " · " + eff;
    }

    private static List<String> extractOptionalStringList(CiPolicyApplicability policy, String... keys) {
        List<String> out = new ArrayList<>();
        Map<String, Object> meta = policy.getMetadata() == null ? Map.of() : policy.getMetadata();
        collectStrings(meta, keys, out);
        Object ed = meta.get("_eligibilityDetail");
        if (ed instanceof Map<?, ?> edMap) {
            Map<String, Object> asMap = new LinkedHashMap<>();
            edMap.forEach((k, v) -> asMap.put(String.valueOf(k), v));
            collectStrings(asMap, keys, out);
        }
        if (policy.getEligibilityDetail() != null) {
            collectStrings(policy.getEligibilityDetail(), keys, out);
        }
        return out.stream().distinct().toList();
    }

    private static void collectStrings(Map<String, Object> map, String[] keys, List<String> out) {
        if (map == null) {
            return;
        }
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        out.add(String.valueOf(o).trim());
                    }
                }
            } else if (v != null && !String.valueOf(v).isBlank()) {
                out.add(String.valueOf(v).trim());
            }
        }
    }

    /** Normalize product/role tokens: BUSINESS_TERM_LOAN == "BUSINESS TERM LOAN". */
    static String tokenKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static LocalDate toLocalDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate asLocalDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        // datetime-local / ISO instant → date portion
        if (s.length() >= 10 && s.charAt(4) == '-') {
            return LocalDate.parse(s.substring(0, 10));
        }
        return LocalDate.parse(s);
    }

    private static BigDecimal asDecimal(Object o) {
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
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.UUID asUuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o, String dflt) {
        if (o == null) {
            return dflt;
        }
        String s = String.valueOf(o);
        return s.isBlank() ? dflt : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Borrower / Anchor is Policy Studio intake relationship, not a commercial customer segment. */
    static boolean isIntakeRelationship(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        return "BORROWER".equals(n) || "ANCHOR".equals(n);
    }

    private static boolean categoryContextAbsent(CategoryScope category) {
        return category == null
                || (blank(category.customerRole())
                && blank(category.entityType())
                && blank(category.loanProduct())
                && category.minAmount() == null
                && category.maxAmount() == null
                && category.effectiveFrom() == null
                && category.effectiveUntil() == null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String formatAmount(BigDecimal v) {
        return v == null ? "unbounded" : v.stripTrailingZeros().toPlainString();
    }
}
