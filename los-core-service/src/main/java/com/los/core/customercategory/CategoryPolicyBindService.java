package com.los.core.customercategory;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * STEP-2 Category → Policy Studio Policy Version linkage.
 * Configuration only — does not enable live underwriting, shadow routing, or production authority.
 */
@Service
@RequiredArgsConstructor
public class CategoryPolicyBindService {

    public static final String LINKAGE_REQUIRED = "POLICY_LINKAGE_REQUIRED";
    public static final String POLICY_NOT_FOUND = "POLICY_NOT_FOUND";
    public static final String POLICY_VERSION_INVALID = "POLICY_VERSION_INVALID";
    public static final String POLICY_DOCUMENT_MISMATCH = "POLICY_DOCUMENT_MISMATCH";
    public static final String POLICY_VERSION_LABEL_MISMATCH = "POLICY_VERSION_LABEL_MISMATCH";

    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final PolicyCatalogueService policyCatalogueService;
    private final CreditIntelligenceProperties creditIntelligenceProperties;

    public UUID defaultTenantId() {
        return creditIntelligenceProperties.getDefaultTenantId();
    }

    @Transactional(readOnly = true)
    public CiPolicyApplicability requireApplicability(UUID applicabilityId) {
        if (applicabilityId == null) {
            throw CustomerCategoryValidator.biz("policyApplicabilityId required",
                    "POLICY_APPLICABILITY_REQUIRED", Map.of());
        }
        return applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> CustomerCategoryValidator.biz(
                        "Policy Version not found in Policy Studio catalogue: " + applicabilityId,
                        POLICY_NOT_FOUND,
                        Map.of("policyApplicabilityId", applicabilityId.toString())));
    }

    /**
     * Resolve and validate a Category bind request against Policy Studio catalogue.
     * Optional documentId / versionLabel must agree when supplied.
     */
    @Transactional(readOnly = true)
    public ResolvedPolicyBind resolveBind(
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel) {
        CiPolicyApplicability a = requireApplicability(policyApplicabilityId);
        if (policyDocumentId != null && a.getPolicyDocumentId() != null
                && !policyDocumentId.equals(a.getPolicyDocumentId())) {
            throw CustomerCategoryValidator.biz(
                    "policyDocumentId does not match selected Policy Version",
                    POLICY_DOCUMENT_MISMATCH,
                    Map.of(
                            "policyDocumentId", policyDocumentId.toString(),
                            "expectedDocumentId", a.getPolicyDocumentId().toString()));
        }
        if (policyVersionLabel != null && !policyVersionLabel.isBlank()) {
            String want = policyVersionLabel.trim();
            String have = a.getPolicyVersionLabel() == null ? "" : a.getPolicyVersionLabel().trim();
            if (!want.equalsIgnoreCase(have)) {
                throw CustomerCategoryValidator.biz(
                        "policyVersionLabel does not match selected Policy Version",
                        POLICY_VERSION_LABEL_MISMATCH,
                        Map.of("policyVersionLabel", want, "expected", have));
            }
        }
        if (a.getPolicyDocumentId() == null || a.getPolicyVersionLabel() == null
                || a.getPolicyVersionLabel().isBlank()) {
            throw CustomerCategoryValidator.biz(
                    "Policy catalogue row missing document identity or version label",
                    POLICY_VERSION_INVALID,
                    Map.of("policyApplicabilityId", a.getId().toString()));
        }
        return new ResolvedPolicyBind(
                a.getId(),
                a.getPolicyDocumentId(),
                a.getPolicyVersionLabel(),
                null,
                a.getPolicyName(),
                a.getBusinessStatus(),
                a.getPolicyVersionId());
    }

    public void applyBind(CustomerCategoryEntity e, ResolvedPolicyBind bind) {
        e.setPolicyApplicabilityId(bind.applicabilityId());
        e.setPolicyDocumentId(bind.documentId());
        e.setPolicyVersionLabel(bind.versionLabel());
        if (bind.lineageId() != null) {
            e.setPolicyLineageId(bind.lineageId());
        } else if (e.getPolicyLineageId() == null && bind.documentId() != null) {
            // Default lineage handle = first known document id until Studio metadata is copied.
            e.setPolicyLineageId(bind.documentId());
        }
    }

    public static String linkageStatus(CustomerCategoryEntity e) {
        if (e.getPolicyApplicabilityId() != null
                && e.getPolicyDocumentId() != null
                && e.getPolicyVersionLabel() != null
                && !e.getPolicyVersionLabel().isBlank()) {
            return "LINKED";
        }
        return LINKAGE_REQUIRED;
    }

    /**
     * Lender-facing Policy picker — Policy Studio catalogue authority.
     * Does not silently hide incompatible rows; attaches compatibility notes.
     */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.EligiblePolicyView> listEligiblePolicies(
            String entityType,
            String loanProduct,
            String customerRole,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        UUID tenant = defaultTenantId();
        List<Map<String, Object>> rows = policyCatalogueService.listCatalogue(tenant);
        List<CustomerCategoryDtos.EligiblePolicyView> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(toPickerView(row, entityType, loanProduct, customerRole, minAmount, maxAmount));
        }
        return out;
    }

    private CustomerCategoryDtos.EligiblePolicyView toPickerView(
            Map<String, Object> row,
            String entityType,
            String loanProduct,
            String customerRole,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        List<String> notes = new ArrayList<>();
        boolean compatible = true;

        @SuppressWarnings("unchecked")
        List<String> products = row.get("products") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (loanProduct != null && !loanProduct.isBlank() && !MatchWildcard.isAny(loanProduct)
                && !products.isEmpty()
                && products.stream().noneMatch(p -> p.equalsIgnoreCase(loanProduct.trim()))) {
            compatible = false;
            notes.add("Loan Product not in Policy product scope: " + products);
        }

        @SuppressWarnings("unchecked")
        List<String> borrowerTypes = row.get("borrowerTypes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String legacyBt = row.get("borrowerType") == null ? null : String.valueOf(row.get("borrowerType"));
        if (entityType != null && !entityType.isBlank() && !MatchWildcard.isAny(entityType)) {
            boolean anyBt = borrowerTypes.isEmpty()
                    && (legacyBt == null || legacyBt.isBlank() || "ALL".equalsIgnoreCase(legacyBt));
            if (!anyBt) {
                boolean hit = borrowerTypes.stream().anyMatch(b -> b.equalsIgnoreCase(entityType.trim()))
                        || (legacyBt != null && legacyBt.equalsIgnoreCase(entityType.trim()));
                if (!hit) {
                    compatible = false;
                    notes.add("Entity Type not in Policy borrowerTypes scope");
                }
            }
        }
        if (customerRole != null && !customerRole.isBlank() && !MatchWildcard.isAny(customerRole)) {
            // Customer Role is not yet a first-class Policy Studio applicability column.
            notes.add("Customer Role applicability not modelled on Policy Studio catalogue (informational)");
        }

        BigDecimal pMin = asDecimal(row.get("minLoanAmount"));
        BigDecimal pMax = asDecimal(row.get("maxLoanAmount"));
        if (minAmount != null && pMax != null && minAmount.compareTo(pMax) > 0) {
            compatible = false;
            notes.add("Category min amount above Policy max");
        }
        if (maxAmount != null && pMin != null && maxAmount.compareTo(pMin) < 0) {
            compatible = false;
            notes.add("Category max amount below Policy min");
        }

        String status = str(row.get("status"));
        boolean governedOk = status != null && List.of("DRAFT", "IN REVIEW", "APPROVED", "SCHEDULED", "ACTIVE",
                "SUPERSEDED", "RETIRED").stream().anyMatch(s -> s.equalsIgnoreCase(status));
        if (!governedOk && status != null) {
            notes.add("Unrecognised Policy business status: " + status);
        }

        return new CustomerCategoryDtos.EligiblePolicyView(
                parseUuid(row.get("applicabilityId")),
                parseUuid(row.get("documentId")),
                str(row.get("policyName")),
                str(row.get("policyVersion")),
                parseUuid(row.get("policyVersionId")),
                status,
                str(row.get("effectiveFrom")),
                str(row.get("effectiveUntil")),
                products,
                borrowerTypes.isEmpty() && legacyBt != null ? List.of(legacyBt) : borrowerTypes,
                null,
                pMin,
                pMax,
                str(row.get("dataReadinessStatus")),
                str(row.get("testsStatus")),
                str(row.get("simulationReviewStatus")),
                str(row.get("shadowEligibility")),
                Boolean.TRUE.equals(row.get("shadowRoutable")),
                "DISABLED",
                false,
                compatible,
                notes);
    }

    /** Activation / submit readiness checks for Policy bind (typed). */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.ActivationCheck> policyActivationChecks(CustomerCategoryEntity e) {
        List<CustomerCategoryDtos.ActivationCheck> checks = new ArrayList<>();
        String linkage = linkageStatus(e);
        boolean linked = "LINKED".equals(linkage);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_SELECTED",
                "Policy Version selected",
                linked,
                linked ? "Linked to Policy Studio catalogue"
                        : "POLICY LINKAGE REQUIRED — Category was created under Policy Set model or Policy not selected"));

        if (!linked) {
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "POLICY_VERSION_RESOLVABLE",
                    "Exact Policy Version resolvable",
                    false,
                    "No policyApplicabilityId / document / version label"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "POLICY_LIFECYCLE_OK",
                    "Policy Version in governed lifecycle state",
                    false,
                    "Skipped — no Policy link"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "POLICY_READINESS_OK",
                    "Policy data/readiness requirements satisfied",
                    false,
                    "Skipped — no Policy link"));
            return checks;
        }

        CiPolicyApplicability a = applicabilityRepository.findById(e.getPolicyApplicabilityId()).orElse(null);
        boolean resolvable = a != null
                && a.getPolicyDocumentId() != null
                && a.getPolicyVersionLabel() != null
                && a.getPolicyVersionLabel().equals(e.getPolicyVersionLabel())
                && a.getPolicyDocumentId().equals(e.getPolicyDocumentId());
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_VERSION_RESOLVABLE",
                "Exact Policy Version resolvable",
                resolvable,
                a == null ? "Catalogue row missing"
                        : resolvable ? a.getPolicyName() + " / " + a.getPolicyVersionLabel()
                        : "Stored document/version does not match catalogue row"));

        if (a == null) {
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "POLICY_LIFECYCLE_OK", "Policy Version in governed lifecycle state", false, "Missing"));
            checks.add(new CustomerCategoryDtos.ActivationCheck(
                    "POLICY_READINESS_OK", "Policy data/readiness requirements satisfied", false, "Missing"));
            return checks;
        }

        String st = a.getBusinessStatus() == null ? "" : a.getBusinessStatus().trim().toUpperCase(Locale.ROOT);
        // Future activation may require ACTIVE; readiness documents current state honestly.
        boolean lifecycleOk = List.of("APPROVED", "SCHEDULED", "ACTIVE").contains(st);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_LIFECYCLE_OK",
                "Policy Version in governed lifecycle state (APPROVED/SCHEDULED/ACTIVE)",
                lifecycleOk,
                "Policy business status: " + a.getBusinessStatus()
                        + (lifecycleOk ? "" : " — not eligible for Category activation yet")));

        boolean deprecated = List.of("RETIRED", "SUPERSEDED").contains(st);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_NOT_DEPRECATED",
                "Policy Version not retired/superseded",
                !deprecated,
                deprecated ? "Invalid/deprecated Policy reference: " + st : "OK"));

        String data = a.getDataReadinessStatus();
        boolean dataOk = data == null || data.isBlank()
                || "PASSED".equalsIgnoreCase(data)
                || "PASS".equalsIgnoreCase(data)
                || "READY".equalsIgnoreCase(data)
                || "OK".equalsIgnoreCase(data);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_READINESS_OK",
                "Policy data/readiness requirements satisfied",
                dataOk && !deprecated,
                "dataReadiness=" + data
                        + ", tests=" + a.getTestsStatus()
                        + ", simulation=" + a.getSimulationReviewStatus()
                        + ", productionAuthority=DISABLED (Category bind does not enable live Policy)"));

        return checks;
    }

    public record ResolvedPolicyBind(
            UUID applicabilityId,
            UUID documentId,
            String versionLabel,
            UUID lineageId,
            String policyName,
            String businessStatus,
            UUID enginePolicyVersionId
    ) {}

    private static UUID parseUuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
