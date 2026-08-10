package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiPolicyCertification;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyCertificationService {

    private final CutoverStore store;

    public PolicyCertificationService(CutoverStore store) {
        this.store = store;
    }

    public CiPolicyCertification certify(
            UUID tenantId,
            UUID policyPackageId,
            String certifiedBy,
            Map<String, Boolean> checklist,
            List<String> evidenceRefs) {
        Map<String, Object> checks = new LinkedHashMap<>();
        Map<String, Boolean> c = checklist == null ? Map.of() : checklist;
        checks.put("studioApproval", c.getOrDefault("studioApproval", false));
        checks.put("makerChecker", c.getOrDefault("makerChecker", false));
        checks.put("hardRuleTestsPass", c.getOrDefault("hardRuleTestsPass", false));
        checks.put("historicalReplayComplete", c.getOrDefault("historicalReplayComplete", false));
        checks.put("dualRunReviewed", c.getOrDefault("dualRunReviewed", false));
        checks.put("criticalBindingsCertified", c.getOrDefault("criticalBindingsCertified", false));
        checks.put("noBlockingAmbiguity", c.getOrDefault("noBlockingAmbiguity", false));

        boolean all = checks.values().stream().allMatch(v -> Boolean.TRUE.equals(v));
        CiPolicyCertification cert = CiPolicyCertification.builder()
                .tenantId(tenantId)
                .policyPackageId(policyPackageId)
                .status(all ? "CERTIFIED" : "PENDING")
                .certifiedBy(all ? certifiedBy : null)
                .certifiedAt(all ? Instant.now() : null)
                .evidenceRefs(evidenceRefs == null ? List.of() : new ArrayList<>(evidenceRefs))
                .checklist(checks)
                .createdAt(Instant.now())
                .build();
        return store.savePolicyCert(cert);
    }

    public boolean isCertified(UUID tenantId, UUID policyPackageId) {
        return store.listPolicyCerts(tenantId).stream()
                .anyMatch(c -> policyPackageId.equals(c.getPolicyPackageId())
                        && "CERTIFIED".equals(c.getStatus()));
    }
}
