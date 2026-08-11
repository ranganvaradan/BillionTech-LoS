package com.los.core.service.underwriting;

import com.los.core.model.entity.UnderwritingEvaluation;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reconstructs historical underwriting explanation from the immutable V118 snapshot only.
 * Does not re-resolve current workflow / rules / scorecard / GACAT rows.
 */
@Service
public class HistoricalDecisionExplanationService {

    public Map<String, Object> explainFromSnapshot(UnderwritingEvaluation e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evaluationId", e.getId() == null ? null : e.getId().toString());
        out.put("applicationId", e.getApplicationId() == null ? null : e.getApplicationId().toString());
        out.put("evaluatedAt", e.getEvaluatedAt() == null ? null : e.getEvaluatedAt().toString());
        out.put("aggregateDecision", e.getAggregateDecision());
        out.put("aggregateScore", e.getAggregateScore());
        out.put("evaluatedBy", e.getEvaluatedBy());
        out.put("explanationSource", "DECISION_SNAPSHOT");
        out.put("allowCanonicalAuthority", false);
        out.put("requiresCurrentConfig", false);

        Map<String, Object> snap = e.getDecisionSnapshotJson();
        if (snap == null || snap.isEmpty()) {
            out.put("explanationSource", "LEGACY_FIELDS_NO_SNAPSHOT");
            out.put("requiresCurrentConfig", true);
            out.put("legacy", Map.of(
                    "effectiveValues", e.getEffectiveValuesJson() == null ? Map.of() : e.getEffectiveValuesJson(),
                    "ruleResults", e.getRuleResultsJson() == null ? java.util.List.of() : e.getRuleResultsJson(),
                    "scorecardEvidence", e.getScorecardEvidenceJson() == null ? Map.of() : e.getScorecardEvidenceJson(),
                    "parameterResults", e.getParameterResultsJson() == null ? java.util.List.of() : e.getParameterResultsJson(),
                    "selectedSources", e.getSelectedSourceJson() == null ? Map.of() : e.getSelectedSourceJson()));
            return out;
        }

        out.put("immutable", snap.getOrDefault("immutable", true));
        out.put("snapshotVersion", snap.get("snapshotVersion"));
        out.put("capturedAt", snap.get("capturedAt"));
        out.put("tenantId", snap.get("tenantId"));
        out.put("routing", snap.get("routing"));
        out.put("facts", snap.get("facts"));
        out.put("rules", snap.get("rules"));
        out.put("scorecard", snap.get("scorecard"));
        out.put("decision", snap.get("decision"));
        out.put("application", snap.get("application"));
        out.put("productionAuthority", snap.getOrDefault("productionAuthority", "LIVE_UW_PATH"));
        return out;
    }
}
