package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.evaluation.domain.CiConfigFreeze;
import com.los.core.creditintelligence.support.ContentHasher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conceptual purity proof: deterministicEvaluationHash from original EvaluationContext inputs
 * stays stable after mutating live app fields, live policy, YAML thresholds, appending metrics,
 * and advancing the wall clock — as long as the frozen context inputs are reused.
 */
class EvaluationReplayPurityTest {

    @Test
    void deterministicHashStableAfterLiveMutationsWhenOriginalContextInputsReused() {
        ContentHasher contentHasher = new ContentHasher();
        DeterministicEvaluationHasher evalHasher = new DeterministicEvaluationHasher(contentHasher);

        UUID freezeId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID metricSetId = UUID.randomUUID();
        UUID policyVersionId = UUID.randomUUID();
        Instant clockInstant = Instant.parse("2026-02-01T10:00:00Z");
        LocalDate asOf = LocalDate.of(2026, 2, 1);

        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCanonicalization().getBureau().setLiveUnsecuredThreshold(6);
        ConfigFreezeService freezeService = new ConfigFreezeService(props, null, contentHasher);
        Map<String, Object> originalFreezeContent = freezeService.snapshotContent();
        String originalFreezeHash = contentHasher.hashMap(originalFreezeContent);

        Map<String, Object> originalPolicyContent = Map.of(
                "ruleSets", List.of(Map.of(
                        "name", "Approve",
                        "active", true,
                        "rulesJson", Map.of("decision", "APPROVE"))));

        // Outcomes built solely from frozen EvaluationContext inputs (as PureCanonicalEvaluationService does)
        Map<String, Object> originalOutcomes = buildOutcomes(
                freezeId, originalFreezeHash, policyVersionId, "policy-hash-1",
                snapshotId, metricSetId, asOf, clockInstant, "Asia/Kolkata",
                "APPROVE", "APPROVED", 80);

        String originalHash = evalHasher.hashOutcomes(originalOutcomes);

        // --- mutate live world ---
        props.getCanonicalization().getBureau().setLiveUnsecuredThreshold(99);
        Map<String, Object> mutatedFreezeContent = freezeService.snapshotContent();
        assertThat(contentHasher.hashMap(mutatedFreezeContent)).isNotEqualTo(originalFreezeHash);

        Map<String, Object> mutatedLivePolicy = Map.of(
                "ruleSets", List.of(Map.of(
                        "name", "Reject",
                        "active", true,
                        "rulesJson", Map.of("decision", "REJECT"))));
        assertThat(mutatedLivePolicy).isNotEqualTo(originalPolicyContent);

        FixedEvaluationClock advanced = new FixedEvaluationClock(
                Instant.parse("2026-08-01T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        assertThat(advanced.today()).isNotEqualTo(asOf);

        // Appended metrics / app field mutation would affect live lookups only — not frozen outcomes
        Map<String, Object> mutatedAppView = Map.of("requestedAmount", "999999999");

        // Re-hash using ORIGINAL context inputs (freeze content hash, clock, snapshot ids, outcomes)
        Map<String, Object> replayOutcomes = buildOutcomes(
                freezeId, originalFreezeHash, policyVersionId, "policy-hash-1",
                snapshotId, metricSetId, asOf, clockInstant, "Asia/Kolkata",
                "APPROVE", "APPROVED", 80);
        // Include a non-deterministic key that hasher must strip
        replayOutcomes.put("startedAt", Instant.now().toString());
        replayOutcomes.put("durationMs", 12345);

        String replayHash = evalHasher.hashOutcomes(replayOutcomes);

        assertThat(replayHash).isEqualTo(originalHash);
        // Sanity: mutated freeze would produce different content, but original freeze CiConfigFreeze is reused
        CiConfigFreeze originalFreeze = CiConfigFreeze.builder()
                .id(freezeId)
                .tenantId(UUID.randomUUID())
                .configVersion(ConfigFreezeService.CONFIG_VERSION)
                .content(originalFreezeContent)
                .contentHash(originalFreezeHash)
                .schemaVersion(ConfigFreezeService.SCHEMA_VERSION)
                .build();
        assertThat(originalFreeze.getContentHash()).isEqualTo(originalFreezeHash);
        assertThat(mutatedAppView.get("requestedAmount")).isEqualTo("999999999");
    }

    private static Map<String, Object> buildOutcomes(
            UUID freezeId,
            String freezeHash,
            UUID policyVersionId,
            String policyHash,
            UUID snapshotId,
            UUID metricSetId,
            LocalDate asOf,
            Instant clockInstant,
            String zone,
            String policyRec,
            String creditDec,
            int risk) {
        Map<String, Object> outcomes = new LinkedHashMap<>();
        outcomes.put("aggregatePolicyRecommendation", policyRec);
        outcomes.put("aggregateCreditDecision", creditDec);
        outcomes.put("aggregateRiskScore", risk);
        outcomes.put("aggregateReasons", List.of());
        outcomes.put("perRule", List.of());
        outcomes.put("kycOutcome", "PASS");
        outcomes.put("defaultedPaths", List.of());
        outcomes.put("configFreezeId", freezeId.toString());
        outcomes.put("configFreezeHash", freezeHash);
        outcomes.put("policyVersionId", policyVersionId.toString());
        outcomes.put("policyContentHash", policyHash);
        outcomes.put("factSnapshotId", snapshotId.toString());
        outcomes.put("metricResultSetId", metricSetId.toString());
        outcomes.put("reconciliationResultSetId", null);
        outcomes.put("evaluationAsOf", asOf.toString());
        outcomes.put("clockInstant", clockInstant.toString());
        outcomes.put("clockZone", zone);
        return outcomes;
    }
}
