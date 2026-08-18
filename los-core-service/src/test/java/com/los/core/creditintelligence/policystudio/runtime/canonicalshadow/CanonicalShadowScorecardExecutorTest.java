package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalShadowScorecardExecutorTest {

    @Mock
    private UnderwritingScorecardRepository scorecardRepository;

    private CanonicalParameterExecutionService cpes;
    private CanonicalShadowScorecardExecutor executor;

    @BeforeEach
    void setUp() {
        cpes = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        executor = new CanonicalShadowScorecardExecutor(scorecardRepository, cpes);
    }

    @Test
    void presentFactors_fullInputs_scoreAndDoNotSkip() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                present("application.requested_amount", 40, 100),
                present("bureau.score", 60, 100)
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        EvaluationContext spine = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("application.requested_amount", 500000)
                .fact("bureau.score", 720)
                .build();
        Map<String, Object> out = executor.execute(freeze(cardId), spine);
        assertThat(out.get("executable")).isEqualTo(true);
        assertThat(out.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
        assertThat(out.get("scorecardZeroMaxWithConfiguredFactors")).isEqualTo(0);
        assertThat((Integer) out.get("configuredFactorCount")).isEqualTo(2);
        assertThat((Integer) out.get("maxPoints")).isEqualTo(100);
        assertThat(out.get("numericalScore")).isEqualTo(100);
        assertThat(out.get("band")).isEqualTo("APPROVE");
    }

    @Test
    void presentFactors_allMissingRequired_dataInsufficientNotZeroReject() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                present("application.requested_amount", 50, 100),
                present("bureau.score", 50, 100)
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        EvaluationContext spine = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .build();
        Map<String, Object> out = executor.execute(freeze(cardId), spine);
        assertThat(out.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
        assertThat((Integer) out.get("configuredFactorCount")).isEqualTo(2);
        assertThat(out.get("band")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(out.get("maxPoints")).isEqualTo(100);
        assertThat(out.get("scorecardZeroMaxWithConfiguredFactors")).isEqualTo(0);
    }

    @Test
    void presentFactors_partialInputs_requiredMissingIsDataInsufficient() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                present("application.requested_amount", 50, 100),
                present("bureau.score", 50, 100)
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        EvaluationContext spine = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("bureau.score", 800)
                .build();
        Map<String, Object> out = executor.execute(freeze(cardId), spine);
        assertThat(out.get("band")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(out.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
    }

    @Test
    void absentCondition_awardsWhenMissingAndZeroWhenPresent() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                row("application.requested_amount", "ABSENT", 100, 100, "OPTIONAL_SKIP")
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        EvaluationContext missing = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .build();
        Map<String, Object> absentOut = executor.execute(freeze(cardId), missing);
        assertThat(absentOut.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
        assertThat(absentOut.get("numericalScore")).isEqualTo(100);

        EvaluationContext present = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("application.requested_amount", 1)
                .build();
        Map<String, Object> presentOut = executor.execute(freeze(cardId), present);
        assertThat(presentOut.get("numericalScore")).isEqualTo(0);
        assertThat(presentOut.get("band")).isEqualTo("REJECT");
    }

    @Test
    void booleanAndNumericRange_mixedWithPresent() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                present("kyc.quality", 20, 100),
                row("bureau.score", "GTE:650", 40, 100, "REQUIRED"),
                row("application.requested_amount", "EQ:true", 40, 100, "REQUIRED")
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        EvaluationContext spine = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("kyc.quality", 1)
                .fact("bureau.score", 710)
                .fact("application.requested_amount", true)
                .build();
        Map<String, Object> out = executor.execute(freeze(cardId), spine);
        assertThat(out.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
        assertThat((Integer) out.get("configuredFactorCount")).isEqualTo(3);
        assertThat(out.get("executable")).isEqualTo(true);
        assertThat(out.get("band")).isNotEqualTo("DATA_INSUFFICIENT");
        assertThat((Integer) out.get("numericalScore")).isGreaterThan(0);
    }

    @Test
    void unsupportedCondition_doesNotSilentlySkip() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = northwindWeighted(cardId, List.of(
                row("bureau.score", "MAGIC:xyz", 100, 100, "REQUIRED")
        ));
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        Map<String, Object> out = executor.execute(freeze(cardId), EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("bureau.score", 700)
                .build());
        assertThat(out.get("executable")).isEqualTo(false);
        assertThat((Integer) out.get("configuredExecutableFactorSkippedCount")).isGreaterThan(0);
        assertThat(String.valueOf(out.get("problems"))).contains("UNSUPPORTED_CONDITION");
    }

    @Test
    void independentNonVikasamScorecard_executesPresent() {
        UUID cardId = UUID.randomUUID();
        UnderwritingScorecard card = UnderwritingScorecard.builder()
                .id(cardId)
                .name("Northwind Retail Presence Card")
                .borrowerType("INDIVIDUAL")
                .loanProduct("WORKING_CAPITAL")
                .version(1)
                .status("DRAFT")
                .scoringMode(PolicyWeightedScorecardEngine.MODE)
                .scorecardJson(Map.of(
                        "mode", PolicyWeightedScorecardEngine.MODE,
                        "rows", List.of(present("gst.turnover.trailing_12m", 100, 100))
                ))
                .thresholdsJson(Map.of("approveMinPercent", 60, "manualMinPercent", 40))
                .safetyJson(Map.of("missingDataPoliciesConfirmed", true, "factorPolicies", Map.of()))
                .build();
        when(scorecardRepository.findById(cardId)).thenReturn(Optional.of(card));
        Map<String, Object> out = executor.execute(freeze(cardId), EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 1, 15))
                .fact("gst.turnover.trailing_12m", 12_000_000)
                .build());
        assertThat(card.getName()).doesNotContain("Vikasam");
        assertThat(out.get("numericalScore")).isEqualTo(100);
        assertThat(out.get("configuredExecutableFactorSkippedCount")).isEqualTo(0);
        assertThat(out.get("band")).isEqualTo("APPROVE");
    }

    private static Map<String, Object> present(String parameter, int weight, int score) {
        return row(parameter, "PRESENT", weight, score, "REQUIRED");
    }

    private static Map<String, Object> row(String parameter, String condition, int weight, int score, String missing) {
        return Map.of(
                "id", "f-" + parameter,
                "parameter", parameter,
                "canonicalParameterId", parameter,
                "source", "POLICY",
                "condition", condition,
                "weight", weight,
                "score", score,
                "missingData", missing);
    }

    private static UnderwritingScorecard northwindWeighted(UUID id, List<Map<String, Object>> rows) {
        return UnderwritingScorecard.builder()
                .id(id)
                .name("Northwind Weighted Scorecard")
                .borrowerType("INDIVIDUAL")
                .loanProduct("WORKING_CAPITAL")
                .version(1)
                .status("DRAFT")
                .scoringMode(PolicyWeightedScorecardEngine.MODE)
                .scorecardJson(Map.of("mode", PolicyWeightedScorecardEngine.MODE, "rows", rows))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 40))
                .safetyJson(Map.of("missingDataPoliciesConfirmed", true, "factorPolicies", Map.of()))
                .build();
    }

    private static CanonicalApplicationConfiguration freeze(UUID scorecardId) {
        return new CanonicalApplicationConfiguration(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "NORTHWIND",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "v1",
                scorecardId,
                1,
                true,
                false,
                UUID.randomUUID(),
                "GENERIC",
                "v1",
                "v1",
                LocalDate.of(2026, 1, 15),
                List.of(),
                Instant.parse("2026-01-15T00:00:00Z"),
                Map.of("source", "northwind-fixture"));
    }
}
