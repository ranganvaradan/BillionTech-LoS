package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiDecisionCertification;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DecisionCertificationService {

    private final CutoverStore store;

    public DecisionCertificationService(CutoverStore store) {
        this.store = store;
    }

    public CiDecisionCertification certify(
            UUID tenantId,
            UUID decisionStrategyId,
            String certifiedBy,
            Map<String, Boolean> checklist,
            List<String> evidenceRefs) {
        Map<String, Object> checks = new LinkedHashMap<>();
        Map<String, Boolean> c = checklist == null ? Map.of() : checklist;
        checks.put("limitMethods", c.getOrDefault("limitMethods", false));
        checks.put("pricing", c.getOrDefault("pricing", false));
        checks.put("tenure", c.getOrDefault("tenure", false));
        checks.put("collateral", c.getOrDefault("collateral", false));
        checks.put("conditions", c.getOrDefault("conditions", false));
        checks.put("authorityMatrix", c.getOrDefault("authorityMatrix", false));
        // Hybrid: dimensions intentionally left LEGACY may mark hybridLegacyOk=true
        checks.put("hybridLegacyOk", c.getOrDefault("hybridLegacyOk", false));

        boolean hybrid = Boolean.TRUE.equals(checks.get("hybridLegacyOk"));
        boolean core = Boolean.TRUE.equals(checks.get("limitMethods"))
                || hybrid;
        boolean all = hybrid || checks.entrySet().stream()
                .filter(e -> !"hybridLegacyOk".equals(e.getKey()))
                .allMatch(e -> Boolean.TRUE.equals(e.getValue()));

        CiDecisionCertification cert = CiDecisionCertification.builder()
                .tenantId(tenantId)
                .decisionStrategyId(decisionStrategyId)
                .status(all && core ? "CERTIFIED" : "PENDING")
                .certifiedBy(all && core ? certifiedBy : null)
                .certifiedAt(all && core ? Instant.now() : null)
                .evidenceRefs(evidenceRefs == null ? List.of() : new ArrayList<>(evidenceRefs))
                .checklist(checks)
                .createdAt(Instant.now())
                .build();
        return store.saveDecisionCert(cert);
    }

    public boolean isCertified(UUID tenantId, UUID decisionStrategyId) {
        return store.listDecisionCerts(tenantId).stream()
                .anyMatch(c -> decisionStrategyId.equals(c.getDecisionStrategyId())
                        && "CERTIFIED".equals(c.getStatus()));
    }
}
