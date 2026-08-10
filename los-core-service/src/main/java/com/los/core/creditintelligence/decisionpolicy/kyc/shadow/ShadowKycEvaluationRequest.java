package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Input for shadow KYC Decision Policy evaluation. All fields frozen for the call —
 * no live provider reads inside the evaluator.
 */
public record ShadowKycEvaluationRequest(
        UUID tenantId,
        UUID applicationId,
        CiExecutablePolicyPackage policyPackage,
        String routingOutcome,
        String routingReason,
        List<NormalizedKycFactBuilder.StepEvidence> stepEvidence,
        Map<String, Object> frozenFacts,
        Map<String, Object> applicationHints,
        Map<String, Object> applicationFields,
        String productionKycOutcome,
        LocalDate evaluationBusinessDate,
        Instant evaluationInstant,
        Map<String, Object> workflowProvenance,
        List<Object> evidenceRefs,
        boolean persist
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID tenantId;
        private UUID applicationId;
        private CiExecutablePolicyPackage policyPackage;
        private String routingOutcome = "EXACTLY_ONE";
        private String routingReason;
        private List<NormalizedKycFactBuilder.StepEvidence> stepEvidence = List.of();
        private Map<String, Object> frozenFacts;
        private Map<String, Object> applicationHints = Map.of();
        private Map<String, Object> applicationFields = Map.of();
        private String productionKycOutcome;
        private LocalDate evaluationBusinessDate;
        private Instant evaluationInstant;
        private Map<String, Object> workflowProvenance;
        private List<Object> evidenceRefs = List.of();
        private boolean persist;

        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder applicationId(UUID v) { this.applicationId = v; return this; }
        public Builder policyPackage(CiExecutablePolicyPackage v) { this.policyPackage = v; return this; }
        public Builder routingOutcome(String v) { this.routingOutcome = v; return this; }
        public Builder routingReason(String v) { this.routingReason = v; return this; }
        public Builder stepEvidence(List<NormalizedKycFactBuilder.StepEvidence> v) {
            this.stepEvidence = v == null ? List.of() : v; return this;
        }
        public Builder frozenFacts(Map<String, Object> v) { this.frozenFacts = v; return this; }
        public Builder applicationHints(Map<String, Object> v) {
            this.applicationHints = v == null ? Map.of() : v; return this;
        }
        public Builder applicationFields(Map<String, Object> v) {
            this.applicationFields = v == null ? Map.of() : v; return this;
        }
        public Builder productionKycOutcome(String v) { this.productionKycOutcome = v; return this; }
        public Builder evaluationBusinessDate(LocalDate v) { this.evaluationBusinessDate = v; return this; }
        public Builder evaluationInstant(Instant v) { this.evaluationInstant = v; return this; }
        public Builder workflowProvenance(Map<String, Object> v) { this.workflowProvenance = v; return this; }
        public Builder evidenceRefs(List<Object> v) {
            this.evidenceRefs = v == null ? List.of() : v; return this;
        }
        public Builder persist(boolean v) { this.persist = v; return this; }

        public ShadowKycEvaluationRequest build() {
            return new ShadowKycEvaluationRequest(
                    tenantId, applicationId, policyPackage, routingOutcome, routingReason,
                    stepEvidence, frozenFacts, applicationHints, applicationFields,
                    productionKycOutcome, evaluationBusinessDate, evaluationInstant,
                    workflowProvenance, evidenceRefs, persist);
        }
    }
}
