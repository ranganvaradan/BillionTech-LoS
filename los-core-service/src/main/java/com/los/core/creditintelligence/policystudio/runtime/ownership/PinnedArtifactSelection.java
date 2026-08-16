package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wave-10 version-pinned artifact selection for target-live canonical orchestration.
 * Avoids mutable latest/current resolution on the authoritative target-live path.
 */
public record PinnedArtifactSelection(
        String policyId,
        String policyVersion,
        String scorecardId,
        String scorecardVersion,
        String gacatSemanticVersion,
        Map<String, String> calculationDefinitionVersions,
        Map<String, String> producerVersions,
        String sourceSnapshotVersion,
        LocalDate evaluationAsOf,
        List<String> certificationIds
) {
    public PinnedArtifactSelection {
        calculationDefinitionVersions = calculationDefinitionVersions == null
                ? Map.of() : Map.copyOf(calculationDefinitionVersions);
        producerVersions = producerVersions == null ? Map.of() : Map.copyOf(producerVersions);
        certificationIds = certificationIds == null ? List.of() : List.copyOf(certificationIds);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fail-fast: target-live requires explicit pins — no silent latest. */
    public void requireForTargetLive() {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("PinnedArtifactSelection.policyId required");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("PinnedArtifactSelection.policyVersion required — no latestFor");
        }
        if (evaluationAsOf == null) {
            throw new IllegalArgumentException("PinnedArtifactSelection.evaluationAsOf required — no wall-clock");
        }
        if (gacatSemanticVersion == null || gacatSemanticVersion.isBlank()) {
            throw new IllegalArgumentException("PinnedArtifactSelection.gacatSemanticVersion required");
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyId", policyId);
        m.put("policyVersion", policyVersion);
        m.put("scorecardId", scorecardId);
        m.put("scorecardVersion", scorecardVersion);
        m.put("gacatSemanticVersion", gacatSemanticVersion);
        m.put("calculationDefinitionVersions", calculationDefinitionVersions);
        m.put("producerVersions", producerVersions);
        m.put("sourceSnapshotVersion", sourceSnapshotVersion);
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        m.put("certificationIds", certificationIds);
        m.put("latestForForbidden", true);
        return m;
    }

    /**
     * Stamp pin map onto EvaluationContext entities for AuthoredDerivedProducer / Wave-11 replay.
     */
    public Map<String, Object> entityPins() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("pinnedArtifacts", toMap());
        e.put("pinnedCalculationDefinitions", calculationDefinitionVersions);
        e.put("pinnedProducerVersions", producerVersions);
        e.put("sourceSnapshotVersion", sourceSnapshotVersion);
        e.put("gacatSemanticVersion", gacatSemanticVersion);
        e.put("forbidLatestFor", true);
        return e;
    }

    public static final class Builder {
        private String policyId;
        private String policyVersion;
        private String scorecardId;
        private String scorecardVersion;
        private String gacatSemanticVersion =
                com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy.SEMANTIC_VERSION;
        private Map<String, String> calculationDefinitionVersions = Map.of();
        private Map<String, String> producerVersions = Map.of();
        private String sourceSnapshotVersion;
        private LocalDate evaluationAsOf;
        private List<String> certificationIds = List.of();

        public Builder policyId(String v) { this.policyId = v; return this; }
        public Builder policyVersion(String v) { this.policyVersion = v; return this; }
        public Builder scorecardId(String v) { this.scorecardId = v; return this; }
        public Builder scorecardVersion(String v) { this.scorecardVersion = v; return this; }
        public Builder gacatSemanticVersion(String v) { this.gacatSemanticVersion = v; return this; }
        public Builder calculationDefinitionVersions(Map<String, String> v) {
            this.calculationDefinitionVersions = v; return this;
        }
        public Builder producerVersions(Map<String, String> v) { this.producerVersions = v; return this; }
        public Builder sourceSnapshotVersion(String v) { this.sourceSnapshotVersion = v; return this; }
        public Builder evaluationAsOf(LocalDate v) { this.evaluationAsOf = v; return this; }
        public Builder certificationIds(List<String> v) { this.certificationIds = v; return this; }

        public PinnedArtifactSelection build() {
            return new PinnedArtifactSelection(
                    policyId, policyVersion, scorecardId, scorecardVersion, gacatSemanticVersion,
                    calculationDefinitionVersions, producerVersions, sourceSnapshotVersion,
                    evaluationAsOf, certificationIds);
        }
    }
}
