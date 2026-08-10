package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.gst.domain.CiGstPeriodFinancials;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.CiGstReturnPeriod;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.domain.GstReturnType;
import com.los.core.creditintelligence.gst.repository.CiGstPeriodFinancialsRepository;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GstMetricServiceTest {

    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private CiGstPeriodFinancialsRepository financialsRepository;

    private GstMetricService service;
    private CreditIntelligenceProperties props;
    private UUID tenantId;
    private UUID appId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        props = new CreditIntelligenceProperties();
        service = new GstMetricService(metricResultRepository, financialsRepository, props);
        tenantId = UUID.randomUUID();
        appId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(metricResultRepository.save(any())).thenAnswer(inv -> {
            CiMetricResult m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
            }
            return m;
        });
    }

    @Test
    void trailing12m_completeWindow_pass() {
        LocalDate asOf = LocalDate.of(2026, 7, 25); // expected end = 2026-06 with lag 20
        YearMonth end = YearMonth.of(2026, 6);
        GstMetricService.PeriodBundle bundle = buildBundleWithMonths(end, 12, new BigDecimal("100000"), false);
        CiMetricResult r = service.computeTrailingTurnover(
                tenantId, appId, sourceId, List.of(bundle), asOf, 12, GstMetricService.TRAILING_12M, true);
        assertThat(r.getOutcome()).isEqualTo(GstMetricOutcome.PASS.name());
        assertThat(r.getValue()).isNotNull();
        assertThat(new BigDecimal(String.valueOf(r.getValue().get("v"))))
                .isEqualByComparingTo(new BigDecimal("1200000"));
    }

    @Test
    void trailing12m_partial_dataInsufficient() {
        LocalDate asOf = LocalDate.of(2026, 7, 25);
        YearMonth end = YearMonth.of(2026, 6);
        GstMetricService.PeriodBundle bundle = buildBundleWithMonths(end, 4, new BigDecimal("100000"), false);
        CiMetricResult r = service.computeTrailingTurnover(
                tenantId, appId, sourceId, List.of(bundle), asOf, 12, GstMetricService.TRAILING_12M, true);
        assertThat(r.getOutcome()).isEqualTo(GstMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.getValue()).isNull();
    }

    @Test
    void trailing_validZeroMonth_includedAsZero() {
        LocalDate asOf = LocalDate.of(2026, 7, 25);
        YearMonth end = YearMonth.of(2026, 6);
        // 11 months with 100k + 1 zero month
        List<CiGstReturnPeriod> periods = new ArrayList<>();
        Map<UUID, CiGstPeriodFinancials> fins = new HashMap<>();
        CiGstRegistration reg = baseReg();
        for (int i = 0; i < 12; i++) {
            YearMonth ym = end.minusMonths(11 - i);
            BigDecimal amt = i == 0 ? BigDecimal.ZERO : new BigDecimal("100000");
            addPeriod(reg, periods, fins, ym.toString(), GstReturnType.GSTR1.name(), amt, "FILED");
        }
        GstMetricService.PeriodBundle bundle = new GstMetricService.PeriodBundle(reg, periods, fins);
        CiMetricResult r = service.computeTrailingTurnover(
                tenantId, appId, sourceId, List.of(bundle), asOf, 12, GstMetricService.TRAILING_12M, true);
        assertThat(r.getOutcome()).isEqualTo(GstMetricOutcome.PASS.name());
        assertThat(new BigDecimal(String.valueOf(r.getValue().get("v"))))
                .isEqualByComparingTo(new BigDecimal("1100000"));
    }

    @Test
    void annualization_gatedWhenBelowMinMonths() {
        LocalDate asOf = LocalDate.of(2026, 7, 25);
        YearMonth end = YearMonth.of(2026, 6);
        GstMetricService.PeriodBundle bundle = buildBundleWithMonths(end, 3, new BigDecimal("100000"), false);
        CiMetricResult r = service.computeAnnualized(tenantId, appId, sourceId, List.of(bundle), asOf);
        assertThat(r.getOutcome()).isEqualTo(GstMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(r.getEvidence()).containsEntry("reason", "ANNUALIZATION_GATED");
        assertThat(r.getValue()).isNull();
    }

    @Test
    void multiGstin_sumsActiveTurnovers() {
        LocalDate asOf = LocalDate.of(2026, 7, 25);
        YearMonth end = YearMonth.of(2026, 6);
        GstMetricService.PeriodBundle a = buildBundleWithMonths(end, 12, new BigDecimal("100000"), false);
        a.registration().setGstin("29AAAAA0000A1Z5");
        GstMetricService.PeriodBundle b = buildBundleWithMonths(end, 12, new BigDecimal("50000"), false);
        b.registration().setGstin("29BBBBB0000B1Z5");
        CiMetricResult r = service.computeTrailingTurnover(
                tenantId, appId, sourceId, List.of(a, b), asOf, 12, GstMetricService.TRAILING_12M, true);
        assertThat(r.getOutcome()).isEqualTo(GstMetricOutcome.PASS.name());
        // 12 * (100k + 50k) = 1.8M
        assertThat(new BigDecimal(String.valueOf(r.getValue().get("v"))))
                .isEqualByComparingTo(new BigDecimal("1800000"));
        assertThat(r.getEvidence()).containsEntry("aggregation", "SUM_ACTIVE_GSTIN_PERIODS");
    }

    @Test
    void variance_matchAndMaterialAndDi() {
        LocalDate asOf = LocalDate.of(2026, 7, 25);
        YearMonth end = YearMonth.of(2026, 6);

        // MATCH: identical
        GstMetricService.PeriodBundle match = dualReturnBundle(end, 6, new BigDecimal("100"), new BigDecimal("100"));
        CiMetricResult m = service.computeVariance(tenantId, appId, sourceId, List.of(match), asOf);
        assertThat(m.getOutcome()).isEqualTo(GstMetricOutcome.MATCH.name());

        // MATERIAL: ~10% variance (warning 5 < pct <= material 15)
        GstMetricService.PeriodBundle material = dualReturnBundle(end, 6, new BigDecimal("100"), new BigDecimal("90"));
        CiMetricResult mat = service.computeVariance(tenantId, appId, sourceId, List.of(material), asOf);
        assertThat(mat.getOutcome()).isEqualTo(GstMetricOutcome.MATERIAL_VARIANCE.name());

        // CONFLICT: ~20% variance (> material 15)
        GstMetricService.PeriodBundle conflict = dualReturnBundle(end, 6, new BigDecimal("100"), new BigDecimal("80"));
        CiMetricResult conf = service.computeVariance(tenantId, appId, sourceId, List.of(conflict), asOf);
        assertThat(conf.getOutcome()).isEqualTo(GstMetricOutcome.CONFLICT.name());

        // DI: no gstr3b
        GstMetricService.PeriodBundle onlyGstr1 = buildBundleWithMonths(end, 6, new BigDecimal("100"), false);
        CiMetricResult di = service.computeVariance(tenantId, appId, sourceId, List.of(onlyGstr1), asOf);
        assertThat(di.getOutcome()).isEqualTo(GstMetricOutcome.DATA_INSUFFICIENT.name());
    }

    private GstMetricService.PeriodBundle buildBundleWithMonths(
            YearMonth end, int count, BigDecimal monthly, boolean includeGstr3b) {
        CiGstRegistration reg = baseReg();
        List<CiGstReturnPeriod> periods = new ArrayList<>();
        Map<UUID, CiGstPeriodFinancials> fins = new HashMap<>();
        for (int i = 0; i < count; i++) {
            YearMonth ym = end.minusMonths(count - 1 - i);
            addPeriod(reg, periods, fins, ym.toString(), GstReturnType.GSTR1.name(), monthly, "FILED");
            if (includeGstr3b) {
                addPeriod(reg, periods, fins, ym.toString(), GstReturnType.GSTR3B.name(), monthly, "FILED");
            }
        }
        return new GstMetricService.PeriodBundle(reg, periods, fins);
    }

    private GstMetricService.PeriodBundle dualReturnBundle(
            YearMonth end, int count, BigDecimal gstr1, BigDecimal gstr3b) {
        CiGstRegistration reg = baseReg();
        List<CiGstReturnPeriod> periods = new ArrayList<>();
        Map<UUID, CiGstPeriodFinancials> fins = new HashMap<>();
        for (int i = 0; i < count; i++) {
            YearMonth ym = end.minusMonths(count - 1 - i);
            addPeriod(reg, periods, fins, ym.toString(), GstReturnType.GSTR1.name(), gstr1, "FILED");
            addPeriod(reg, periods, fins, ym.toString(), GstReturnType.GSTR3B.name(), gstr3b, "FILED");
        }
        return new GstMetricService.PeriodBundle(reg, periods, fins);
    }

    private CiGstRegistration baseReg() {
        return CiGstRegistration.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(appId)
                .sourceRecordId(sourceId)
                .gstin("29AAHCB0052H2ZV")
                .registrationStatus("ACTIVE")
                .parserVersion("KARZA_GST_PARSER_V1")
                .normalizerVersion("GST_NORMALIZER_V1")
                .build();
    }

    private void addPeriod(
            CiGstRegistration reg,
            List<CiGstReturnPeriod> periods,
            Map<UUID, CiGstPeriodFinancials> fins,
            String yyyyMm,
            String returnType,
            BigDecimal amt,
            String status) {
        UUID pid = UUID.randomUUID();
        CiGstReturnPeriod p = CiGstReturnPeriod.builder()
                .id(pid)
                .tenantId(tenantId)
                .gstRegistrationId(reg.getId())
                .returnType(returnType)
                .financialYear("2025-26")
                .periodYyyyMm(yyyyMm)
                .filingStatus(status)
                .effective(true)
                .build();
        periods.add(p);
        fins.put(pid, CiGstPeriodFinancials.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .returnPeriodId(pid)
                .returnType(returnType)
                .taxableTurnover(amt)
                .build());
    }
}
