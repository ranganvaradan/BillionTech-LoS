package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverControl;
import com.los.core.creditintelligence.cutover.domain.CohortStatus;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kill switch LEGACY ↔ DUAL_RUN only. Rejects CANONICAL in G0. Audits every change.
 */
@Service
public class CutoverControlService {

    private final CutoverStore store;
    private final CutoverCohortService cohortService;
    private final CreditIntelligenceProperties properties;
    private final CutoverObservability observability;
    /** Ensures strictly increasing effectiveAt when Instant.now() collides in tests. */
    private final AtomicLong effectiveAtSeq = new AtomicLong();

    public CutoverControlService(
            CutoverStore store,
            CutoverCohortService cohortService,
            CreditIntelligenceProperties properties,
            CutoverObservability observability) {
        this.store = store;
        this.cohortService = cohortService;
        this.properties = properties;
        this.observability = observability;
    }

    public CiCutoverControl setMode(
            UUID cohortId, AuthorityMode mode, String changedBy, String reason) {
        if (mode == AuthorityMode.CANONICAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "G0 rejects CANONICAL authority activation");
        }
        if (properties.getCutover() != null && properties.getCutover().isAllowCanonicalAuthority()) {
            // Still refuse in G0 service layer even if misconfigured
            if (mode == AuthorityMode.CANONICAL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "G0 rejects CANONICAL even if allow-canonical-authority is true");
            }
        }
        cohortService.get(cohortId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("previous", store.latestControl(cohortId)
                .map(CiCutoverControl::getAuthorityMode).orElse(AuthorityMode.LEGACY.name()));
        audit.put("g0", true);
        audit.put("allowCanonical", false);
        audit.put("at", Instant.now().toString());

        Instant effective = Instant.now().plusMillis(1 + effectiveAtSeq.incrementAndGet());
        CiCutoverControl control = CiCutoverControl.builder()
                .cohortId(cohortId)
                .authorityMode(mode.name())
                .effectiveAt(effective)
                .changedBy(changedBy)
                .reason(reason)
                .audit(audit)
                .createdAt(effective)
                .build();
        store.saveControl(control);

        if (mode == AuthorityMode.DUAL_RUN) {
            cohortService.updateStatus(cohortId, CohortStatus.DUAL_RUN);
        } else if (mode == AuthorityMode.LEGACY) {
            // rollback path — keep cohort but mark rolled back if leaving dual-run
            String prev = String.valueOf(audit.get("previous"));
            if (AuthorityMode.DUAL_RUN.name().equals(prev)) {
                cohortService.updateStatus(cohortId, CohortStatus.ROLLED_BACK);
            }
        }
        observability.inc("control_mode_change");
        return control;
    }

    public CiCutoverControl current(UUID cohortId) {
        return store.latestControl(cohortId).orElseGet(() ->
                CiCutoverControl.builder()
                        .cohortId(cohortId)
                        .authorityMode(AuthorityMode.LEGACY.name())
                        .effectiveAt(Instant.now())
                        .reason("implicit default")
                        .audit(Map.of("implicit", true))
                        .build());
    }

    public List<CiCutoverControl> history(UUID cohortId) {
        return store.listControls(cohortId);
    }
}
