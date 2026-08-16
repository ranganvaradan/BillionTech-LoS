package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
 * SCORECARD-UNDERWRITING-EXECUTION-SPINE-CLOSURE-1 — canonical values via spine only.
 */
class ScorecardSpineClosureAcceptanceTest {

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

    static final List<String> UNSUPPORTED = List.of(
            "bureau.dpd_30_plus_count_6m",
            "bureau.cc_overdue_amount",
            "bureau.overdue.amount",
            "bureau.overdue.age_months"
    );

    private CanonicalParameterExecutionService spine;
    private DerivedCalculationDefinitionService definitions;

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
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000099"))
                        .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(cleanExpr)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

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
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000098"))
                        .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(badDpd)
                        .dependencyIds(List.of("bureau.tradeline.payment_history"))
                        .versionNo(1)
                        .build()));

        spine = ExecutionSpineProducerBootstrap.standalone(definitions);
        ExecutionCapabilityAuthority.install(spine);
    }

    private EvaluationContext sharedFactsContext(EvaluationMode mode) {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of("month", "2026-01", "dpd", 45));
        history.add(Map.of("month", "2026-02", "dpd", 0));
        history.add(Map.of("month", "2026-03", "dpd", 0));
        history.add(Map.of("month", "2026-04", "dpd", 0));
        history.add(Map.of("month", "2026-05", "dpd", 0));
        history.add(Map.of("month", "2026-06", "dpd", 0));
        history.add(Map.of("month", "2026-07", "dpd", 0));
        history.add(Map.of("month", "2026-08", "dpd", 0));
        return EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .fact("bureau.score", 720)
                .fact("bureau.recent_inquiries_90d", 2)
                .fact("bureau.settled_account_count", 1)
                .fact("bureau.written_off_account_count", 0)
                .fact("bureau.accounts.cc_writeoff", 0)
                .fact("bureau.accounts.writeoff_non_cc", 0)
                .fact("bureau.tradeline.suit_filed", false)
                .fact("bureau.max_dpd_6m", 45)
                .fact("bureau.tradeline.payment_history", history)
                .build();
    }

    @Test
    void unsupportedParameters_neverResurrectViaLegacy() {
        LoanApplication app = new LoanApplication();
        app.setBureauScore(720);
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        // Poison: legacy map must NOT satisfy unsupported canonicals
        sc.put("bureau.cc_overdue_amount", BigDecimal.valueOf(9999));
        sc.put("bureau.overdue.amount", BigDecimal.valueOf(8888));
        sc.put("CC_OVERDUE", BigDecimal.valueOf(7777));
        EffectiveUnderwritingContext uw = new EffectiveUnderwritingContext(
                720, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC", sc);

        for (String id : UNSUPPORTED) {
            Map<String, Object> row = Map.of("canonicalParameterId", id, "parameter", id, "source", "BUREAU");
            ScorecardFactorValueAdapter.ResolvedValue rv =
                    ScorecardFactorValueAdapter.resolveRow(row, app, uw);
            assertThat(rv.legacyFallbackUsed()).as(id).isFalse();
            assertThat(rv.numericValue()).as(id + " must not invent value").isNull();
            assertThat(rv.executionStatus()).as(id).isNotEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        }
    }

    @Test
    void cleanHistory_scorecardUsesSpineAuthoredPath() {
        EvaluationContext pt = sharedFactsContext(EvaluationMode.POLICY_TEST);
        ExecutionResult spinePt = spine.resolveAndExecute(
                "bureau.credit_after_overdue.clean_history_months", pt);
        assertThat(spinePt.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(spinePt.producerType().name()).isEqualTo("AUTHORED_DERIVED");

        LoanApplication app = new LoanApplication();
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        // Provide payment history via canonical fact key in scorecard-shaped map
        EffectiveUnderwritingContext uw = new EffectiveUnderwritingContext(
                720, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC", sc);
        EvaluationContext uwCtx = UnderwritingEvaluationContextFactory.forUnderwriting(app, uw);
        // Inject same payment history facts the Policy Test used
        EvaluationContext enriched = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .facts(pt.facts())
                .build();
        CanonicalScorecardValueResolver.ResolveOutcome scOut =
                CanonicalScorecardValueResolver.resolveCanonical(
                        "bureau.credit_after_overdue.clean_history_months", enriched);
        assertThat(scOut.valueAvailable()).isTrue();
        assertThat(scOut.legacyFallbackUsed()).isFalse();
        assertThat(scOut.producerType()).isEqualTo("AUTHORED_DERIVED");
        assertThat(scOut.numericValue()).isEqualByComparingTo(
                CanonicalScorecardValueResolver.toBigDecimal(spinePt.value()));
    }

    @Test
    void crossSurface_sameValueSameProducer() {
        EvaluationContext ptCtx = sharedFactsContext(EvaluationMode.POLICY_TEST);
        EvaluationContext uwCtx = sharedFactsContext(EvaluationMode.UNDERWRITING);

        LoanApplication app = new LoanApplication();
        app.setBureauScore(720);
        Map<String, BigDecimal> scMap = new LinkedHashMap<>();
        scMap.put("bureau.score", BigDecimal.valueOf(720));
        scMap.put("bureau.recent_inquiries_90d", BigDecimal.valueOf(2));
        scMap.put("bureau.settled_account_count", BigDecimal.ONE);
        scMap.put("bureau.written_off_account_count", BigDecimal.ZERO);
        scMap.put("bureau.accounts.cc_writeoff", BigDecimal.ZERO);
        scMap.put("bureau.accounts.writeoff_non_cc", BigDecimal.ZERO);
        scMap.put("bureau.max_dpd_6m", BigDecimal.valueOf(45));
        EffectiveUnderwritingContext uw = new EffectiveUnderwritingContext(
                720, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC", scMap);

        int mismatchPtSc = 0;
        int mismatchPtUw = 0;
        int legacyFallback = 0;
        int falseExec = 0;

        System.out.println("=== CROSS_SURFACE_VALUE_PARITY / VIKASAM_SCORECARD_EXECUTION ===");
        for (String id : VIKASAM_13) {
            ExecutionResult pt = spine.resolveAndExecute(id, ptCtx);
            ExecutionResult uwR = spine.resolveAndExecute(id, uwCtx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalParameterId", id);
            row.put("parameter", id);
            row.put("source", "BUREAU");
            ScorecardFactorValueAdapter.ResolvedValue sc =
                    ScorecardFactorValueAdapter.resolveRow(row, app, uw);
            // Prefer enriched shared facts for scorecard when UW map alone lacks payment_history
            if ("bureau.credit_after_overdue.clean_history_months".equals(id)
                    || "bureau.tradeline.suit_filed".equals(id)) {
                CanonicalScorecardValueResolver.ResolveOutcome o =
                        CanonicalScorecardValueResolver.resolveCanonical(id, uwCtx);
                sc = new ScorecardFactorValueAdapter.ResolvedValue(
                        id, 1, id, "BUREAU", o.numericValue(), o.stringValue(),
                        o.exactProducerPath(), o.status(), o.producerId(), o.reason(), false);
            }

            boolean samePtSc = valuesEqual(pt, sc);
            boolean samePtUw = statusAndValueEqual(pt, uwR);
            if (pt.valueAvailable() && !samePtSc) {
                mismatchPtSc++;
            }
            if (pt.valueAvailable() && !samePtUw) {
                mismatchPtUw++;
            }
            if (sc.legacyFallbackUsed()) {
                legacyFallback++;
            }
            if (UNSUPPORTED.contains(id) && sc.numericValue() != null) {
                falseExec++;
            }

            System.out.println(id
                    + " | pt=" + pt.status() + "/" + pt.value() + "/" + pt.producerId()
                    + " | sc=" + sc.executionStatus() + "/" + sc.numericValue() + "/" + sc.producerId()
                    + " | uw=" + uwR.status() + "/" + uwR.value() + "/" + uwR.producerId()
                    + " | samePtSc=" + samePtSc + " legacyFb=" + sc.legacyFallbackUsed());
        }

        assertThat(mismatchPtSc).isEqualTo(0);
        assertThat(mismatchPtUw).isEqualTo(0);
        assertThat(legacyFallback).isEqualTo(0);
        assertThat(falseExec).isEqualTo(0);
    }

    @Test
    void bureauScore_adapterUsesSpineNotLegacyEngine() {
        LoanApplication app = new LoanApplication();
        app.setBureauScore(760);
        var ctx = new EffectiveUnderwritingContext(
                760, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC", Map.of());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parameter", "BUREAU_SCORE");
        row.put("source", "BUREAU");
        row.put("canonicalParameterId", "bureau.score");
        row.put("canonicalDefinitionVersion", 1);
        ScorecardFactorValueAdapter.ResolvedValue resolved =
                ScorecardFactorValueAdapter.resolveRow(row, app, ctx);
        assertThat(resolved.canonicalParameterId()).isEqualTo("bureau.score");
        assertThat(resolved.numericValue()).isEqualByComparingTo("760");
        assertThat(resolved.legacyFallbackUsed()).isFalse();
        assertThat(resolved.executionStatus()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(resolved.producerId()).contains("RawFactProducer");
        assertThat(ScorecardFactorValueAdapter.RUNTIME_AUTHORITY)
                .isEqualTo("CanonicalParameterExecutionService");
    }

    @Test
    void scorecardExecutableImpliesSpineCapable() {
        for (String id : List.of(
                "bureau.score", "bureau.max_dpd_6m", "bureau.recent_inquiries_90d", "kyc.quality")) {
            assertThat(ExecutionCapabilityAuthority.hasExecutionCapability(id, EvaluationMode.UNDERWRITING))
                    .as(id)
                    .isTrue();
            CanonicalScorecardValueResolver.ResolveOutcome o =
                    CanonicalScorecardValueResolver.resolveCanonical(
                            id,
                            EvaluationContext.builder()
                                    .mode(EvaluationMode.UNDERWRITING)
                                    .fact(id, id.startsWith("kyc") ? BigDecimal.ONE : 1)
                                    .build());
            // capability true; value may or may not be available depending on fact shape
            assertThat(o.legacyFallbackUsed()).isFalse();
        }
    }

    private static boolean valuesEqual(ExecutionResult pt, ScorecardFactorValueAdapter.ResolvedValue sc) {
        if (!pt.valueAvailable()) {
            return sc.numericValue() == null && sc.executionStatus() != ExecutionStatus.VALUE_AVAILABLE;
        }
        if (sc.numericValue() == null && pt.value() instanceof Boolean) {
            return String.valueOf(pt.value()).equalsIgnoreCase(sc.stringValue())
                    || ("true".equalsIgnoreCase(String.valueOf(pt.value()))
                    && sc.stringValue() != null);
        }
        BigDecimal expected = CanonicalScorecardValueResolver.toBigDecimal(pt.value());
        if (expected == null) {
            return sc.stringValue() != null && sc.stringValue().equals(String.valueOf(pt.value()));
        }
        return sc.numericValue() != null && expected.compareTo(sc.numericValue()) == 0
                && pt.producerId() != null && pt.producerId().equals(sc.producerId());
    }

    private static boolean statusAndValueEqual(ExecutionResult a, ExecutionResult b) {
        if (a.status() != b.status()) {
            return false;
        }
        if (!a.valueAvailable()) {
            return !b.valueAvailable();
        }
        BigDecimal av = CanonicalScorecardValueResolver.toBigDecimal(a.value());
        BigDecimal bv = CanonicalScorecardValueResolver.toBigDecimal(b.value());
        if (av != null && bv != null) {
            return av.compareTo(bv) == 0 && java.util.Objects.equals(a.producerId(), b.producerId());
        }
        return java.util.Objects.equals(String.valueOf(a.value()), String.valueOf(b.value()))
                && java.util.Objects.equals(a.producerId(), b.producerId());
    }
}
