package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Staging CEO review APIs — aggregate existing CI harnesses. Never CANONICAL authority.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/staging-demo")
@RequiredArgsConstructor
public class StagingDemoController {

    private final CreditIntelligenceProperties properties;
    private final StagingDemoWorkspaceService workspaceService;
    private final StagingPolicyStudioDemoService policyStudioDemoService;
    private final StagingReplayDemoService replayDemoService;
    private final StagingProspectSimulationService prospectSimulationService;
    private final PolicyStudioTestExperienceService policyStudioTestExperienceService;
    private final StagingProspectApprovalService prospectApprovalService;
    private final PolicyCatalogueFacade policyCatalogueFacade;
    private final CreditCapabilityCatalogueService creditCapabilityCatalogueService;
    private final com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService policyLifecycleService;
    private final com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycApplicationEvaluationFacade shadowKycFacade;
    private final com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService shadowKycEvaluationService;
    private final com.los.core.creditintelligence.decisionpolicy.sim.DecisionPolicyEndToEndSimulationService decisionPolicyE2eSimulationService;
    private final com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyRealCorpusValidationService decisionPolicyRealCorpusValidationService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/health")
    public Map<String, Object> health(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("status", "UP");
        out.put("stagingDemoEnabled", properties.getStagingDemo() != null
                && properties.getStagingDemo().isEnabled());
        out.put("validationEnabled", properties.getValidation() != null
                && properties.getValidation().isEnabled());
        out.put("allowCanonicalAuthority", properties.getCutover() != null
                && properties.getCutover().isAllowCanonicalAuthority());
        out.put("aiProvider", properties.getAiUnderwriter() == null
                ? "stub" : properties.getAiUnderwriter().getProvider());
        return out;
    }

    @GetMapping("/cases")
    public Map<String, Object> cases(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("cases", workspaceService.listCases());
        out.put("count", workspaceService.listCases().size());
        return out;
    }

    @GetMapping("/cases/{caseCode}/workspace")
    public Map<String, Object> workspace(
            @PathVariable String caseCode,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        // Tenant header respected when provided (logged for audit); harness uses configured default.
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            log.info("staging-demo workspace tenant header present case={}", caseCode);
        }
        try {
            return workspaceService.buildWorkspace(caseCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/policy-studio")
    public Map<String, Object> policyStudioLanding(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> landing = policyStudioDemoService.landing();
        // POLICY-STUDIO-DURABLE-LANDING-LIST-1 — membership already from durable documents.
        // Catalogue merge may only enrich / fill orphan catalogue rows; never hide durables.
        // Deduplicate strictly by policy document ID (never by name).
        try {
            Map<String, Object> cat = policyCatalogueFacade.list();
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> catalogue =
                    cat.get("policies") instanceof java.util.List<?> l
                            ? (java.util.List<Map<String, Object>>) l : java.util.List.of();
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> existing =
                    landing.get("existingPolicies") instanceof java.util.List<?> l
                            ? new java.util.ArrayList<>((java.util.List<Map<String, Object>>) l)
                            : new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Map<String, Object> row : existing) {
                if (row.get("documentId") != null) seen.add(String.valueOf(row.get("documentId")));
            }
            for (Map<String, Object> c : catalogue) {
                String docId = c.get("documentId") == null ? null : String.valueOf(c.get("documentId"));
                if (docId != null && !docId.isBlank() && seen.contains(docId)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("documentId", docId);
                row.put("applicabilityId", c.get("applicabilityId"));
                row.put("policyName", c.get("policyName"));
                Object catalogueStatus = c.get("status");
                row.put("status", catalogueStatus);
                row.put("lifecycleStatus", catalogueStatus);
                row.put("approvalStatus", catalogueStatus);
                row.put("lifecycleAuthority",
                        com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority.NAME);
                row.put("policyVersion", c.get("policyVersion"));
                row.put("products", c.get("products"));
                row.put("effectiveFrom", c.get("effectiveFrom"));
                row.put("kind", "catalogue");
                row.put("underwritingRuleCount", c.get("underwritingRuleCount"));
                row.put("needsInputCount", c.get("needsInputCount"));
                row.put("membershipAuthority", "CATALOGUE_ORPHAN");
                row.put("availableActions", policyLifecycleService.landingActionsForCatalogueRow(c));
                existing.add(row);
                if (docId != null) seen.add(docId);
            }
            landing.put("existingPolicies", existing);
            landing.put("existingPolicyCount", existing.size());
        } catch (Exception e) {
            log.warn("policy landing catalogue merge skipped reason={}", e.getClass().getSimpleName());
            @SuppressWarnings("unchecked")
            java.util.List<?> existing = landing.get("existingPolicies") instanceof java.util.List<?> l
                    ? l : java.util.List.of();
            landing.put("existingPolicyCount", existing.size());
        }
        return landing;
    }

    /** POLICY-CREATION-1 — Start from scratch draft. */
    @PostMapping("/policy-studio/create")
    public Map<String, Object> createPolicyFromScratch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.createFromScratch(body == null ? Map.of() : body, userId, tenantHeader);
    }

    /** POLICY-CREATION-1 — Copy existing policy into a new draft (source unchanged). */
    @PostMapping("/policy-studio/documents/{documentId}/copy")
    public Map<String, Object> copyPolicy(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.copyPolicy(documentId, body == null ? Map.of() : body, tenantHeader);
    }

    /**
     * POLICY-UX-2A — universal credit capability catalogue (read model).
     * Does not modify production underwriting configuration.
     */
    @GetMapping("/policy-studio/capabilities")
    public Map<String, Object> creditCapabilities(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "advanced", required = false, defaultValue = "false") boolean advanced) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.catalogueView(advanced);
    }

    @GetMapping("/policy-studio/capabilities/{capabilityId}")
    public Map<String, Object> creditCapability(
            @PathVariable String capabilityId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "advanced", required = false, defaultValue = "false") boolean advanced) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.findById(capabilityId)
                .map(c -> {
                    Map<String, Object> view = new LinkedHashMap<>(c.toBusinessView(advanced));
                    view.put("allowCanonicalAuthority", false);
                    return view;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown capability: " + capabilityId));
    }

    @PostMapping("/policy-studio/capabilities/map-production-rule")
    public Map<String, Object> mapProductionRule(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.mapProductionHardRule(body == null ? Map.of() : body);
    }

    @GetMapping("/policy-studio/capabilities/production-fixture-mappings")
    public Map<String, Object> productionFixtureMappings(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.mapProductionFixtureSample();
    }

    @GetMapping("/policy-studio/capabilities/search")
    public Map<String, Object> searchCapabilities(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "advanced", required = false, defaultValue = "false") boolean advanced) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.search(q, advanced);
    }

    /**
     * POLICY-PARAMETER-RESOLVER-1 — CanonicalParameterRegistry browse/search (read model only).
     */
    @GetMapping("/policy-studio/parameters")
    public Map<String, Object> parameterCatalogue(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "includeNonSelectable", required = false) Boolean includeNonSelectableParam) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        var registry = com.los.core.creditintelligence.policystudio.parameters
                .PolicyStudioConvergencePresenter.registry();
        boolean includeNonSelectable = Boolean.TRUE.equals(includeNonSelectableParam);
        if (source != null && !source.isBlank()) {
            return includeNonSelectable
                    ? registry.browseBySource(source)
                    : com.los.core.creditintelligence.policystudio.parameters
                    .PolicyAuthorableParameterProjection.browseBySource(registry, source);
        }
        return includeNonSelectable
                ? registry.catalogueView()
                : com.los.core.creditintelligence.policystudio.parameters
                .PolicyAuthorableParameterProjection.catalogueView(registry);
    }

    @GetMapping("/policy-studio/parameters/search")
    public Map<String, Object> searchParameters(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "q", required = false) String q) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return com.los.core.creditintelligence.policystudio.parameters
                .PolicyAuthorableParameterProjection.search(
                        com.los.core.creditintelligence.policystudio.parameters
                                .PolicyStudioConvergencePresenter.registry(),
                        q);
    }

    @PostMapping("/policy-studio/parameters/propose-definition")
    public Map<String, Object> proposeParameterDefinition(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        String term = body == null || body.get("term") == null ? "" : String.valueOf(body.get("term"));
        String description = body == null || body.get("description") == null
                ? "" : String.valueOf(body.get("description"));
        var planner = new com.los.core.creditintelligence.policystudio.parameters
                .ParameterDerivationPlanner(
                com.los.core.creditintelligence.policystudio.parameters
                        .PolicyStudioConvergencePresenter.registry());
        Map<String, Object> proposal = planner.propose(term, description);
        proposal.put("actions", java.util.List.of("USE_THIS_DEFINITION", "EDIT", "CANCEL"));
        proposal.put("autoAccepted", false);
        return proposal;
    }

    /**
     * POLICY-STUDIO-GATE2 — authoritative business-concept → parameter resolution.
     * Optional {@code source} constrains search (source-guided override).
     */
    @PostMapping("/policy-studio/parameters/resolve-concept")
    public Map<String, Object> resolveBusinessConcept(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        String concept = body == null || body.get("concept") == null
                ? (body == null || body.get("term") == null ? "" : String.valueOf(body.get("term")))
                : String.valueOf(body.get("concept"));
        String source = body == null || body.get("source") == null ? null : String.valueOf(body.get("source"));
        return com.los.core.creditintelligence.policystudio.parameters.BusinessConceptResolver
                .resolve(concept, source);
    }

    /**
     * POLICY-STUDIO-GATE3 — authoritative executability for a canonical parameter.
     */
    @GetMapping("/policy-studio/parameters/{parameterId}/executability")
    public Map<String, Object> parameterExecutability(
            @PathVariable String parameterId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport
                .evaluate(parameterId);
    }

    @PostMapping("/policy-studio/parameters/executability/batch")
    public Map<String, Object> parameterExecutabilityBatch(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        @SuppressWarnings("unchecked")
        java.util.List<String> ids = body == null || body.get("parameterIds") == null
                ? java.util.List.of()
                : (java.util.List<String>) body.get("parameterIds");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String id : ids) {
            rows.add(com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport
                    .evaluate(id));
        }
        out.put("parameters", rows);
        out.put("count", rows.size());
        return out;
    }

    @GetMapping("/policy-studio/capabilities/scf-representability")
    public Map<String, Object> scfRepresentability(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return creditCapabilityCatalogueService.scfRepresentability();
    }

    @GetMapping("/policy-studio/{kind}")
    public Map<String, Object> policyStudio(
            @PathVariable String kind,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.build(kind);
    }

    @PostMapping(value = "/policy-studio/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> policyStudioUpload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.upload(file, userId, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo policy upload failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not process this policy upload. Please try a different file.");
        }
    }

    @PostMapping("/cases/{caseCode}/replay")
    public Map<String, Object> replay(
            @PathVariable String caseCode,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return replayDemoService.replay(caseCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/policy-studio/documents/{documentId}")
    public Map<String, Object> policyStudioSession(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.sessionView(documentId, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("policy-studio sessionView failed documentId={} reason={}",
                    documentId, e.toString(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Policy Studio session could not be projected");
        }
    }

    @GetMapping("/policy-studio/documents/{documentId}/implementability")
    public Map<String, Object> policyImplementability(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            Map<String, Object> view = policyStudioDemoService.sessionView(documentId, tenantHeader);
            Object impl = view.get("implementability");
            if (impl instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> out = new java.util.LinkedHashMap<>((Map<String, Object>) m);
                out.put("documentId", documentId.toString());
                out.put("allowCanonicalAuthority", false);
                return out;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Implementability not available");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("policy-studio implementability failed documentId={} reason={}",
                    documentId, e.toString(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Policy Studio session could not be projected");
        }
    }

    @GetMapping("/policy-studio/documents/{documentId}/business-measures/designer")
    public Map<String, Object> openBusinessMeasureDesigner(
            @PathVariable UUID documentId,
            @RequestParam String dataElementCode,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.openBusinessMeasureDesigner(documentId, dataElementCode, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/business-measures/confirm")
    public Map<String, Object> confirmBusinessMeasure(
            @PathVariable UUID documentId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.confirmBusinessMeasure(documentId, body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/business-measures/{measureId}/approve")
    public Map<String, Object> approveBusinessMeasure(
            @PathVariable UUID documentId,
            @PathVariable String measureId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.approveBusinessMeasure(
                documentId, measureId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/{kind}/reset")
    public Map<String, Object> resetDemoPolicy(
            @PathVariable String kind,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.resetDemo(kind);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not reset demo policy");
        }
    }

    /**
     * POLICY-RESOLUTION-PERSISTENCE-P0 — simulate JVM/process restart for golden tests.
     * Wipes in-memory sessions; durable resolution overlays remain on disk.
     */
    @PostMapping("/policy-studio/simulate-process-restart")
    public Map<String, Object> simulateProcessRestart(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        policyStudioDemoService.simulateProcessRestart();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("simulated", true);
        out.put("inMemoryCleared", true);
        out.put("durableOverlaysRetained", true);
        out.put("message", "In-memory Policy Studio sessions cleared; durable resolution store retained");
        return out;
    }

    @PostMapping("/policy-studio/documents/{documentId}/ambiguities/{id}/resolve")
    public Map<String, Object> resolveAmbiguity(
            @PathVariable UUID documentId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.resolveAmbiguity(documentId, id, body == null ? Map.of() : body, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo ambiguity resolve failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not save this resolution. Please try again.");
        }
    }

    @PostMapping("/policy-studio/documents/{documentId}/rules/{id}/review")
    public Map<String, Object> reviewRule(
            @PathVariable UUID documentId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.reviewRule(documentId, id, body == null ? Map.of() : body, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo rule review failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not record this rule review. Please try again.");
        }
    }

    /**
     * POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 — preview EMI Bounce Count (same calculator as Policy Test).
     */
    @PostMapping("/policy-studio/documents/{documentId}/data-calculations/preview")
    public Map<String, Object> previewDataCalculation(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.previewDataCalculation(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    /** POLICY-RULE-AUTHORING-FIX-1 — source/parameter picker for CM rule authoring. */
    @GetMapping("/policy-studio/rule-authoring/sources")
    public Map<String, Object> ruleAuthoringSources(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.authoringSources(tenantHeader);
    }

    /** POLICY-RULE-AUTHORING-FIX-1 — preview structured or plain-English rule (no persist). */
    @PostMapping("/policy-studio/documents/{documentId}/rules/preview")
    public Map<String, Object> previewAuthoredRule(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.previewAuthoredRule(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/rules/add-plain-english")
    public Map<String, Object> addPlainEnglishRule(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.addPlainEnglishRule(
                    documentId, body == null ? Map.of() : body, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo add plain-english rule failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not add this rule. Please try again.");
        }
    }

    /**
     * POLICY-UX-2C — add/edit a catalogue capability into the current draft (business parameters, no DSL UX).
     */
    @PostMapping("/policy-studio/documents/{documentId}/rules/add-catalogue-capability")
    public Map<String, Object> addCatalogueCapabilityRule(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return policyStudioDemoService.addCatalogueCapabilityRule(
                    documentId, body == null ? Map.of() : body, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo add catalogue capability failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not add this capability to the draft. Please check parameters and try again.");
        }
    }

    /** @deprecated Prefer document-scoped resolve path. Kept for older clients. */
    @PostMapping("/ambiguities/{id}/resolve")
    public Map<String, Object> resolveAmbiguityLegacy(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestParam UUID documentId,
            @RequestBody Map<String, Object> body) {
        return resolveAmbiguity(documentId, id, token, tenantHeader, body);
    }

    @GetMapping("/policy-studio/simulation/applications")
    public Map<String, Object> simulationApplications(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(value = "dataSource", required = false) String dataSource) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectSimulationService.listApplications(dataSource);
    }

    /** POLICY-UX-2E — Credit Manager Test experience context (modes, required params, apps). */
    @GetMapping("/policy-studio/documents/{documentId}/test")
    public Map<String, Object> policyTestContext(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioTestExperienceService.testContext(documentId, tenantHeader);
    }

    /** POLICY-UX-2E — Quick Test against draft rules (simulation-only values). */
    @PostMapping("/policy-studio/documents/{documentId}/test/quick")
    public Map<String, Object> policyQuickTest(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioTestExperienceService.runQuickTest(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    /** POLICY-UX-2E — Existing application test (read-only; does not mutate application). */
    @PostMapping("/policy-studio/documents/{documentId}/test/application")
    public Map<String, Object> policyApplicationTest(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioTestExperienceService.runApplicationTest(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/simulation")
    public Map<String, Object> simulationContext(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectSimulationService.simulationContext(documentId, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/simulate")
    public Map<String, Object> runSimulation(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        try {
            return prospectSimulationService.runSimulation(
                    documentId, body == null ? Map.of() : body, tenantHeader);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("staging-demo simulation failed reason={}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not complete this policy simulation. Please try again.");
        }
    }

    @GetMapping("/policy-studio/documents/{documentId}/simulations")
    public Map<String, Object> simulationHistory(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectSimulationService.listHistory(documentId, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/simulations/{runId}")
    public Map<String, Object> simulationRun(
            @PathVariable UUID documentId,
            @PathVariable UUID runId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectSimulationService.getRun(documentId, runId, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/simulations/{runId}/export.csv")
    public ResponseEntity<byte[]> exportSimulationCsv(
            @PathVariable UUID documentId,
            @PathVariable UUID runId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        byte[] csv = prospectSimulationService.exportCsv(documentId, runId, tenantHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"policy-simulation-" + runId + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    // ─── Day 4 Approvals / Draft / Tests ───────────────────────────

    @GetMapping("/policy-studio/demo-actors")
    public Map<String, Object> demoActors(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.demoActors();
    }

    @GetMapping("/policy-studio/documents/{documentId}/approvals")
    public Map<String, Object> approvalsContext(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.approvalsContext(documentId, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/approvals/credit-manager")
    public Map<String, Object> approveCreditManager(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.submitCreditManagerApproval(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/approvals/checker")
    public Map<String, Object> approveChecker(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.submitCheckerApproval(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/simulation/reviewed")
    public Map<String, Object> markSimulationReviewed(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.markSimulationReviewed(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/build-draft-policy")
    public Map<String, Object> buildDraftPolicy(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.buildDraftPackage(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/draft-diff")
    public Map<String, Object> draftDiff(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.compareDraftVersions(documentId, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/approvals/invalidate-demo")
    public Map<String, Object> invalidateApprovalDemo(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.invalidateAfterMaterialEdit(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/test-cases")
    public Map<String, Object> listTestCases(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.listTests(documentId, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/test-cases/{testId}/review")
    public Map<String, Object> reviewTestCase(
            @PathVariable UUID documentId,
            @PathVariable UUID testId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.reviewTest(
                documentId, testId, body == null ? Map.of() : body, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/export/rules.csv")
    public ResponseEntity<byte[]> exportRulesCsv(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        byte[] csv = prospectApprovalService.exportRulesCsv(documentId, tenantHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"policy-rules-" + documentId + ".csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @GetMapping("/policy-studio/documents/{documentId}/export/summary.html")
    public ResponseEntity<byte[]> exportSummaryHtml(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        byte[] html = prospectApprovalService.exportPrintableHtml(documentId, tenantHeader)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"policy-draft-summary.html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping("/policy-studio/demo/happy-path")
    public Map<String, Object> demoHappyPath(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.runDemoHappyPath(tenantHeader);
    }

    @PostMapping("/policy-studio/demo/blocked-path")
    public Map<String, Object> demoBlockedPath(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return prospectApprovalService.runDemoBlockedPath(tenantHeader);
    }

    // ─── Day 6.2 Policy lifecycle / applicability (shadow only) ────

    @GetMapping("/policy-studio/lifecycle/status-mapping")
    public Map<String, Object> lifecycleStatusMapping(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.lifecycleStatusMapping();
    }

    @GetMapping("/policy-studio/documents/{documentId}/lifecycle")
    public Map<String, Object> lifecycleSettings(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.lifecycleSettings(documentId, tenantHeader);
    }

    @GetMapping("/policy-studio/documents/{documentId}/lifecycle/history")
    public Map<String, Object> lifecycleHistory(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.lifecycleHistory(documentId, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/save-draft")
    public Map<String, Object> lifecycleSaveDraft(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.saveLifecycleDraft(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/submit-review")
    public Map<String, Object> lifecycleSubmitReview(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.submitLifecycleReview(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/approve")
    public Map<String, Object> lifecycleApprove(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.approveLifecyclePolicy(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/schedule")
    public Map<String, Object> lifecycleSchedule(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.scheduleLifecyclePolicy(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/retire")
    public Map<String, Object> lifecycleRetire(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.retireLifecyclePolicy(
                documentId, mergeActor(body, userId, userRole, userName), tenantHeader);
    }

    /** POLICY-STUDIO-UX-CLOSURE-1 — hard-delete never-activated DRAFT (backend-authorised). */
    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/delete-draft")
    public Map<String, Object> lifecycleDeleteDraft(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.deleteDraftPolicy(
                documentId, mergeActor(body, userId, userRole, userName), tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/stamp-active-for-versioning")
    public Map<String, Object> lifecycleStampActiveForVersioning(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.stampActiveForVersioning(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/documents/{documentId}/lifecycle/new-version")
    public Map<String, Object> lifecycleNewVersion(
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.createLifecycleVersion(
                documentId, body == null ? Map.of() : body, tenantHeader);
    }

    @PostMapping("/policy-studio/lifecycle/resolve-shadow")
    public Map<String, Object> resolveShadowApplication(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.resolveShadowApplication(body, tenantHeader);
    }

    @GetMapping("/policy-catalogue")
    public Map<String, Object> policyCatalogue(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.list();
    }

    @PostMapping("/policy-catalogue")
    public Map<String, Object> upsertPolicyCatalogue(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.upsert(body == null ? Map.of() : body);
    }

    @GetMapping("/policy-catalogue/{applicabilityId}")
    public Map<String, Object> policyCatalogueEntry(
            @PathVariable UUID applicabilityId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.get(applicabilityId);
    }

    @PostMapping("/policy-catalogue/{applicabilityId}/schedule")
    public Map<String, Object> scheduleCatalogueEntry(
            @PathVariable UUID applicabilityId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.schedule(applicabilityId, body == null ? Map.of() : body);
    }

    @PostMapping("/policy-catalogue/{applicabilityId}/retire")
    public Map<String, Object> retireCatalogueEntry(
            @PathVariable UUID applicabilityId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.retire(applicabilityId, body == null ? Map.of() : body);
    }

    @PostMapping("/policy-catalogue/{applicabilityId}/link-immutable")
    public Map<String, Object> linkImmutableCatalogue(
            @PathVariable UUID applicabilityId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.linkImmutable(applicabilityId, body == null ? Map.of() : body);
    }

    @PostMapping("/policy-catalogue/reclassify")
    public Map<String, Object> reclassifyCatalogue(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.reclassifyCatalogue();
    }

    @PostMapping("/p2-validation/run")
    public Map<String, Object> runP2Validation(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.runP2Validation(body == null ? Map.of() : body);
    }

    @GetMapping("/p2-validation/dashboard")
    public Map<String, Object> p2ValidationDashboard(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.p2Dashboard();
    }

    @GetMapping("/policy-catalogue/discovery")
    public Map<String, Object> shadowDiscovery(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.discover();
    }

    @GetMapping("/applications/{applicationId}/applicable-policy")
    public Map<String, Object> applicablePolicy(
            @PathVariable UUID applicationId,
            @RequestParam(required = false) String evaluationDate,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.applicablePolicy(applicationId, evaluationDate);
    }

    @PostMapping("/applications/{applicationId}/shadow-route")
    public Map<String, Object> shadowRouteApplication(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyCatalogueFacade.shadowRoute(applicationId, body == null ? Map.of() : body);
    }

    /** KYC-5 — evaluate Decision Policy KYC rules in shadow for an application. */
    @PostMapping("/applications/{applicationId}/kyc-shadow/evaluate")
    public Map<String, Object> evaluateKycShadow(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        boolean persist = body == null || !Boolean.FALSE.equals(body.get("persist"));
        Map<String, Object> out = shadowKycFacade.evaluateForApplication(applicationId, null, persist);
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("kyc5", true);
        return out;
    }

    @GetMapping("/applications/{applicationId}/kyc-shadow/latest")
    public Map<String, Object> latestKycShadow(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>(shadowKycFacade.latestForApplication(applicationId));
        StagingDemoWorkspaceService.stampSafety(out);
        return out;
    }

    @PostMapping("/applications/{applicationId}/kyc-shadow/replay")
    public Map<String, Object> replayKycShadow(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>(shadowKycFacade.replayLatest(applicationId));
        StagingDemoWorkspaceService.stampSafety(out);
        return out;
    }

    /** KYC-5 fixture matrix — validation only, not real-data certification. */
    @GetMapping("/kyc-shadow/fixture-matrix")
    public Map<String, Object> kycShadowFixtureMatrix(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("demoLabel", "VALIDATION FIXTURE — NOT REAL BORROWER DATA");
        out.put("certificationStatus", "SHADOW_EVALUATION_READY");
        out.put("productionReady", false);
        out.put("cases", com.los.core.creditintelligence.decisionpolicy.kyc.shadow.Kyc5FixtureMatrix.run(
                shadowKycEvaluationService));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /** KYC-7 — end-to-end Decision Policy simulation fixture matrix (shadow only). */
    @GetMapping("/decision-policy-e2e/fixture-matrix")
    public Map<String, Object> decisionPolicyE2eFixtureMatrix(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = decisionPolicyE2eSimulationService.runFixtureMatrix(
                properties.getDefaultTenantId());
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("kyc7", true);
        return out;
    }

    @PostMapping("/decision-policy-e2e/simulate")
    public Map<String, Object> decisionPolicyE2eSimulate(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = decisionPolicyE2eSimulationService.runFixtureMatrix(
                properties.getDefaultTenantId());
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("kyc7", true);
        out.put("requestEcho", body == null ? Map.of() : body);
        return out;
    }

    @GetMapping("/decision-policy-e2e/version-transition")
    public Map<String, Object> decisionPolicyE2eVersionTransition(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = decisionPolicyE2eSimulationService.versionTransitionDemo(
                properties.getDefaultTenantId());
        StagingDemoWorkspaceService.stampSafety(out);
        return out;
    }

    /** DP-V1 — real/stored Decision Policy corpus discovery (shadow only). */
    @GetMapping("/decision-policy-corpus/discovery")
    public Map<String, Object> decisionPolicyCorpusDiscovery(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return decisionPolicyRealCorpusValidationService.discover();
    }

    @GetMapping("/decision-policy-corpus/export-spec")
    public Map<String, Object> decisionPolicyCorpusExportSpec(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        Map<String, Object> out = new LinkedHashMap<>(
                com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyCorpusExportSpec.fullSpec());
        StagingDemoWorkspaceService.stampSafety(out);
        return out;
    }

    @PostMapping("/decision-policy-corpus/import")
    public Map<String, Object> decisionPolicyCorpusImport(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return decisionPolicyRealCorpusValidationService.importCorpus(
                body == null ? Map.of() : body, "staging-demo");
    }

    @PostMapping("/decision-policy-corpus/validate")
    public Map<String, Object> decisionPolicyCorpusValidate(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return decisionPolicyRealCorpusValidationService.runValidation("staging-demo");
    }

    @GetMapping("/decision-policy-corpus/dashboard")
    public Map<String, Object> decisionPolicyCorpusDashboard(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return decisionPolicyRealCorpusValidationService.dashboard();
    }

    /** Stamp JWT/header actor into lifecycle body — do not trust client-only role hiding. */
    private static Map<String, Object> mergeActor(
            Map<String, Object> body, String userId, String userRole, String userName) {
        Map<String, Object> out = new LinkedHashMap<>(body == null ? Map.of() : body);
        if (userRole != null && !userRole.isBlank()) {
            out.putIfAbsent("reviewerRole", userRole.trim());
            out.putIfAbsent("actorRole", userRole.trim());
            out.putIfAbsent("role", userRole.trim());
        }
        String actor = (userName != null && !userName.isBlank()) ? userName.trim()
                : (userId != null && !userId.isBlank() ? userId.trim() : null);
        if (actor != null) {
            out.putIfAbsent("reviewer", actor);
            out.putIfAbsent("actor", actor);
        }
        return out;
    }

    private void assertStagingDemoEnabled() {
        boolean staging = properties.getStagingDemo() != null && properties.getStagingDemo().isEnabled();
        boolean validation = properties.getValidation() != null && properties.getValidation().isEnabled();
        if (!staging && !validation) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Staging demo disabled (enable credit-intelligence.staging-demo or validation)");
        }
        if (properties.getCutover() != null && properties.getCutover().isAllowCanonicalAuthority()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "allow-canonical-authority must remain false for staging demo");
        }
    }

    private void assertInternalToken(String token) {
        // Delegates to shared fail-closed/soft-open rules (filter also enforces).
        // Soft-open only when credit-intelligence.internal-token-required=false and token blank.
        if (internalToken == null || internalToken.isBlank()) {
            if (properties.isInternalTokenRequired()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal token not configured");
            }
            log.warn("credit-intelligence.internal-token blank — allowing staging-demo API (token not required)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
