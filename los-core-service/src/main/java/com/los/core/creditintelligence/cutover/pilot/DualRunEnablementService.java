package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverException;
import com.los.core.creditintelligence.cutover.domain.LimitedPilotCertificationStatus;
import com.los.core.creditintelligence.cutover.service.CutoverControlService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enable DUAL_RUN only if certification LIMITED_PILOT_READY or READY_WITH_EXCEPTIONS with approved exception (§27).
 * Rejects CANONICAL always.
 */
@Service
public class DualRunEnablementService {

    public static final String SAMPLE_SIZE_EXCEPTION = "SAMPLE_SIZE_BELOW_MINIMUM";

    private final CutoverStore store;
    private final CutoverControlService controlService;

    public DualRunEnablementService(CutoverStore store, CutoverControlService controlService) {
        this.store = store;
        this.controlService = controlService;
    }

    public Map<String, Object> enableDualRun(UUID cohortId, String changedBy, String reason) {
        var cert = store.latestPilotCertification(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No certification record — run certification first"));

        String status = cert.getStatus();
        boolean allowed = LimitedPilotCertificationStatus.LIMITED_PILOT_READY.name().equals(status)
                || (LimitedPilotCertificationStatus.READY_WITH_EXCEPTIONS.name().equals(status)
                && hasApprovedSampleSizeException(cohortId));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "enable-dual-run requires LIMITED_PILOT_READY or READY_WITH_EXCEPTIONS with approved exception; "
                            + "status=" + status);
        }

        var control = controlService.setMode(cohortId, AuthorityMode.DUAL_RUN, changedBy,
                reason == null ? "G0.1 enable dual-run" : reason);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("control", control);
        out.put("certificationStatus", status);
        out.put("productionAuthority", "LEGACY");
        out.put("canonicalAuthority", false);
        return out;
    }

    public CiCutoverException grantSampleSizeException(
            UUID cohortId, String risk, String approver, String mitigation, Instant expiry) {
        return store.saveException(CiCutoverException.builder()
                .cohortId(cohortId)
                .exceptionCode(SAMPLE_SIZE_EXCEPTION)
                .risk(risk)
                .approver(approver)
                .mitigation(mitigation)
                .expiry(expiry)
                .createdAt(Instant.now())
                .build());
    }

    public boolean hasApprovedSampleSizeException(UUID cohortId) {
        Instant now = Instant.now();
        List<CiCutoverException> exceptions = store.listExceptions(cohortId);
        return exceptions.stream().anyMatch(e ->
                SAMPLE_SIZE_EXCEPTION.equals(e.getExceptionCode())
                        && e.getApprover() != null && !e.getApprover().isBlank()
                        && (e.getExpiry() == null || e.getExpiry().isAfter(now)));
    }
}
