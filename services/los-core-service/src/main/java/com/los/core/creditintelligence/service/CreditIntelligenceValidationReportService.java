package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.DataStatus;
import com.los.core.creditintelligence.domain.EvaluationType;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.repository.CiStandardRuleResultRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase F validation report (spec section 16) — read-only, no workflow mutation.
 */
@Service
@RequiredArgsConstructor
public class CreditIntelligenceValidationReportService {

    private final LoanApplicationRepository applicationRepository;
    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;
    private final CiCreditEvaluationRepository evaluationRepository;
    private final CiStandardRuleResultRepository ruleResultRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final CreditIntelligenceProperties properties;

    @Transactional(readOnly = true)
    public Map<String, Object> buildReport(UUID applicationId) {
        Map<String, Object> report = new LinkedHashMap<>();
        LoanApplication app = applicationRepository.findById(applicationId).orElse(null);
        report.put("application_id", applicationId != null ? applicationId.toString() : null);
        report.put("application_number", app != null ? app.getApplicationNumber() : null);
        report.put("tenant", properties.getDefaultTenantId().toString());
        report.put("product", app != null ? app.getLoanProduct() : null);

        Optional<CiFactSnapshot> snapOpt = snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(applicationId);
        if (snapOpt.isPresent()) {
            CiFactSnapshot snap = snapOpt.get();
            report.put("fact_snapshot_version", snap.getSnapshotVersion());
            report.put("fact_snapshot_hash", snap.getFactsHash());
            List<CiUnderwritingFact> facts = factRepository.findBySnapshotId(snap.getId());
            long defaulted = facts.stream()
                    .filter(f -> FactClassification.DEFAULTED.name().equals(f.getClassification()))
                    .count();
            report.put("defaulted_fact_count", defaulted);
        } else {
            report.put("fact_snapshot_version", null);
            report.put("fact_snapshot_hash", null);
            report.put("defaulted_fact_count", 0);
        }

        List<CiCreditEvaluation> evals = evaluationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        CiCreditEvaluation shadow = evals.stream()
                .filter(e -> EvaluationType.SHADOW.name().equals(e.getEvaluationType()))
                .findFirst()
                .orElse(null);
        CiCreditEvaluation prodRef = evals.stream()
                .filter(e -> EvaluationType.PRODUCTION_REFERENCE.name().equals(e.getEvaluationType()))
                .findFirst()
                .orElse(null);

        report.put("production_outcome", prodRef != null ? prodRef.getOverallOutcome()
                : (shadow != null ? null : null));
        if (prodRef != null) {
            report.put("production_outcome", prodRef.getOverallOutcome());
        } else {
            report.put("production_outcome", null);
        }
        report.put("shadow_outcome", shadow != null ? shadow.getOverallOutcome() : null);
        report.put("comparison_status", shadow != null ? shadow.getComparisonStatus() : null);

        UUID policyVersionId = shadow != null ? shadow.getPolicyVersionId()
                : (prodRef != null ? prodRef.getPolicyVersionId() : null);
        if (policyVersionId != null) {
            Optional<CiPolicyVersion> pv = policyVersionRepository.findById(policyVersionId);
            report.put("policy_version", pv.map(CiPolicyVersion::getVersion).orElse(null));
            report.put("policy_hash", pv.map(CiPolicyVersion::getContentHash).orElse(null));
        } else {
            report.put("policy_version", null);
            report.put("policy_hash", null);
        }

        if (shadow != null) {
            List<CiStandardRuleResult> results = ruleResultRepository.findByEvaluationId(shadow.getId());
            report.put("rule_result_count", results.size());
            report.put("pass_count", countOutcome(results, RuleOutcome.PASS.name()));
            report.put("fail_count", countOutcome(results, RuleOutcome.FAIL.name()));
            report.put("refer_count", countOutcome(results, RuleOutcome.REFER.name()));
            report.put("data_insufficient_count", countOutcome(results, RuleOutcome.DATA_INSUFFICIENT.name()));
            Object duration = shadow.getMetadata() != null ? shadow.getMetadata().get("durationMs") : null;
            report.put("shadow_duration_ms", duration);
        } else {
            report.put("rule_result_count", 0);
            report.put("pass_count", 0);
            report.put("fail_count", 0);
            report.put("refer_count", 0);
            report.put("data_insufficient_count", 0);
            report.put("shadow_duration_ms", null);
        }
        return report;
    }

    private static long countOutcome(List<CiStandardRuleResult> results, String outcome) {
        return results.stream().filter(r -> outcome.equals(r.getOutcome())).count();
    }
}
