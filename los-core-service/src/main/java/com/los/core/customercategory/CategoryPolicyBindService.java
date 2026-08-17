package com.los.core.customercategory;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    public static final String POLICY_LIFECYCLE_NOT_ELIGIBLE = "POLICY_LIFECYCLE_NOT_ELIGIBLE";

    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final PolicyCatalogueService policyCatalogueService;
    private final CreditIntelligenceProperties creditIntelligenceProperties;

    /** Optional — same live session identity as Policy Studio when the JVM has the version open. */
    private PolicyStudioPersistenceService persistenceService;

    @Autowired(required = false)
    public void setPersistenceService(PolicyStudioPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

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
        String canonical = PolicyCanonicalLifecycleAuthority.reconcile(
                sessionBusinessStatus(a.getPolicyDocumentId()), a.getBusinessStatus());
        if (!PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(canonical)) {
            throw CustomerCategoryValidator.biz(
                    "Policy Version is not eligible for Customer Category linkage (requires APPROVED/SCHEDULED/ACTIVE)",
                    POLICY_LIFECYCLE_NOT_ELIGIBLE,
                    Map.of(
                            "policyApplicabilityId", a.getId().toString(),
                            "businessStatus", canonical == null ? "" : canonical,
                            "lifecycleAuthority", PolicyCanonicalLifecycleAuthority.NAME));
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
     * Does not silently hide incompatible rows; uses {@link CustomerCategoryPolicyScopeCompatibility}.
     */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.EligiblePolicyView> listEligiblePolicies(
            String entityType,
            String loanProduct,
            String customerRole,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            java.time.Instant effectiveFrom,
            java.time.Instant effectiveUntil) {
        UUID tenant = defaultTenantId();
        List<Map<String, Object>> rows = policyCatalogueService.listCatalogue(tenant);
        var catScope = new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                customerRole, entityType, loanProduct, minAmount, maxAmount, effectiveFrom, effectiveUntil);
        List<CustomerCategoryDtos.EligiblePolicyView> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                out.add(toPickerView(row, catScope));
            } catch (RuntimeException e) {
                // Never fail the picker closed — a single corrupt catalogue row must not 500 the screen.
                out.add(fallbackPickerView(row, e));
            }
        }
        return out;
    }

    /** Backward-compatible overload (no effective dates). */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.EligiblePolicyView> listEligiblePolicies(
            String entityType,
            String loanProduct,
            String customerRole,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return listEligiblePolicies(entityType, loanProduct, customerRole, minAmount, maxAmount, null, null);
    }

    private CustomerCategoryDtos.EligiblePolicyView toPickerView(
            Map<String, Object> row,
            CustomerCategoryPolicyScopeCompatibility.CategoryScope catScope) {
        CustomerCategoryPolicyScopeCompatibility.Result compat =
                CustomerCategoryPolicyScopeCompatibility.evaluateFromCatalogueRow(catScope, row);

        @SuppressWarnings("unchecked")
        List<String> products = row.get("products") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        @SuppressWarnings("unchecked")
        List<String> borrowerTypes = row.get("borrowerTypes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String legacyBt = row.get("borrowerType") == null ? null : String.valueOf(row.get("borrowerType"));
        List<String> entityTypes = borrowerTypes.isEmpty() && legacyBt != null && !legacyBt.isBlank()
                ? List.of(legacyBt) : borrowerTypes;

        List<String> notes = new ArrayList<>();
        for (var check : compat.checks()) {
            if (!check.ok()) {
                notes.add(check.label() + ": " + check.detail());
            }
        }
        if (compat.compatible()) {
            notes.add(0, "Scope compatible — Policy fully covers Category dimensions");
        }

        String catalogueStatus = str(row.get("status"));
        String sessionStatus = sessionBusinessStatus(parseUuid(row.get("documentId")));
        UUID applicabilityId = parseUuid(row.get("applicabilityId"));
        PolicyCanonicalLifecycleAuthority.Projection projection =
                PolicyCanonicalLifecycleAuthority.project(
                        sessionStatus,
                        catalogueStatus,
                        applicabilityId,
                        row.get("contentImmutable") instanceof Boolean b ? b : null);

        return new CustomerCategoryDtos.EligiblePolicyView(
                applicabilityId,
                parseUuid(row.get("documentId")),
                str(row.get("policyName")),
                str(row.get("policyVersion")),
                parseUuid(row.get("policyVersionId")),
                projection.businessStatus(),
                str(row.get("effectiveFrom")),
                str(row.get("effectiveUntil")),
                products,
                entityTypes,
                null,
                asDecimal(row.get("minLoanAmount")),
                asDecimal(row.get("maxLoanAmount")),
                str(row.get("dataReadinessStatus")),
                str(row.get("testsStatus")),
                str(row.get("simulationReviewStatus")),
                str(row.get("shadowEligibility")),
                Boolean.TRUE.equals(row.get("shadowRoutable")),
                "DISABLED",
                false,
                compat.compatible(),
                compat.status(),
                compat.reasons(),
                notes,
                compat.scopeSummary(),
                projection.eligibleForCustomerCategoryLinkage(),
                projection.ownerType(),
                projection.ownerId(),
                projection.lifecycleAuthority(),
                projection.ineligibleReason());
    }

    private CustomerCategoryDtos.EligiblePolicyView fallbackPickerView(Map<String, Object> row, Exception error) {
        UUID applicabilityId = parseUuid(row == null ? null : row.get("applicabilityId"));
        String catalogueStatus = row == null ? null : str(row.get("status"));
        PolicyCanonicalLifecycleAuthority.Projection projection =
                PolicyCanonicalLifecycleAuthority.project(null, catalogueStatus, applicabilityId, null);
        return new CustomerCategoryDtos.EligiblePolicyView(
                applicabilityId,
                parseUuid(row == null ? null : row.get("documentId")),
                row == null ? null : str(row.get("policyName")),
                row == null ? null : str(row.get("policyVersion")),
                null,
                projection.businessStatus(),
                null, null, List.of(), List.of(), null, null, null,
                null, null, null, null, false, "DISABLED", false,
                false,
                CustomerCategoryPolicyScopeCompatibility.STATUS_NEEDS_CONTEXT,
                List.of("PICKER_ROW_UNREADABLE"),
                List.of("Policy row could not be fully evaluated: " + error.getClass().getSimpleName()),
                null,
                projection.eligibleForCustomerCategoryLinkage(),
                projection.ownerType(),
                projection.ownerId(),
                projection.lifecycleAuthority(),
                projection.ineligibleReason());
    }

    private String sessionBusinessStatus(UUID documentId) {
        if (persistenceService == null || documentId == null) {
            return null;
        }
        try {
            PolicyStudioSession session = persistenceService.loadSession(documentId);
            if (session == null || session.getDocument() == null || session.getDocument().getMetadata() == null) {
                return null;
            }
            Object life = session.getDocument().getMetadata().get(PolicyLifecycleService.META_KEY);
            if (life instanceof Map<?, ?> m && m.get("businessStatus") != null) {
                return String.valueOf(m.get("businessStatus"));
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /**
     * Fail-closed typed validation for Category→Policy bind on save / submit / activate.
     */
    @Transactional(readOnly = true)
    public CustomerCategoryPolicyScopeCompatibility.Result requireScopeCompatible(
            CustomerCategoryPolicyScopeCompatibility.CategoryScope categoryScope,
            UUID policyApplicabilityId) {
        CiPolicyApplicability a = requireApplicability(policyApplicabilityId);
        CustomerCategoryPolicyScopeCompatibility.Result r =
                CustomerCategoryPolicyScopeCompatibility.evaluate(categoryScope, a);
        if (!r.compatible()) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("status", r.status());
            ctx.put("reasons", r.reasons());
            ctx.put("checks", r.checks().stream()
                    .map(c -> Map.of("code", c.code(), "ok", c.ok(), "detail", c.detail() == null ? "" : c.detail()))
                    .toList());
            ctx.put("evidence", r.evidence());
            ctx.put("policyApplicabilityId", policyApplicabilityId.toString());
            throw CustomerCategoryValidator.biz(
                    "Policy Scope is not compatible with Customer Category: " + String.join(", ", r.reasons()),
                    CustomerCategoryPolicyScopeCompatibility.POLICY_SCOPE_INCOMPATIBLE,
                    ctx);
        }
        return r;
    }

    /** Non-mutating compatibility scan for current Categories vs catalogue. */
    @Transactional(readOnly = true)
    public List<CustomerCategoryDtos.CategoryPolicyCompatibilityReportRow> compatibilityReport(
            List<CustomerCategoryEntity> categories) {
        UUID tenant = defaultTenantId();
        List<Map<String, Object>> rows = policyCatalogueService.listCatalogue(tenant);
        List<CustomerCategoryDtos.CategoryPolicyCompatibilityReportRow> out = new ArrayList<>();
        for (CustomerCategoryEntity e : categories) {
            var scope = CustomerCategoryPolicyScopeCompatibility.CategoryScope.fromEntity(e);
            int ok = 0;
            int bad = 0;
            int needs = 0;
            for (Map<String, Object> row : rows) {
                var r = CustomerCategoryPolicyScopeCompatibility.evaluateFromCatalogueRow(scope, row);
                switch (r.status()) {
                    case CustomerCategoryPolicyScopeCompatibility.STATUS_COMPATIBLE -> ok++;
                    case CustomerCategoryPolicyScopeCompatibility.STATUS_NEEDS_CONTEXT -> needs++;
                    default -> bad++;
                }
            }
            out.add(new CustomerCategoryDtos.CategoryPolicyCompatibilityReportRow(
                    e.getCode(), e.getName(), e.getStatus() == null ? null : e.getStatus().name(),
                    ok, bad, needs));
        }
        return out;
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
        String sessionStatus = sessionBusinessStatus(a.getPolicyDocumentId());
        String canonical = PolicyCanonicalLifecycleAuthority.reconcile(sessionStatus, st);
        boolean lifecycleOk = PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(canonical);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_LIFECYCLE_OK",
                "Policy Version in governed lifecycle state (APPROVED/SCHEDULED/ACTIVE)",
                lifecycleOk,
                "Policy business status: " + canonical
                        + " (authority=" + PolicyCanonicalLifecycleAuthority.NAME + ")"
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

        CustomerCategoryPolicyScopeCompatibility.Result scope =
                CustomerCategoryPolicyScopeCompatibility.evaluate(e, a);
        checks.add(new CustomerCategoryDtos.ActivationCheck(
                "POLICY_SCOPE_COMPATIBLE",
                "Policy Scope fully covers Customer Category",
                scope.compatible(),
                scope.compatible()
                        ? scope.scopeSummary()
                        : scope.status() + ": " + String.join(", ", scope.reasons())));

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
