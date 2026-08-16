package com.los.core.creditintelligence.policystudio.api;

import com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType;
import com.los.core.creditintelligence.policystudio.certification.CertificationEvidenceRequirements;
import com.los.core.creditintelligence.policystudio.certification.CertificationScopeType;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wave-8 controlled admin API for production certification.
 * No lender self-certify without token. No broad UI.
 */
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/production-certification")
@RequiredArgsConstructor
public class ProductionCertificationAdminController {

    private final ProductionCertificationService certificationService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/status")
    public Map<String, Object> status(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam String artifactType,
            @RequestParam String artifactId,
            @RequestParam String artifactVersion,
            @RequestParam(defaultValue = "PLATFORM") String scopeType,
            @RequestParam(required = false) String scopeId) {
        assertToken(token);
        CertifiableArtifactType type = CertifiableArtifactType.valueOf(artifactType);
        CertificationScopeType scope = CertificationScopeType.valueOf(scopeType);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", certificationService.getCertificationStatus(
                type, artifactId, artifactVersion, scope, scopeId).name());
        out.put("certifiedForLiveUse", certificationService.isCertifiedForLiveUse(
                type, artifactId, artifactVersion, scope, scopeId));
        out.putAll(certificationService.projectionFor(type, artifactId, artifactVersion, scope, scopeId));
        out.put("evidenceRequirements", CertificationEvidenceRequirements.forType(type));
        return out;
    }

    @GetMapping("/evidence")
    public Map<String, Object> evidence(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam String artifactType,
            @RequestParam String artifactId,
            @RequestParam String artifactVersion,
            @RequestParam(defaultValue = "PLATFORM") String scopeType,
            @RequestParam(required = false) String scopeId) {
        assertToken(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("history", certificationService.listEvidence(
                CertifiableArtifactType.valueOf(artifactType), artifactId, artifactVersion,
                CertificationScopeType.valueOf(scopeType), scopeId));
        return out;
    }

    @GetMapping("/counts")
    public Map<String, Object> counts(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        return certificationService.counts();
    }

    @PostMapping("/certify")
    public Map<String, Object> certify(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertToken(token);
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = body.get("evidenceJson") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        var rec = certificationService.certify(
                CertifiableArtifactType.valueOf(String.valueOf(body.get("artifactType"))),
                String.valueOf(body.get("artifactId")),
                String.valueOf(body.get("artifactVersion")),
                CertificationScopeType.valueOf(String.valueOf(
                        body.getOrDefault("scopeType", "PLATFORM"))),
                body.get("scopeId") == null ? null : String.valueOf(body.get("scopeId")),
                String.valueOf(body.get("certifiedBy")),
                body.get("evidenceSummary") == null ? null : String.valueOf(body.get("evidenceSummary")),
                evidence,
                body.get("semanticCatalogueVersion") == null ? null
                        : String.valueOf(body.get("semanticCatalogueVersion")),
                body.get("engineVersion") == null ? null : String.valueOf(body.get("engineVersion")),
                body.get("producerVersion") == null ? null : String.valueOf(body.get("producerVersion")));
        return rec.toMap();
    }

    @PostMapping("/revoke")
    public Map<String, Object> revoke(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        assertToken(token);
        var rec = certificationService.revoke(
                CertifiableArtifactType.valueOf(String.valueOf(body.get("artifactType"))),
                String.valueOf(body.get("artifactId")),
                String.valueOf(body.get("artifactVersion")),
                CertificationScopeType.valueOf(String.valueOf(
                        body.getOrDefault("scopeType", "PLATFORM"))),
                body.get("scopeId") == null ? null : String.valueOf(body.get("scopeId")),
                String.valueOf(body.get("revokedBy")),
                body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return rec.toMap();
    }

    private void assertToken(String token) {
        if (internalToken != null && !internalToken.isBlank()
                && (token == null || !internalToken.equals(token))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
