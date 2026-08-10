package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.service.DiscrepancySeverityClassifier;
import com.los.core.creditintelligence.reconciliation.service.LegacyReconciliationBridge;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationExplanationBuilder;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Asserts C2 GSTR1/3B and C4 ITR AIS/26AS outcome parity when wrapped into CiReconciliationResult.
 */
@ExtendWith(MockitoExtension.class)
class LegacyReconciliationBridgeParityTest {

    @Mock
    CiMetricResultRepository metricResultRepository;

    private LegacyReconciliationBridge bridge;
    private final UUID tenant = UUID.randomUUID();
    private final UUID app = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bridge = new LegacyReconciliationBridge(
                metricResultRepository,
                new ReconciliationExplanationBuilder(),
                new DiscrepancySeverityClassifier());
    }

    @Test
    void gstr1Gstr3bMatchParity() {
        CiMetricResult m = metric(GstMetricService.VARIANCE, GstMetricOutcome.MATCH.name(),
                pct(0.0), "100", "100");
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(app), eq(GstMetricService.VARIANCE))).thenReturn(Optional.of(m));
        var result = bridge.wrapIfLegacy(tenant, app, null, null, gstr1Def()).orElseThrow();
        assertThat(result.getOutcome()).isEqualTo(ReconciliationOutcome.MATCH.name());
        assertThat(result.getLeftValue()).isEqualByComparingTo("100");
        assertThat(result.getRightValue()).isEqualByComparingTo("100");
    }

    @Test
    void gstr1Gstr3bConflictParity() {
        CiMetricResult m = metric(GstMetricService.VARIANCE, GstMetricOutcome.CONFLICT.name(),
                pct(40.0), "1000000", "600000");
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(app), eq(GstMetricService.VARIANCE))).thenReturn(Optional.of(m));
        var result = bridge.wrapIfLegacy(tenant, app, null, null, gstr1Def()).orElseThrow();
        assertThat(result.getOutcome()).isEqualTo(ReconciliationOutcome.CONFLICT.name());
        assertThat(LegacyReconciliationBridge.mapOutcome(GstMetricOutcome.CONFLICT.name()))
                .isEqualTo(ReconciliationOutcome.CONFLICT.name());
    }

    @Test
    void itrAisAcceptableParity() {
        CiMetricResult m = metric(TaxMetricService.XSRC_AIS_INCOME,
                TaxMetricOutcome.ACCEPTABLE_VARIANCE.name(), pct(8.0), null, null);
        m.getEvidence().put("left", "1000000");
        m.getEvidence().put("right", "920000");
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(app), eq(TaxMetricService.XSRC_AIS_INCOME))).thenReturn(Optional.of(m));
        var result = bridge.wrapIfLegacy(tenant, app, null, null, aisDef()).orElseThrow();
        assertThat(result.getOutcome()).isEqualTo(ReconciliationOutcome.ACCEPTABLE_VARIANCE.name());
    }

    @Test
    void itr26asMaterialParity() {
        CiMetricResult m = metric(TaxMetricService.XSRC_26AS_TDS,
                TaxMetricOutcome.MATERIAL_VARIANCE.name(), pct(18.0), null, null);
        m.getEvidence().put("left", "500000");
        m.getEvidence().put("right", "410000");
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                eq(app), eq(TaxMetricService.XSRC_26AS_TDS))).thenReturn(Optional.of(m));
        var result = bridge.wrapIfLegacy(tenant, app, null, null, tdsDef()).orElseThrow();
        assertThat(result.getOutcome()).isEqualTo(ReconciliationOutcome.MATERIAL_VARIANCE.name());
    }

    @Test
    void dataInsufficientParity() {
        CiMetricResult m = metric(GstMetricService.VARIANCE,
                GstMetricOutcome.DATA_INSUFFICIENT.name(), null, null, null);
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                any(), any())).thenReturn(Optional.of(m));
        var result = bridge.wrapIfLegacy(tenant, app, null, null, gstr1Def()).orElseThrow();
        assertThat(result.getOutcome()).isEqualTo(ReconciliationOutcome.DATA_INSUFFICIENT.name());
    }

    private CiMetricResult metric(String code, String outcome, BigDecimal pct, String g1, String g3) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (pct != null) {
            value.put("v", pct);
        }
        if (g1 != null) {
            value.put("gstr1Sum", g1);
        }
        if (g3 != null) {
            value.put("gstr3bSum", g3);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (g1 != null) {
            evidence.put("gstr1Sum", g1);
        }
        if (g3 != null) {
            evidence.put("gstr3bSum", g3);
        }
        return CiMetricResult.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant)
                .applicationId(app)
                .metricCode(code)
                .metricVersion("V1")
                .outcome(outcome)
                .value(value)
                .evidence(evidence)
                .sourceRecordIds(List.of())
                .includedReferences(List.of())
                .excludedReferences(List.of())
                .metadata(Map.of())
                .build();
    }

    private CiReconciliationDefinition gstr1Def() {
        return CiReconciliationDefinition.builder()
                .reconciliationCode(ReconciliationConstants.XSRC_GSTR1_GSTR3B_TURNOVER)
                .version("V1").name("GSTR1/3B").category("TURNOVER")
                .periodAlignmentStrategy("COMMON_OVERLAP").varianceMethod("PERCENT_OF_MAX")
                .leftOperandDefinition(Map.of()).rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of()).allowedDataStatuses(List.of())
                .metadata(Map.of("legacyMetricCode", GstMetricService.VARIANCE, "migratedFrom", "C2"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    private CiReconciliationDefinition aisDef() {
        return CiReconciliationDefinition.builder()
                .reconciliationCode(ReconciliationConstants.XSRC_ITR_AIS_INCOME)
                .version("V1").name("AIS").category("INCOME")
                .periodAlignmentStrategy("FINANCIAL_YEAR").varianceMethod("PERCENT_OF_RIGHT")
                .leftOperandDefinition(Map.of()).rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of()).allowedDataStatuses(List.of())
                .metadata(Map.of("legacyMetricCode", TaxMetricService.XSRC_AIS_INCOME, "migratedFrom", "C4"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    private CiReconciliationDefinition tdsDef() {
        return CiReconciliationDefinition.builder()
                .reconciliationCode(ReconciliationConstants.XSRC_ITR_26AS_TDS)
                .version("V1").name("26AS").category("TAX")
                .periodAlignmentStrategy("FINANCIAL_YEAR").varianceMethod("PERCENT_OF_RIGHT")
                .leftOperandDefinition(Map.of()).rightOperandDefinition(Map.of())
                .dependencyMetricCodes(List.of()).allowedDataStatuses(List.of())
                .metadata(Map.of("legacyMetricCode", TaxMetricService.XSRC_26AS_TDS, "migratedFrom", "C4"))
                .missingDataPolicy("DATA_INSUFFICIENT")
                .explanationStrategyVersion(ReconciliationConstants.RECON_EXPLANATION_V1)
                .build();
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v);
    }
}
