package com.los.core.service.underwriting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LOS-PRODUCTION-P0-CLOSURE-1 — historical decision must not re-resolve current config.
 */
@ExtendWith(MockitoExtension.class)
class HistoricalDecisionReproducibilityTest {

    @Mock UnderwritingScorecardRepository scorecardRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HistoricalDecisionExplanationService explanation = new HistoricalDecisionExplanationService();

    @Test
    void historicalExplanation_usesSnapshotOnly_withoutScorecardLookup() throws Exception {
        UUID evalId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID scorecardId = UUID.randomUUID();
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("immutable", true);
        snap.put("snapshotVersion", 1);
        snap.put("routing", Map.of(
                "workflowId", "wf-1",
                "workflowVersion", 2,
                "ruleSetIds", List.of("rule-a"),
                "scorecardId", scorecardId.toString(),
                "scorecardVersion", 7));
        snap.put("scorecard", Map.of(
                "scorecardVersion", 7,
                "evidence", Map.of("earnedPoints", 40, "maxPoints", 100, "normalizedScore", 0.4)));
        snap.put("decision", Map.of("aggregateCreditDecision", "APPROVE"));
        snap.put("facts", List.of(Map.of("parameter", "BUREAU_SCORE", "value", 750, "provenance", "MANUAL_AUTHORISED")));
        snap.put("rules", List.of(Map.of("ruleId", "rule-a", "creditDecision", "APPROVE")));

        UnderwritingEvaluation e = UnderwritingEvaluation.builder()
                .id(evalId)
                .applicationId(appId)
                .evaluatedAt(Instant.parse("2026-08-01T10:00:00Z"))
                .aggregateDecision("APPROVE")
                .aggregateScore(80)
                .scorecardId(scorecardId)
                .scorecardVersion(7)
                .decisionSnapshotJson(snap)
                .effectiveValuesJson(Map.of())
                .ruleResultsJson(List.of())
                .selectedSourceJson(Map.of())
                .build();

        Map<String, Object> hist = explanation.explainFromSnapshot(e);
        assertThat(hist.get("requiresCurrentConfig")).isEqualTo(false);
        assertThat(hist.get("explanationSource")).isEqualTo("DECISION_SNAPSHOT");
        @SuppressWarnings("unchecked")
        Map<String, Object> routing = (Map<String, Object>) hist.get("routing");
        assertThat(routing.get("scorecardVersion")).isEqualTo(7);
        assertThat(routing.get("workflowVersion")).isEqualTo(2);

        // Mutate "current" world — snapshot explanation must stay identical
        String before = mapper.writeValueAsString(hist);
        snap.put("SHOULD_NOT_APPEAR_IN_COPY", true); // mutate map reference carefully
        Map<String, Object> snapCopy = new LinkedHashMap<>(e.getDecisionSnapshotJson());
        e.setDecisionSnapshotJson(new LinkedHashMap<>(snapCopy)); // restore
        // Simulate future scorecard version change in DB by never calling repository
        when(scorecardRepository.findById(any())).thenReturn(Optional.empty());
        UnderwritingEvaluationService svc = new UnderwritingEvaluationService(
                null, scorecardRepository, null, explanation);
        Map<String, Object> api = svc.toApiMap(e);
        assertThat(api.get("requiresCurrentConfig")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> hist2 = (Map<String, Object>) api.get("historicalExplanation");
        assertThat(hist2.get("routing")).isEqualTo(routing);
        // math must come from snapshot (display-only name lookup may hit DB)
        assertThat(((Map<?, ?>) hist2.get("scorecard")).get("evidence")).isEqualTo(
                Map.of("earnedPoints", 40, "maxPoints", 100, "normalizedScore", 0.4));
        assertThat(before).contains("\"scorecardVersion\":7");
    }

    @Test
    void snapshotMutationApi_rejected() {
        UnderwritingEvaluationService svc = new UnderwritingEvaluationService(
                null, scorecardRepository, null, explanation);
        assertThatThrownBy(() -> svc.rejectSnapshotMutation(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("DECISION_SNAPSHOT_IMMUTABLE");
    }

    @Test
    void oldSnapshotUnchangedWhenNewEvaluationExists() {
        Map<String, Object> oldSnap = Map.of(
                "immutable", true,
                "scorecard", Map.of("scorecardVersion", 1, "evidence", Map.of("earnedPoints", 40)));
        Map<String, Object> newSnap = Map.of(
                "immutable", true,
                "scorecard", Map.of("scorecardVersion", 2, "evidence", Map.of("earnedPoints", 45)));
        UnderwritingEvaluation oldE = UnderwritingEvaluation.builder()
                .id(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .evaluatedAt(Instant.now().minusSeconds(3600))
                .aggregateDecision("APPROVE")
                .decisionSnapshotJson(new LinkedHashMap<>(oldSnap))
                .effectiveValuesJson(Map.of())
                .ruleResultsJson(List.of())
                .selectedSourceJson(Map.of())
                .build();
        UnderwritingEvaluation newE = UnderwritingEvaluation.builder()
                .id(UUID.randomUUID())
                .applicationId(oldE.getApplicationId())
                .evaluatedAt(Instant.now())
                .aggregateDecision("APPROVE")
                .decisionSnapshotJson(new LinkedHashMap<>(newSnap))
                .effectiveValuesJson(Map.of())
                .ruleResultsJson(List.of())
                .selectedSourceJson(Map.of())
                .build();
        Map<String, Object> oldHist = explanation.explainFromSnapshot(oldE);
        Map<String, Object> newHist = explanation.explainFromSnapshot(newE);
        assertThat(oldHist.get("scorecard")).isNotEqualTo(newHist.get("scorecard"));
        assertThat(((Map<?, ?>) ((Map<?, ?>) oldHist.get("scorecard")).get("evidence")).get("earnedPoints"))
                .isEqualTo(40);
        assertThat(((Map<?, ?>) ((Map<?, ?>) newHist.get("scorecard")).get("evidence")).get("earnedPoints"))
                .isEqualTo(45);
        // re-read old after new exists
        assertThat(explanation.explainFromSnapshot(oldE).get("scorecard")).isEqualTo(oldHist.get("scorecard"));
    }
}
