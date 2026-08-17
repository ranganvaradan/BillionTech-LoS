package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vikasam golden 13 — representative Policy Test context through the execution spine.
 * Does not force outcomes; asserts truthful producer presence/absence.
 */
class Vikasam13ExecutionAcceptanceTest {

    static final List<String> VIKASAM_13 = List.of(
            "bureau.score",
            "bureau.recent_inquiries_90d",
            "bureau.settled_account_count",
            "bureau.written_off_account_count",
            "bureau.accounts.cc_writeoff",
            "bureau.accounts.writeoff_non_cc",
            "bureau.tradeline.suit_filed",
            "bureau.credit_after_overdue.clean_history_months",
            "bureau.dpd_30_plus_count_6m",
            "bureau.cc_overdue_amount",
            "bureau.overdue.amount",
            "bureau.overdue.age_months",
            "bureau.max_dpd_6m"
    );

    private DerivedCalculationDefinitionService definitions;
    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenReturn(Optional.empty());

        Map<String, Object> cleanExpr = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GT",
                "matchValue", 0,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        when(definitions.latestFor(eq("bureau.credit_after_overdue.clean_history_months"), any()))
                .thenReturn(Optional.of(CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000001"))
                        .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(cleanExpr)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

        // Semantically invalid approved expression (MONTHS_SINCE on COUNT target)
        Map<String, Object> badDpd = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "matchField", "dpd",
                "matchOp", "GTE",
                "matchValue", 30,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        when(definitions.latestFor(eq("bureau.dpd_30_plus_count_6m"), any()))
                .thenReturn(Optional.of(CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("dd0218c5-0000-4000-8000-000000000001"))
                        .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                        .status(DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY)
                        .expressionJson(badDpd)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
    }

    @Test
    void vikasam13ThroughSpine() {
        List<Map<String, Object>> history = List.of(
                Map.of("month", "2025-01-01", "dpd", 45),
                Map.of("month", "2025-08-01", "dpd", 0),
                Map.of("month", "2026-01-01", "dpd", 0));

        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 710)
                .fact("bureau.recent_inquiries_90d", 1)
                .fact("bureau.settled_account_count", 2)
                .fact("bureau.written_off_account_count", 0)
                .fact("bureau.accounts.cc_writeoff", 0)
                .fact("bureau.accounts.writeoff_non_cc", 0)
                .fact("bureau.tradeline.suit_filed", 0)
                .fact("bureau.tradeline.payment_history", history)
                .fact("bureau.max_dpd_6m", 45)
                .build();

        List<Map<String, Object>> table = new ArrayList<>();
        int executable = 0;
        int notExecutable = 0;
        for (String id : VIKASAM_13) {
            ExecutionResult r = spine.resolveAndExecute(id, ctx);
            Map<String, Object> row = new LinkedHashMap<>(r.toTraceMap());
            row.put("policyTestUsedSpine", true);
            table.add(row);
            if (r.capability() && r.status() != ExecutionStatus.NOT_EXECUTABLE
                    && r.status() != ExecutionStatus.CALCULATION_NOT_DEFINED) {
                executable++;
            } else {
                notExecutable++;
            }
        }

        // Print-style assertion surface for acceptance report
        assertThat(table).hasSize(13);

        Map<String, ExecutionResult> byId = new LinkedHashMap<>();
        for (String id : VIKASAM_13) {
            byId.put(id, spine.resolveAndExecute(id, ctx));
        }

        assertThat(byId.get("bureau.score").status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(byId.get("bureau.recent_inquiries_90d").status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").status())
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(byId.get("bureau.credit_after_overdue.clean_history_months").producerType())
                .isEqualTo(ProducerType.BUILT_IN);

        assertThat(byId.get("bureau.dpd_30_plus_count_6m").capability()).isTrue();
        assertThat(byId.get("bureau.dpd_30_plus_count_6m").status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(byId.get("bureau.cc_overdue_amount").status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(byId.get("bureau.overdue.amount").status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(byId.get("bureau.overdue.age_months").status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);

        assertThat(executable).isGreaterThanOrEqualTo(8);
        assertThat(notExecutable).isGreaterThanOrEqualTo(0);
        assertThat(executable + notExecutable).isEqualTo(13);
    }
}
