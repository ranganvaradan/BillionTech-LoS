package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.ImmutablePolicyLinkService;
import com.los.core.creditintelligence.policystudio.lifecycle.P2ValidationHarnessService;
import com.los.core.creditintelligence.policystudio.lifecycle.ApplicationPolicyQuery;
import com.los.core.creditintelligence.policystudio.lifecycle.ApplicationPolicyQueryFactory;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCatalogueService;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowApplicationDiscoveryService;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyCatalogueFacade {

    private final PolicyCatalogueService catalogueService;
    private final ShadowApplicationDiscoveryService discoveryService;
    private final ShadowPolicyRoutingService routingService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CreditIntelligenceProperties properties;
    private final ImmutablePolicyLinkService linkService;
    private final P2ValidationHarnessService p2ValidationHarnessService;

    public Map<String, Object> list() {
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("title", "Policy Catalogue");
        out.put("policies", catalogueService.listCatalogue(properties.getDefaultTenantId()));
        out.put("count", ((java.util.List<?>) out.get("policies")).size());
        out.put("note", "Business ACTIVE ≠ production authority. allowCanonicalAuthority=false.");
        return out;
    }

    public Map<String, Object> upsert(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        var saved = catalogueService.upsertDraft(properties.getDefaultTenantId(), payload);
        Object statusObj = payload.get("businessStatus");
        if (statusObj != null) {
            String target = String.valueOf(statusObj).trim();
            if (!target.isBlank() && !"DRAFT".equalsIgnoreCase(target)
                    && !target.equalsIgnoreCase(saved.getBusinessStatus())) {
                String normalized = switch (target.toUpperCase().replace(' ', '_')) {
                    case "IN_REVIEW", "INREVIEW" -> "IN REVIEW";
                    case "APPROVED" -> "APPROVED";
                    case "SCHEDULED" -> "APPROVED"; // schedule endpoint performs SCHEDULED/ACTIVE
                    default -> target.toUpperCase();
                };
                saved = catalogueService.transition(
                        saved.getId(),
                        normalized,
                        "STATUS_SET",
                        payload.get("actor") == null ? "ops" : String.valueOf(payload.get("actor")),
                        payload.get("reason") == null ? null : String.valueOf(payload.get("reason")));
            }
        }
        return catalogueService.getBusinessView(saved.getId());
    }

    public Map<String, Object> get(UUID id) {
        return catalogueService.getBusinessView(id);
    }

    public Map<String, Object> schedule(UUID id, Map<String, Object> body) {
        return catalogueService.schedule(id, body);
    }

    public Map<String, Object> retire(UUID id, Map<String, Object> body) {
        return catalogueService.retire(id, body);
    }

    public Map<String, Object> linkImmutable(UUID id, Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (Boolean.TRUE.equals(payload.get("publishDemoPackage"))
                || Boolean.TRUE.equals(payload.get("createPackage"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = payload.get("policyContent") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : null;
            return linkService.publishImmutableShadowPackageAndLink(
                    id, content, payload.get("actor") == null ? "p2" : String.valueOf(payload.get("actor")));
        }
        UUID versionId = payload.get("policyVersionId") == null ? null
                : UUID.fromString(String.valueOf(payload.get("policyVersionId")));
        UUID execId = payload.get("executablePackageId") == null ? null
                : UUID.fromString(String.valueOf(payload.get("executablePackageId")));
        return linkService.linkExisting(id, versionId, execId);
    }

    public Map<String, Object> reclassifyCatalogue() {
        return linkService.reclassifyAll(properties.getDefaultTenantId());
    }

    public Map<String, Object> runP2Validation(Map<String, Object> body) {
        String product = body == null || body.get("productCode") == null ? null
                : String.valueOf(body.get("productCode"));
        String actor = body == null || body.get("actor") == null ? "p2" : String.valueOf(body.get("actor"));
        return p2ValidationHarnessService.runValidation(product, actor);
    }

    public Map<String, Object> p2Dashboard() {
        return p2ValidationHarnessService.latestDashboard();
    }

    public Map<String, Object> discover() {
        return discoveryService.discover();
    }

    public Map<String, Object> applicablePolicy(UUID applicationId, String evaluationDate) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        LocalDate asOf = evaluationDate == null || evaluationDate.isBlank() ? null
                : LocalDate.parse(evaluationDate.substring(0, 10));
        ApplicationPolicyQuery query = ApplicationPolicyQueryFactory.fromLoanApplication(app, asOf);
        Map<String, Object> resolved = catalogueService.resolve(properties.getDefaultTenantId(), query);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("title", "Applicable Policy");
        out.put("evaluationMode", "Shadow");
        out.put("applicationId", applicationId.toString());
        out.put("applicationNumber", app.getApplicationNumber());
        out.put("product", app.getLoanProduct());
        out.put("evaluationBusinessDate", query.evaluationDate() == null ? null : query.evaluationDate().toString());
        out.put("resolverOutcome", resolved.get("outcome"));
        out.put("reason", resolved.get("reason"));
        out.put("selectedPolicy", resolved.get("selectedPolicy"));
        out.put("evidence", ApplicationPolicyQueryFactory.evidence(app, query.evaluationDate(), resolved));
        out.put("latestRouting", routingService.latestForApplication(applicationId));
        if (resolved.get("selectedPolicy") instanceof Map<?, ?> sel) {
            out.put("policyName", sel.get("policyName"));
            out.put("policyVersion", sel.get("policyVersion"));
            out.put("effectiveFrom", sel.get("effectiveFrom"));
            out.put("effectiveUntil", sel.get("effectiveUntil"));
        }
        return out;
    }

    public Map<String, Object> shadowRoute(UUID applicationId, Map<String, Object> body) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        LocalDate asOf = body.get("evaluationDate") == null ? null
                : LocalDate.parse(String.valueOf(body.get("evaluationDate")).substring(0, 10));
        return routingService.routeShadow(app, asOf, null)
                .map(r -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    StagingDemoWorkspaceService.stampSafety(out);
                    out.put("outcome", r.outcome());
                    out.put("policyName", r.policyName());
                    out.put("policyVersion", r.policyVersion());
                    out.put("policyVersionId", r.policyVersionId() == null ? null : r.policyVersionId().toString());
                    out.put("executablePackageId", r.executablePackageId() == null ? null : r.executablePackageId().toString());
                    out.put("contentHash", r.contentHash());
                    out.put("executableForShadow", r.executableForShadow());
                    out.put("reason", r.reason());
                    out.put("evaluationBusinessDate",
                            r.evaluationBusinessDate() == null ? null : r.evaluationBusinessDate().toString());
                    out.put("evidence", r.evidence());
                    out.put("routingRecordId", r.routingRecordId() == null ? null : r.routingRecordId().toString());
                    out.put("resolvePayload", r.resolvePayload());
                    out.put("evaluationMode", "Shadow");
                    return out;
                })
                .orElseGet(() -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    StagingDemoWorkspaceService.stampSafety(out);
                    out.put("outcome", "ROUTING_ERROR");
                    out.put("message", "Shadow routing failed cleanly — production underwriting unaffected.");
                    return out;
                });
    }
}
