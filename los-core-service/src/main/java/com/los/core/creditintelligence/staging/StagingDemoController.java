package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
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
    private final StagingProspectApprovalService prospectApprovalService;
    private final PolicyCatalogueFacade policyCatalogueFacade;
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
        return policyStudioDemoService.landing();
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy session not found");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy session not found");
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
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        assertStagingDemoEnabled();
        return policyStudioDemoService.retireLifecyclePolicy(
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
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token blank — allowing staging-demo API (local/staging)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
