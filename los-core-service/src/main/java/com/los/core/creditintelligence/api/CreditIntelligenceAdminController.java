package com.los.core.creditintelligence.api;

import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.CiEvaluationStage;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.repository.CiEvaluationStageRepository;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.repository.CiStandardRuleResultRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.CreditIntelligenceValidationReportService;
import com.los.core.creditintelligence.service.ShadowCreditEvaluationService;
import com.los.core.creditintelligence.service.SourceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal admin APIs for Credit Intelligence Phase F.
 * <p>
 * Security: when {@code credit-intelligence.internal-token} is non-blank, requests must send
 * matching {@code X-Internal-Token}. When blank (local default), requests are allowed with a warn log.
 * Optional {@code X-Tenant-Id} is checked against snapshot/evaluation tenant when present.
 * Do not return raw sensitive provider payloads.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence")
@RequiredArgsConstructor
public class CreditIntelligenceAdminController {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CiEvaluationStageRepository stageRepository;
    private final CiStandardRuleResultRepository ruleResultRepository;
    private final ShadowCreditEvaluationService shadowCreditEvaluationService;
    private final CreditIntelligenceValidationReportService validationReportService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/snapshots")
    public List<Map<String, Object>> listSnapshots(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return snapshotRepository.findByApplicationIdOrderBySnapshotVersionDesc(applicationId).stream()
                .filter(s -> tenantMatches(tenantHeader, s.getTenantId()))
                .map(this::snapshotSummary)
                .toList();
    }

    @GetMapping("/snapshots/{snapshotId}")
    public Map<String, Object> getSnapshot(
            @PathVariable UUID snapshotId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiFactSnapshot s = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        assertTenant(tenantHeader, s.getTenantId());
        return snapshotDetail(s);
    }

    @GetMapping("/snapshots/{snapshotId}/facts")
    public List<Map<String, Object>> getFacts(
            @PathVariable UUID snapshotId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiFactSnapshot s = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        assertTenant(tenantHeader, s.getTenantId());
        return factRepository.findBySnapshotIdOrderByCanonicalPathAsc(snapshotId).stream()
                .map(this::factSummary)
                .toList();
    }

    @GetMapping("/policy-versions/{id}")
    public Map<String, Object> getPolicyVersion(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        CiPolicyVersion v = policyVersionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy version not found"));
        return policySummary(v);
    }

    @GetMapping("/evaluations/{id}")
    public Map<String, Object> getEvaluation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiCreditEvaluation e = evaluationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));
        assertTenant(tenantHeader, e.getTenantId());
        return evaluationSummary(e);
    }

    @GetMapping("/evaluations/{id}/stages")
    public List<Map<String, Object>> getStages(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiCreditEvaluation e = evaluationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));
        assertTenant(tenantHeader, e.getTenantId());
        return stageRepository.findByEvaluationIdOrderBySequenceAsc(id).stream()
                .map(this::stageSummary)
                .toList();
    }

    @GetMapping("/evaluations/{id}/rule-results")
    public List<Map<String, Object>> getRuleResults(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiCreditEvaluation e = evaluationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation not found"));
        assertTenant(tenantHeader, e.getTenantId());
        return ruleResultRepository.findByEvaluationId(id).stream()
                .map(this::ruleResultSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/compare")
    public Map<String, Object> compare(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return validationReportService.buildReport(applicationId);
    }

    /** Spec §16 validation report — read-only; does not mutate application or workflow. */
    @GetMapping("/applications/{applicationId}/validation-report")
    public Map<String, Object> validationReport(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return validationReportService.buildReport(applicationId);
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        UUID snapshotId = parseUuid(body.get("snapshotId"));
        UUID policyVersionId = parseUuid(body.get("policyVersionId"));
        if (snapshotId == null || policyVersionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snapshotId and policyVersionId required");
        }
        CiFactSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        Optional<ShadowCreditEvaluationService.ShadowResult> result =
                shadowCreditEvaluationService.evaluate(
                        snapshotId, policyVersionId, snapshot.getApplicationId(), null, null);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "FAILED", "message", "Replay shadow evaluation failed"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "COMPLETED");
        out.put("evaluationId", result.get().shadow().getId().toString());
        out.put("overallOutcome", result.get().shadow().getOverallOutcome());
        out.put("authoritative", false);
        out.put("evaluationType", EvaluationType.SHADOW.name());
        return ResponseEntity.ok(out);
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing internal CI API without token (local only)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }

    private void assertTenant(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return;
        }
        if (!tenantMatches(tenantHeader, tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
        }
    }

    private static boolean tenantMatches(String tenantHeader, UUID tenantId) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return true;
        }
        try {
            return UUID.fromString(tenantHeader).equals(tenantId);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> snapshotSummary(CiFactSnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("applicationId", s.getApplicationId().toString());
        m.put("tenantId", s.getTenantId().toString());
        m.put("snapshotVersion", s.getSnapshotVersion());
        m.put("status", s.getStatus());
        m.put("factsHash", s.getFactsHash());
        m.put("sourceContextHash", s.getSourceContextHash());
        m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
        m.put("createdReason", s.getCreatedReason());
        return m;
    }

    private Map<String, Object> snapshotDetail(CiFactSnapshot s) {
        Map<String, Object> m = snapshotSummary(s);
        // metadata may contain applicationView / effectiveContextMap — strip nested sensitive keys
        m.put("metadata", SourceRegistryService.sanitizeMetadata(s.getMetadata()));
        m.put("schemaVersion", s.getSchemaVersion());
        return m;
    }

    private Map<String, Object> factSummary(CiUnderwritingFact f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId().toString());
        m.put("canonicalPath", f.getCanonicalPath());
        m.put("valueType", f.getValueType());
        m.put("value", f.getValue());
        m.put("classification", f.getClassification());
        m.put("qualityStatus", f.getQualityStatus());
        m.put("sourceRecordIds", f.getSourceRecordIds());
        m.put("metadata", f.getMetadata());
        return m;
    }

    private Map<String, Object> policySummary(CiPolicyVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId().toString());
        m.put("policyPackageId", v.getPolicyPackageId().toString());
        m.put("version", v.getVersion());
        m.put("status", v.getStatus());
        m.put("contentHash", v.getContentHash());
        m.put("orchestrationVersion", v.getOrchestrationVersion());
        m.put("publishedAt", v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
        m.put("policyContent", v.getPolicyContent());
        m.put("sourcePolicyReferences", v.getSourcePolicyReferences());
        return m;
    }

    private Map<String, Object> evaluationSummary(CiCreditEvaluation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("applicationId", e.getApplicationId().toString());
        m.put("factSnapshotId", e.getFactSnapshotId().toString());
        m.put("policyVersionId", e.getPolicyVersionId().toString());
        m.put("evaluationType", e.getEvaluationType());
        m.put("status", e.getStatus());
        m.put("authoritative", e.isAuthoritative());
        m.put("overallOutcome", e.getOverallOutcome());
        m.put("comparisonStatus", e.getComparisonStatus());
        m.put("productionEvaluationId",
                e.getProductionEvaluationId() != null ? e.getProductionEvaluationId().toString() : null);
        m.put("errorCode", e.getErrorCode());
        m.put("startedAt", e.getStartedAt() != null ? e.getStartedAt().toString() : null);
        m.put("completedAt", e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
        return m;
    }

    private Map<String, Object> stageSummary(CiEvaluationStage s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("stageCode", s.getStageCode());
        m.put("sequence", s.getSequence());
        m.put("status", s.getStatus());
        m.put("outcome", s.getOutcome());
        m.put("trace", s.getTrace());
        return m;
    }

    private Map<String, Object> ruleResultSummary(CiStandardRuleResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("ruleId", r.getRuleId());
        m.put("engineName", r.getEngineName());
        m.put("outcome", r.getOutcome());
        m.put("dataStatus", r.getDataStatus());
        m.put("factReferences", r.getFactReferences());
        m.put("explanation", r.getExplanation());
        m.put("trace", r.getTrace());
        return m;
    }

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
}
