package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CleanHistoryReadinessHonestyTest {

    @BeforeEach
    void installSeedRegistry() {
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
    }

    @Test
    void cleanHistoryMonthsIsNotExecutionReadyWhenVocabularyGated() {
        Map<String, Object> op = RuleOperandPresenter.buildOperands(
                        "OVERDUE_EXCEPTION_PARENT",
                        List.of("bureau.credit_after_overdue.clean_history_months"),
                        Map.of("parameterId", "bureau.credit_after_overdue.clean_history_months"),
                        Map.of())
                .stream()
                .filter(o -> "clean_history".equals(String.valueOf(o.get("operandKey")))
                        || String.valueOf(o.get("parameterId")).contains("clean_history"))
                .findFirst()
                .orElseThrow();
        assertThat(PolicyExecutionReadiness.operandBlocksExecution(op)).isTrue();
        assertThat(Boolean.TRUE.equals(op.get("needsConfiguration"))
                || Boolean.TRUE.equals(op.get("calculationRequired"))
                || ParameterResolutionSupport.AVAIL_NEEDS_CONFIG.equals(op.get("availability")))
                .isTrue();
        assertThat(String.valueOf(op.get("parameterId")))
                .isEqualTo("bureau.credit_after_overdue.clean_history_months");
    }
}
