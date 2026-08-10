package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxMetricServiceTest {

    @Mock
    private CiMetricResultRepository metricResultRepository;

    private TaxMetricService service;
    private final List<CiMetricResult> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        service = new TaxMetricService(metricResultRepository, props);
        when(metricResultRepository.save(any())).thenAnswer(inv -> {
            CiMetricResult m = inv.getArgument(0);
            saved.add(m);
            return m;
        });
    }

    @Test
    void turnoverLatestAndCagrDiWhenInsufficientYears() {
        UUID tenant = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        UUID src = UUID.randomUUID();
        var bundles = List.of(bundle("2025-26", "2024-25", "28921624", "1026039", "8620512",
                "200309002", "68162682", false));

        service.computeAndPersist(tenant, app, src, bundles,
                new TaxMetricService.CrossSourceContext(List.of(), List.of()),
                LocalDate.of(2026, 8, 1));

        CiMetricResult latest = find(TaxMetricService.TURNOVER_LATEST);
        assertThat(latest.getOutcome()).isEqualTo(TaxMetricOutcome.PASS.name());
        assertThat(latest.getValue().get("v")).isEqualTo(new BigDecimal("28921624"));

        assertThat(find(TaxMetricService.TURNOVER_CAGR_2Y).getOutcome())
                .isEqualTo(TaxMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(find(TaxMetricService.XSRC_AIS_INCOME).getOutcome())
                .isEqualTo(TaxMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(find(TaxMetricService.XSRC_26AS_TDS).getOutcome())
                .isEqualTo(TaxMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(find(TaxMetricService.PAT_ABS).getOutcome()).isEqualTo(TaxMetricOutcome.PASS.name());
        assertThat(find(TaxMetricService.EBITDA_MARGIN).getOutcome()).isEqualTo(TaxMetricOutcome.PASS.name());
    }

    @Test
    void presumptive_marginsAndBsNotApplicable() {
        UUID tenant = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        var bundles = List.of(bundle("2025-26", "2024-25", "500000", null, null, null, null, true));
        service.computeAndPersist(tenant, app, UUID.randomUUID(), bundles,
                new TaxMetricService.CrossSourceContext(List.of(), List.of()), LocalDate.now());

        assertThat(find(TaxMetricService.EBITDA_MARGIN).getOutcome())
                .isEqualTo(TaxMetricOutcome.NOT_APPLICABLE.name());
        assertThat(find(TaxMetricService.TOL_TNW).getOutcome())
                .isEqualTo(TaxMetricOutcome.NOT_APPLICABLE.name());
        assertThat(find(TaxMetricService.PAT_ABS).getOutcome())
                .isEqualTo(TaxMetricOutcome.NOT_APPLICABLE.name());
    }

    private CiMetricResult find(String code) {
        return saved.stream().filter(m -> code.equals(m.getMetricCode())).findFirst().orElseThrow();
    }

    private TaxMetricService.ReturnBundle bundle(
            String ay, String fy, String turnover, String pat, String ebitda,
            String tol, String tnw, boolean presumptive) {
        CiItrReturn r = CiItrReturn.builder()
                .id(UUID.randomUUID())
                .assessmentYear(ay)
                .financialYear(fy)
                .itrForm(presumptive ? "ITR_4" : "ITR_6")
                .effective(true)
                .build();
        CiItrIncome income = CiItrIncome.builder()
                .businessProfessionIncome(new BigDecimal(turnover))
                .totalIncome(new BigDecimal(turnover))
                .grossTotalIncome(new BigDecimal(turnover))
                .build();
        CiItrBusinessFinancials biz = CiItrBusinessFinancials.builder()
                .salesTurnover(new BigDecimal(turnover))
                .grossReceipts(new BigDecimal(turnover))
                .profitAfterTax(pat != null ? new BigDecimal(pat) : null)
                .ebitda(ebitda != null ? new BigDecimal(ebitda) : null)
                .totalLiabilities(tol != null ? new BigDecimal(tol) : null)
                .netWorth(tnw != null ? new BigDecimal(tnw) : null)
                .build();
        return new TaxMetricService.ReturnBundle(
                r, income, biz,
                presumptive ? List.of(com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome.builder()
                        .applicableSection("S_44AD").build()) : List.of(),
                null);
    }
}
