package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.model.ValidationRunResult;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic replay demo — evaluate twice via harness freeze / evaluation hashes.
 */
@Service
public class StagingReplayDemoService {

    private final CreditIntelligenceProperties properties;
    private final MultiSourceValidationHarness harness;

    public StagingReplayDemoService(
            CreditIntelligenceProperties properties,
            MultiSourceValidationHarness harness) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.harness = harness;
    }

    public Map<String, Object> replay(String caseCode) {
        StagingCaseCatalog.CaseMeta meta = StagingCaseCatalog.require(caseCode);
        UUID tenantId = properties.getDefaultTenantId();

        ValidationRunResult first = harness.runCase(meta.enumCode(), tenantId, UUID.randomUUID());
        ValidationRunResult second = harness.runCase(meta.enumCode(), tenantId, UUID.randomUUID());

        String originalHash = first.deterministicEvaluationHash();
        String replayHash = second.deterministicEvaluationHash();
        // Also expose harness intra-run replay identity from the first run
        boolean intraRunMatch = first.replayIdentical();
        boolean crossRunMatch = Objects.equals(originalHash, replayHash);

        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("caseCode", meta.caseCode());
        out.put("enumCode", meta.enumCode().name());
        out.put("originalHash", originalHash);
        out.put("replayHash", replayHash);
        out.put("match", crossRunMatch);
        out.put("intraRunReplayMatch", intraRunMatch);
        out.put("intraRunOriginalHash", first.deterministicEvaluationHash());
        out.put("intraRunReplayHash", first.replayHash());
        out.put("configFreezeHash", first.configFreezeHash());
        out.put("note", "VALIDATION FIXTURE replay — hashes must match for deterministic evaluation");
        return out;
    }
}
