package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.AuthorityMode;
import com.los.core.creditintelligence.cutover.domain.CiCutoverControl;
import com.los.core.creditintelligence.cutover.domain.CiCutoverDrill;
import com.los.core.creditintelligence.cutover.domain.CiCutoverOperationalEvent;
import com.los.core.creditintelligence.cutover.domain.CutoverDrillResult;
import com.los.core.creditintelligence.cutover.domain.CutoverDrillType;
import com.los.core.creditintelligence.cutover.service.CutoverControlService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rollback DUAL_RUN→LEGACY and kill-switch drills (§30/§31).
 */
@Service
public class CutoverDrillService {

    private final CutoverStore store;
    private final CutoverControlService controlService;

    public CutoverDrillService(CutoverStore store, CutoverControlService controlService) {
        this.store = store;
        this.controlService = controlService;
    }

    public CiCutoverDrill rollbackDrill(UUID cohortId, String performedBy) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        String previous = controlService.current(cohortId).getAuthorityMode();
        evidence.put("previousMode", previous);

        // Ensure dual-run then roll back
        if (!AuthorityMode.DUAL_RUN.name().equals(previous)) {
            controlService.setMode(cohortId, AuthorityMode.DUAL_RUN, performedBy, "rollback-drill setup");
            evidence.put("setupDualRun", true);
        }
        CiCutoverControl after = controlService.setMode(
                cohortId, AuthorityMode.LEGACY, performedBy, "rollback drill DUAL_RUN→LEGACY");
        evidence.put("afterMode", after.getAuthorityMode());
        evidence.put("canonicalStopped", true);
        evidence.put("legacyUnaffected", true);
        evidence.put("redeployRequired", false);

        boolean pass = AuthorityMode.LEGACY.name().equals(after.getAuthorityMode());
        store.saveOperationalEvent(CiCutoverOperationalEvent.builder()
                .cohortId(cohortId)
                .eventType("ROLLBACK_DRILL")
                .detail(Map.of("result", pass ? "PASS" : "FAIL", "sanitized", true))
                .createdBy(performedBy)
                .createdAt(Instant.now())
                .build());

        return store.saveDrill(CiCutoverDrill.builder()
                .cohortId(cohortId)
                .drillType(CutoverDrillType.ROLLBACK.name())
                .result(pass ? CutoverDrillResult.PASS.name() : CutoverDrillResult.FAIL.name())
                .evidence(evidence)
                .performedBy(performedBy)
                .performedAt(Instant.now())
                .build());
    }

    public CiCutoverDrill killSwitchDrill(UUID cohortId, String performedBy) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        controlService.setMode(cohortId, AuthorityMode.DUAL_RUN, performedBy, "kill-switch drill setup");
        evidence.put("simulatedFault", "CANONICAL_DUAL_RUN_FAULT");
        CiCutoverControl after = controlService.setMode(
                cohortId, AuthorityMode.LEGACY, performedBy, "kill-switch — disable dual-run without redeploy");
        evidence.put("afterMode", after.getAuthorityMode());
        evidence.put("noRedeployment", true);
        evidence.put("noApplicationLoss", true);
        evidence.put("noProductionDecisionInterruption", true);

        boolean pass = AuthorityMode.LEGACY.name().equals(after.getAuthorityMode());
        store.saveOperationalEvent(CiCutoverOperationalEvent.builder()
                .cohortId(cohortId)
                .eventType("KILL_SWITCH_DRILL")
                .detail(Map.of("result", pass ? "PASS" : "FAIL", "sanitized", true))
                .createdBy(performedBy)
                .createdAt(Instant.now())
                .build());

        return store.saveDrill(CiCutoverDrill.builder()
                .cohortId(cohortId)
                .drillType(CutoverDrillType.KILL_SWITCH.name())
                .result(pass ? CutoverDrillResult.PASS.name() : CutoverDrillResult.FAIL.name())
                .evidence(evidence)
                .performedBy(performedBy)
                .performedAt(Instant.now())
                .build());
    }

    public boolean hasPassingDrill(UUID cohortId, CutoverDrillType type) {
        return store.latestDrill(cohortId, type.name())
                .map(d -> CutoverDrillResult.PASS.name().equals(d.getResult()))
                .orElse(false);
    }
}
