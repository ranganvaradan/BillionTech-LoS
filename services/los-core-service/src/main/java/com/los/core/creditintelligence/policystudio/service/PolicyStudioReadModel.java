package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Three-column UI read model: SOURCE / INTERPRETATION / EXECUTABLE RULE.
 */
@Component
public class PolicyStudioReadModel {

    public Map<String, Object> threeColumn(PolicyStudioSession session) {
        Map<UUID, CiPolicyInterpretation> interpByClause = session.getInterpretations().stream()
                .collect(Collectors.toMap(CiPolicyInterpretation::getClauseId, i -> i, (a, b) -> a));
        Map<UUID, List<CiPolicyRuleCandidate>> rulesByClause = session.getRuleCandidates().stream()
                .collect(Collectors.groupingBy(CiPolicyRuleCandidate::getClauseId));
        Map<UUID, List<CiPolicyTestCase>> testsByRule = session.getTestCases().stream()
                .collect(Collectors.groupingBy(CiPolicyTestCase::getRuleCandidateId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CiPolicyClause clause : session.getClauses()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", Map.of(
                    "clauseId", clause.getId().toString(),
                    "section", clause.getSection() == null ? "" : clause.getSection(),
                    "clauseNumber", clause.getClauseNumber() == null ? "" : clause.getClauseNumber(),
                    "parentClauseId", clause.getParentClauseId() == null ? "" : clause.getParentClauseId().toString(),
                    "text", clause.getSourceText(),
                    "clauseType", clause.getClauseType(),
                    "productScope", clause.getProductScope() == null ? "" : clause.getProductScope()
            ));
            CiPolicyInterpretation interp = interpByClause.get(clause.getId());
            if (interp != null) {
                row.put("interpretation", Map.of(
                        "meaning", interp.getNaturalLanguageMeaning() == null ? "" : interp.getNaturalLanguageMeaning(),
                        "period", interp.getCandidatePeriod() == null ? "" : interp.getCandidatePeriod(),
                        "thresholds", interp.getCandidateThresholds(),
                        "confidence", interp.getConfidence(),
                        "confidenceBreakdown", interp.getConfidenceBreakdown()
                ));
            } else {
                row.put("interpretation", Map.of());
            }
            List<Map<String, Object>> exec = new ArrayList<>();
            for (CiPolicyRuleCandidate rule : rulesByClause.getOrDefault(clause.getId(), List.of())) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("systemRuleId", rule.getSystemRuleId());
                r.put("expression", rule.getExpression());
                r.put("onTrue", rule.getOnTrue());
                r.put("onFalse", rule.getOnFalse());
                r.put("onMissing", rule.getOnMissing());
                r.put("lineage", rule.getLineage());
                r.put("tests", testsByRule.getOrDefault(rule.getId(), List.of()).stream()
                        .map(t -> Map.of("name", t.getName(), "expected", t.getExpectedOutcome(),
                                "boundary", t.getBoundaryCase()))
                        .toList());
                exec.add(r);
            }
            row.put("executableRule", exec);
            rows.add(row);
        }
        return Map.of(
                "documentId", session.getDocument().getId().toString(),
                "columns", List.of("SOURCE_POLICY", "INTERPRETATION", "EXECUTABLE_RULE"),
                "rows", rows
        );
    }
}
