package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyLifecycleEvent;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyLifecycleEventRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyShadowRoutingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable policy catalogue — lifecycle/applicability persistence only.
 * Does not execute credit rules. Never enables production authority.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyCatalogueService {

    public static final String EVT_DRAFT_CREATED = "DRAFT_CREATED";
    public static final String EVT_SUBMITTED_FOR_REVIEW = "SUBMITTED_FOR_REVIEW";
    public static final String EVT_CREDIT_MANAGER_APPROVED = "CREDIT_MANAGER_APPROVED";
    public static final String EVT_CHECKER_APPROVED = "CHECKER_APPROVED";
    public static final String EVT_APPROVED = "APPROVED";
    public static final String EVT_SCHEDULED = "SCHEDULED";
    public static final String EVT_BECAME_ACTIVE = "BECAME_ACTIVE";
    public static final String EVT_SUPERSEDED = "SUPERSEDED";
    public static final String EVT_RETIRED = "RETIRED";
    public static final String EVT_APPROVAL_INVALIDATED = "APPROVAL_INVALIDATED";

    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final CiPolicyLifecycleEventRepository eventRepository;
    private final CiPolicyShadowRoutingRepository routingRepository;
    private final PolicyApplicabilityResolver resolver;
    private final CreditIntelligenceProperties properties;
    private final PolicyShadowEligibilityService eligibilityService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalogue(UUID tenantId) {
        UUID tid = tenantOrDefault(tenantId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiPolicyApplicability a : applicabilityRepository.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            out.add(toBusinessRow(a));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<CiPolicyApplicability> findLatestByDocument(UUID tenantId, UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        List<CiPolicyApplicability> rows = applicabilityRepository
                .findByTenantIdAndPolicyDocumentIdOrderByUpdatedAtDesc(tenantOrDefault(tenantId), documentId);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBusinessView(UUID applicabilityId) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy catalogue entry not found"));
        Map<String, Object> out = toBusinessRow(a);
        out.put("history", eventRepository.findByApplicabilityIdOrderByCreatedAtDesc(a.getId()).stream()
                .map(this::eventView).toList());
        out.put("applicationsEvaluatedShadow", routingRepository.countBySelectedApplicabilityId(a.getId()));
        stampSafety(out);
        return out;
    }

    @Transactional
    public CiPolicyApplicability upsertDraft(UUID tenantId, Map<String, Object> body) {
        UUID tid = tenantOrDefault(tenantId);
        UUID documentId = uuid(body.get("policyDocumentId"));
        String versionLabel = str(body, "policyVersion", "v1");
        CiPolicyApplicability existing = documentId == null ? null
                : applicabilityRepository
                .findByTenantIdAndPolicyDocumentIdAndPolicyVersionLabel(tid, documentId, versionLabel)
                .orElse(null);
        boolean created = existing == null;
        CiPolicyApplicability a = existing != null ? existing : CiPolicyApplicability.builder()
                .tenantId(tid)
                .policyDocumentId(documentId)
                .build();
        if (Boolean.TRUE.equals(a.getContentImmutable())
                && isImmutableStatus(a.getBusinessStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approved / active policy content is immutable. Create New Version.");
        }
        applyBody(a, body);
        if (created || a.getBusinessStatus() == null) {
            a.setBusinessStatus(PolicyBusinessLifecycleStatus.DRAFT);
        }
        a.setProductionAuthorityEnabled(false);
        eligibilityService.apply(a);
        a = applicabilityRepository.save(a);
        recordEvent(a, created ? EVT_DRAFT_CREATED : "DRAFT_UPDATED",
                created ? null : a.getBusinessStatus(),
                a.getBusinessStatus(),
                str(body, "createdBy", str(body, "actor", "system")),
                str(body, "reasonForChange", null),
                Map.of());
        return a;
    }

    @Transactional
    public CiPolicyApplicability transition(
            UUID applicabilityId, String newStatus, String eventType, String actor, String reason) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy catalogue entry not found"));
        String prev = a.getBusinessStatus();
        a.setBusinessStatus(newStatus);
        a.setProductionAuthorityEnabled(false);
        if (PolicyBusinessLifecycleStatus.APPROVED.equals(newStatus)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(newStatus)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(newStatus)) {
            a.setContentImmutable(true);
        }
        eligibilityService.apply(a);
        a = applicabilityRepository.save(a);
        recordEvent(a, eventType, prev, newStatus, actor, reason, Map.of());
        return a;
    }

    /**
     * Schedule with pessimistic overlap lock. Resolution uses evaluation date — no nightly flip required.
     */
    @Transactional
    public Map<String, Object> schedule(UUID applicabilityId, Map<String, Object> body) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy catalogue entry not found"));
        String status = a.getBusinessStatus();
        if (!PolicyBusinessLifecycleStatus.APPROVED.equals(status)
                && !PolicyBusinessLifecycleStatus.SCHEDULED.equals(status)
                && !PolicyBusinessLifecycleStatus.ACTIVE.equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only APPROVED policies can be scheduled. Current: " + status);
        }
        applyBody(a, body);
        if (a.getEffectiveFrom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Effective From is required");
        }
        if (a.getProducts() == null || a.getProducts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Applicable product(s) required");
        }

        // Lock overlapping candidates transactionally
        List<CiPolicyApplicability> locked = applicabilityRepository.findOverlappingForUpdate(
                a.getTenantId(), a.getId(), a.getEffectiveFrom(), a.getEffectiveUntil());
        PolicyApplicabilityRecord candidate = toRecord(a, PolicyBusinessLifecycleStatus.SCHEDULED);
        List<PolicyApplicabilityRecord> others = locked.stream()
                .map(x -> toRecord(x, x.getBusinessStatus()))
                .toList();
        Map<String, Object> overlap = resolver.detectOverlap(candidate, others);
        if (Boolean.TRUE.equals(overlap.get("blocked"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conflicts = overlap.get("conflicts") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            String msg = conflicts.isEmpty()
                    ? "Effective-date / applicability conflict"
                    : String.valueOf(conflicts.get(0).get("message"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
        }

        LocalDate businessAsOf = body.get("businessDate") != null
                ? LocalDate.parse(String.valueOf(body.get("businessDate")).substring(0, 10))
                : a.getEffectiveFrom();
        boolean alreadyActive = !businessAsOf.isBefore(a.getEffectiveFrom());
        String prev = a.getBusinessStatus();
        String next = alreadyActive ? PolicyBusinessLifecycleStatus.ACTIVE : PolicyBusinessLifecycleStatus.SCHEDULED;
        a.setBusinessStatus(next);
        a.setContentImmutable(true);
        a.setProductionAuthorityEnabled(false);
        eligibilityService.apply(a);
        a = applicabilityRepository.save(a);
        recordEvent(a, alreadyActive ? EVT_BECAME_ACTIVE : EVT_SCHEDULED, prev, next,
                str(body, "actor", "credit_manager"),
                str(body, "reason", "Scheduled for shadow applicability"),
                Map.of("effectiveFrom", a.getEffectiveFrom().toString(),
                        "businessDate", businessAsOf.toString(),
                        "shadowRoutable", Boolean.TRUE.equals(a.getShadowRoutable()),
                        "linkageClass", a.getLinkageClass() == null ? "" : a.getLinkageClass()));

        if (alreadyActive && a.getReplacesApplicabilityId() != null) {
            supersede(a.getReplacesApplicabilityId(), a.getPolicyVersionLabel(),
                    str(body, "actor", "system"));
        }

        Map<String, Object> out = toBusinessRow(a);
        stampSafety(out);
        out.put("overlapCheck", overlap);
        out.put("shadowRoutable", a.getShadowRoutable());
        out.put("linkageClass", a.getLinkageClass());
        out.put("shadowEligibility", a.getShadowEligibility());
        if (!Boolean.TRUE.equals(a.getShadowRoutable())) {
            out.put("warning", "Scheduled for catalogue display but NOT eligible for real application shadow routing until immutable package is linked.");
        }
        out.put("message", alreadyActive
                ? "Policy business status ACTIVE for evaluation window. Production authority DISABLED."
                : "Policy SCHEDULED. Applicability uses evaluation date — no wall-clock job required.");
        log.info("policy_catalogue_scheduled id={} status={} products={} from={} shadowRoutable={} authority=false",
                a.getId(), next, a.getProducts(), a.getEffectiveFrom(), a.getShadowRoutable());
        return out;
    }

    @Transactional
    public CiPolicyApplicability supersede(UUID applicabilityId, String replacedBy, String actor) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId).orElse(null);
        if (a == null) {
            return null;
        }
        String prev = a.getBusinessStatus();
        a.setBusinessStatus(PolicyBusinessLifecycleStatus.SUPERSEDED);
        if (a.getEffectiveUntil() == null) {
            a.setEffectiveUntil(LocalDate.now().minusDays(1));
        }
        a.setReasonForChange("Superseded by " + replacedBy);
        a = applicabilityRepository.save(a);
        recordEvent(a, EVT_SUPERSEDED, prev, PolicyBusinessLifecycleStatus.SUPERSEDED, actor,
                "Superseded by " + replacedBy, Map.of("replacedBy", replacedBy));
        return a;
    }

    @Transactional
    public Map<String, Object> retire(UUID applicabilityId, Map<String, Object> body) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy catalogue entry not found"));
        String prev = a.getBusinessStatus();
        a.setBusinessStatus(PolicyBusinessLifecycleStatus.RETIRED);
        if (body.get("effectiveUntil") != null) {
            a.setEffectiveUntil(LocalDate.parse(String.valueOf(body.get("effectiveUntil")).substring(0, 10)));
        }
        a = applicabilityRepository.save(a);
        recordEvent(a, EVT_RETIRED, prev, PolicyBusinessLifecycleStatus.RETIRED,
                str(body, "actor", "credit_manager"), str(body, "reason", "Retired"), Map.of());
        Map<String, Object> out = toBusinessRow(a);
        stampSafety(out);
        out.put("message", "Policy RETIRED. Historical evaluations retain pinned versions.");
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolve(UUID tenantId, ApplicationPolicyQuery query) {
        UUID tid = tenantOrDefault(tenantId);
        List<CiPolicyApplicability> rows = applicabilityRepository.findResolvableByTenant(tid);
        List<PolicyApplicabilityRecord> catalogue = rows.stream()
                .map(a -> toRecord(a, a.getBusinessStatus()))
                .toList();
        Map<String, Object> result = resolver.resolve(query, catalogue);
        stampSafety(result);
        result.put("durableCatalogue", true);
        result.put("catalogueSize", catalogue.size());
        if (PolicyApplicabilityResolver.EXACTLY_ONE.equals(result.get("outcome"))
                && result.get("selectedPolicy") instanceof Map<?, ?> sel) {
            String version = String.valueOf(sel.get("policyVersion"));
            String name = String.valueOf(sel.get("policyName"));
            rows.stream()
                    .filter(a -> version.equals(a.getPolicyVersionLabel()) && name.equals(a.getPolicyName()))
                    .findFirst()
                    .ifPresent(a -> {
                        result.put("resolvedApplicabilityId", a.getId().toString());
                        result.put("resolvedPolicyVersionId",
                                a.getPolicyVersionId() == null ? null : a.getPolicyVersionId().toString());
                        result.put("resolvedExecutablePackageId",
                                a.getExecutablePackageId() == null ? null : a.getExecutablePackageId().toString());
                        result.put("contentHash", a.getContentHash());
                        result.put("packageContentHash", a.getPackageContentHash());
                        result.put("linkageClass", a.getLinkageClass());
                        result.put("shadowEligibility", a.getShadowEligibility());
                        result.put("shadowRoutable", a.getShadowRoutable());
                        result.put("eligibilityDetail", a.getEligibilityDetail());
                        if (!Boolean.TRUE.equals(a.getShadowRoutable())) {
                            String outcome = a.getShadowEligibility() != null
                                    ? a.getShadowEligibility()
                                    : PolicyShadowRoutingOutcomes.POLICY_PACKAGE_NOT_EXECUTABLE;
                            if (PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE.equals(outcome)
                                    || PolicyShadowRoutingOutcomes.LINK_DEMO_NOT_ROUTABLE.equals(a.getLinkageClass())) {
                                outcome = PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE;
                            } else if (a.getPolicyVersionId() == null && a.getExecutablePackageId() == null) {
                                outcome = PolicyShadowRoutingOutcomes.POLICY_PACKAGE_NOT_EXECUTABLE;
                            } else {
                                outcome = PolicyShadowRoutingOutcomes.NOT_ELIGIBLE_FOR_SHADOW_ROUTING;
                            }
                            result.put("outcome", outcome);
                            result.put("selectedPolicy", null);
                            result.put("onePolicySelected", false);
                            result.put("catalogueMatch", sel);
                            result.put("reason", "Catalogue matched product/date but immutable package is not executable for shadow: "
                                    + outcome);
                        }
                    });
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PolicyApplicabilityRecord> loadResolvableRecords(UUID tenantId) {
        return applicabilityRepository.findResolvableByTenant(tenantOrDefault(tenantId)).stream()
                .map(a -> toRecord(a, a.getBusinessStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CiPolicyApplicability require(UUID id) {
        return applicabilityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy catalogue entry not found"));
    }

    public PolicyApplicabilityRecord toRecord(CiPolicyApplicability a, String statusOverride) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (a.getMetadata() != null) {
            a.getMetadata().forEach((k, v) -> {
                if (k != null && v != null) {
                    meta.put(k, v);
                }
            });
        }
        if (a.getId() != null) {
            meta.put("applicabilityId", a.getId().toString());
        }
        if (a.getPolicyVersionId() != null) {
            meta.put("ciPolicyVersionId", a.getPolicyVersionId().toString());
        }
        if (a.getExecutablePackageId() != null) {
            meta.put("executablePackageId", a.getExecutablePackageId().toString());
        }
        if (a.getContentHash() != null) {
            meta.put("contentHash", a.getContentHash());
        }
        if (a.getShadowRoutable() != null) {
            meta.put("shadowRoutable", a.getShadowRoutable());
        }
        if (a.getLinkageClass() != null) {
            meta.put("linkageClass", a.getLinkageClass());
        }
        return new PolicyApplicabilityRecord(
                a.getId(),
                a.getPolicyName(),
                a.getPolicyVersionLabel(),
                a.getPolicyType(),
                statusOverride == null ? a.getBusinessStatus() : statusOverride,
                a.getProducts() == null ? List.of() : a.getProducts(),
                a.getFacilityType(),
                a.getCustomerSegment(),
                a.getBorrowerType(),
                a.getBorrowerTypes() == null ? List.of() : a.getBorrowerTypes(),
                a.getSecuredUnsecured(),
                a.getProgramScheme(),
                a.getMinLoanAmount(),
                a.getMaxLoanAmount(),
                a.getEffectiveFrom(),
                a.getEffectiveUntil(),
                a.getReplacesVersion(),
                a.getReasonForChange(),
                a.getApprovedBy(),
                a.getChecker(),
                a.getCreatedBy(),
                false,
                meta
        );
    }

    public Map<String, Object> toBusinessRow(CiPolicyApplicability a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicabilityId", a.getId() == null ? null : a.getId().toString());
        m.put("policyName", a.getPolicyName());
        m.put("policyVersion", a.getPolicyVersionLabel());
        m.put("policyType", a.getPolicyType());
        m.put("status", a.getBusinessStatus());
        m.put("products", a.getProducts());
        m.put("facilityType", a.getFacilityType());
        m.put("customerSegment", a.getCustomerSegment());
        m.put("borrowerType", a.getBorrowerType());
        m.put("borrowerTypes", a.getBorrowerTypes() == null ? List.of() : a.getBorrowerTypes());
        m.put("borrowerTypeSummary", BorrowerTypeScope.summaryLabel(
                BorrowerTypeScope.normalize(a.getBorrowerTypes(), a.getBorrowerType())));
        m.put("securedUnsecured", a.getSecuredUnsecured());
        m.put("programScheme", a.getProgramScheme());
        m.put("minLoanAmount", a.getMinLoanAmount());
        m.put("maxLoanAmount", a.getMaxLoanAmount());
        m.put("effectiveFrom", a.getEffectiveFrom() == null ? null : a.getEffectiveFrom().toString());
        m.put("effectiveUntil", a.getEffectiveUntil() == null ? null : a.getEffectiveUntil().toString());
        m.put("replaces", a.getReplacesVersion());
        m.put("reasonForChange", a.getReasonForChange());
        m.put("approvedBy", a.getApprovedBy());
        m.put("checker", a.getChecker());
        m.put("createdBy", a.getCreatedBy());
        m.put("dataReadinessStatus", a.getDataReadinessStatus());
        m.put("testsStatus", a.getTestsStatus());
        m.put("simulationReviewStatus", a.getSimulationReviewStatus());
        Map<String, Object> impl = new LinkedHashMap<>();
        impl.put("dataReadiness", a.getDataReadinessStatus());
        impl.put("tests", a.getTestsStatus());
        impl.put("simulation", a.getSimulationReviewStatus());
        m.put("implementationStatus", impl);
        m.put("contentImmutable", a.getContentImmutable());
        m.put("policyVersionId", a.getPolicyVersionId() == null ? null : a.getPolicyVersionId().toString());
        m.put("executablePackageId", a.getExecutablePackageId() == null ? null : a.getExecutablePackageId().toString());
        m.put("contentHash", a.getContentHash());
        m.put("packageContentHash", a.getPackageContentHash());
        m.put("linkageClass", a.getLinkageClass());
        m.put("shadowEligibility", a.getShadowEligibility());
        m.put("shadowRoutable", a.getShadowRoutable());
        m.put("eligibilityDetail", a.getEligibilityDetail());
        m.put("productionAuthority", "DISABLED");
        m.put("allowCanonicalAuthority", false);
        m.put("documentId", a.getPolicyDocumentId() == null ? null : a.getPolicyDocumentId().toString());
        long evaluated = 0L;
        try {
            if (a.getId() != null && routingRepository != null) {
                evaluated = routingRepository.countBySelectedApplicabilityId(a.getId());
            }
        } catch (RuntimeException e) {
            log.debug("shadow evaluation count skipped for {}: {}", a.getId(), e.getClass().getSimpleName());
        }
        m.put("applicationsEvaluatedShadow", evaluated);
        return m;
    }

    private void applyBody(CiPolicyApplicability a, Map<String, Object> body) {
        if (body == null) {
            return;
        }
        if (body.get("policyName") != null) {
            a.setPolicyName(String.valueOf(body.get("policyName")));
        }
        if (body.get("policyVersion") != null) {
            a.setPolicyVersionLabel(String.valueOf(body.get("policyVersion")));
        }
        if (body.get("policyType") != null) {
            a.setPolicyType(String.valueOf(body.get("policyType")));
        }
        if (body.get("products") instanceof List<?> list) {
            List<String> products = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    products.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                }
            }
            a.setProducts(products);
        } else if (body.get("productCode") != null) {
            a.setProducts(List.of(String.valueOf(body.get("productCode")).toUpperCase(Locale.ROOT)));
        }
        if (body.containsKey("facilityType")) {
            a.setFacilityType(blankToNull(str(body, "facilityType", null)));
        }
        if (body.containsKey("customerSegment")) {
            a.setCustomerSegment(blankToNull(str(body, "customerSegment", null)));
        }
        if (body.containsKey("borrowerTypes") || body.containsKey("borrowerType")) {
            List<String> raw = new ArrayList<>();
            if (body.get("borrowerTypes") instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        raw.add(String.valueOf(o));
                    }
                }
            }
            List<String> normalized = BorrowerTypeScope.normalize(raw, str(body, "borrowerType", null));
            a.setBorrowerTypes(normalized);
            a.setBorrowerType(BorrowerTypeScope.legacyScalar(normalized));
        }
        if (body.containsKey("securedUnsecured")) {
            a.setSecuredUnsecured(blankToNull(str(body, "securedUnsecured", null)));
        }
        if (body.containsKey("programScheme")) {
            a.setProgramScheme(blankToNull(str(body, "programScheme", null)));
        }
        if (body.containsKey("minLoanAmount")) {
            a.setMinLoanAmount(decimal(body.get("minLoanAmount")));
        }
        if (body.containsKey("maxLoanAmount")) {
            a.setMaxLoanAmount(decimal(body.get("maxLoanAmount")));
        }
        if (body.get("effectiveFrom") != null && !String.valueOf(body.get("effectiveFrom")).isBlank()) {
            a.setEffectiveFrom(LocalDate.parse(String.valueOf(body.get("effectiveFrom")).substring(0, 10)));
        }
        if (body.containsKey("effectiveUntil")) {
            Object eu = body.get("effectiveUntil");
            a.setEffectiveUntil(eu == null || String.valueOf(eu).isBlank() ? null
                    : LocalDate.parse(String.valueOf(eu).substring(0, 10)));
        }
        if (body.get("replacesVersion") != null) {
            a.setReplacesVersion(String.valueOf(body.get("replacesVersion")));
        }
        if (body.get("replacesApplicabilityId") != null) {
            a.setReplacesApplicabilityId(uuid(body.get("replacesApplicabilityId")));
        }
        if (body.get("reasonForChange") != null) {
            a.setReasonForChange(String.valueOf(body.get("reasonForChange")));
        }
        if (body.get("approvedBy") != null) {
            a.setApprovedBy(String.valueOf(body.get("approvedBy")));
        }
        if (body.get("checker") != null) {
            a.setChecker(String.valueOf(body.get("checker")));
        }
        if (body.get("createdBy") != null) {
            a.setCreatedBy(String.valueOf(body.get("createdBy")));
        }
        if (body.get("dataReadinessStatus") != null) {
            a.setDataReadinessStatus(String.valueOf(body.get("dataReadinessStatus")));
        }
        if (body.get("testsStatus") != null) {
            a.setTestsStatus(String.valueOf(body.get("testsStatus")));
        }
        if (body.get("simulationReviewStatus") != null) {
            a.setSimulationReviewStatus(String.valueOf(body.get("simulationReviewStatus")));
        }
        if (body.get("draftPackageId") != null) {
            a.setDraftPackageId(uuid(body.get("draftPackageId")));
        }
        if (body.get("executablePackageId") != null) {
            a.setExecutablePackageId(uuid(body.get("executablePackageId")));
        }
        if (body.get("policyVersionId") != null) {
            a.setPolicyVersionId(uuid(body.get("policyVersionId")));
        }
        if (a.getPolicyName() == null) {
            a.setPolicyName("Policy");
        }
        if (a.getPolicyVersionLabel() == null) {
            a.setPolicyVersionLabel("v1");
        }
    }

    private void recordEvent(
            CiPolicyApplicability a, String type, String prev, String next,
            String actor, String reason, Map<String, Object> payload) {
        eventRepository.save(CiPolicyLifecycleEvent.builder()
                .tenantId(a.getTenantId())
                .applicabilityId(a.getId())
                .policyDocumentId(a.getPolicyDocumentId())
                .eventType(type)
                .previousStatus(prev)
                .newStatus(next)
                .actor(actor)
                .reason(reason)
                .payload(payload == null ? Map.of() : payload)
                .build());
    }

    private Map<String, Object> eventView(CiPolicyLifecycleEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event", e.getEventType());
        m.put("previousStatus", e.getPreviousStatus());
        m.put("newStatus", e.getNewStatus());
        m.put("actor", e.getActor());
        m.put("reason", e.getReason());
        m.put("at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private boolean isImmutableStatus(String s) {
        String st = PolicyBusinessLifecycleStatus.fromStored(s);
        return PolicyBusinessLifecycleStatus.APPROVED.equals(st)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(st)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(st)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(st)
                || PolicyBusinessLifecycleStatus.RETIRED.equals(st);
    }

    private UUID tenantOrDefault(UUID tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return properties.getDefaultTenantId();
    }

    private void stampSafety(Map<String, Object> out) {
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "DISABLED");
        out.put("productionActive", false);
        out.put("authoritative", false);
        out.put("shadowOnly", true);
    }

    private static String str(Map<String, Object> m, String k, String def) {
        if (m == null || m.get(k) == null) {
            return def;
        }
        String s = String.valueOf(m.get(k));
        return s.isBlank() ? def : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
