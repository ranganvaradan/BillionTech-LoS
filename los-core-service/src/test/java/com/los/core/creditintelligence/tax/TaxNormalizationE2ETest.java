package com.los.core.creditintelligence.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiSourceArtifact;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import com.los.core.creditintelligence.tax.domain.CiItrReturn;
import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import com.los.core.creditintelligence.tax.domain.CiTaxReturnRevision;
import com.los.core.creditintelligence.tax.domain.TaxMetricOutcome;
import com.los.core.creditintelligence.tax.repository.CiAisInformationRepository;
import com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsEntryRepository;
import com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiItrBusinessFinancialsRepository;
import com.los.core.creditintelligence.tax.repository.CiItrIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrPresumptiveIncomeRepository;
import com.los.core.creditintelligence.tax.repository.CiItrReturnRepository;
import com.los.core.creditintelligence.tax.repository.CiItrTaxSummaryRepository;
import com.los.core.creditintelligence.tax.repository.CiTaxReturnRevisionRepository;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import com.los.core.creditintelligence.tax.service.TaxNormalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Fixture JSON from ItrReturnFormsMapperTest → TaxNormalizationService.
 */
@ExtendWith(MockitoExtension.class)
class TaxNormalizationE2ETest {

    @Mock private CiItrReturnRepository returnRepository;
    @Mock private CiItrIncomeRepository incomeRepository;
    @Mock private CiItrBusinessFinancialsRepository businessRepository;
    @Mock private CiItrPresumptiveIncomeRepository presumptiveRepository;
    @Mock private CiItrTaxSummaryRepository taxSummaryRepository;
    @Mock private CiTaxReturnRevisionRepository revisionRepository;
    @Mock private CiAisSummaryRepository aisSummaryRepository;
    @Mock private CiAisInformationRepository aisInformationRepository;
    @Mock private CiForm26AsSummaryRepository form26AsSummaryRepository;
    @Mock private CiForm26AsEntryRepository form26AsEntryRepository;
    @Mock private SourceRegistryService sourceRegistryService;
    @Mock private CiMetricResultRepository metricResultRepository;

    private TaxNormalizationService normalizationService;
    private final List<CiItrReturn> savedReturns = new ArrayList<>();
    private final List<CiMetricResult> savedMetrics = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCanonicalization().getTax().setPersistDetail(true);

        TaxMetricService metricService = new TaxMetricService(metricResultRepository, props);
        normalizationService = new TaxNormalizationService(
                returnRepository, incomeRepository, businessRepository, presumptiveRepository,
                taxSummaryRepository, revisionRepository, aisSummaryRepository, aisInformationRepository,
                form26AsSummaryRepository, form26AsEntryRepository,
                sourceRegistryService, metricService, new ContentHasher(), props);

        when(returnRepository.findByTenantIdAndApplicationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(returnRepository.findByApplicationIdAndSubjectScopeAndAssessmentYearAndEffectiveTrue(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(returnRepository.save(any())).thenAnswer(inv -> {
            CiItrReturn r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            savedReturns.add(r);
            return r;
        });
        when(incomeRepository.save(any())).thenAnswer(inv -> {
            CiItrIncome i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(businessRepository.save(any())).thenAnswer(inv -> {
            CiItrBusinessFinancials b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        lenient().when(presumptiveRepository.save(any())).thenAnswer(inv -> {
            CiItrPresumptiveIncome p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        lenient().when(taxSummaryRepository.save(any())).thenAnswer(inv -> {
            CiItrTaxSummary t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        when(revisionRepository.save(any())).thenAnswer(inv -> {
            CiTaxReturnRevision r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(metricResultRepository.save(any())).thenAnswer(inv -> {
            CiMetricResult m = inv.getArgument(0);
            savedMetrics.add(m);
            return m;
        });

        CiSourceRecord src = CiSourceRecord.builder().id(UUID.randomUUID()).build();
        when(sourceRegistryService.createOrGet(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(src);
        when(sourceRegistryService.createArtifact(any(), any(), any(), any()))
                .thenReturn(CiSourceArtifact.builder().id(UUID.randomUUID()).build());
    }

    @Test
    void normalizesMapperFixture_multiYearTurnoverAndDiVariance() throws Exception {
        String json = """
                {
                  "formDetails": { "assessmentYear": "2025-26", "formName": "ITR-6" },
                  "generalInformation": { "entityName": "ACME PVT LTD", "entityPan": "AAHCB0052H" },
                  "financialInformation": [
                    {
                      "assessmentYear": "2024-25",
                      "financialYear": "2023-24",
                      "profitAndLoss": { "totalRevenue": 9000, "profitAfterTax": -100, "ebitda": 50, "interestExpense": 10 },
                      "balanceSheet": { "totalLiability": 1, "totalEquity": 2 }
                    },
                    {
                      "assessmentYear": "2025-26",
                      "financialYear": "2024-25",
                      "profitAndLoss": {
                        "totalRevenue": 28921624,
                        "profitAfterTax": 1026039,
                        "ebitda": 8620512,
                        "interestExpense": 7498893
                      },
                      "balanceSheet": { "totalLiability": 200309002, "totalEquity": 68162682 }
                    }
                  ],
                  "itrFilled": [
                    { "annualYear": "2025-26", "fillingDate": "2025-10-22", "itrForm": "ITR-6", "pan": "AAHCB0052H" }
                  ]
                }
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(json, Map.class);
        UUID appId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        var out = normalizationService.normalize(
                appId, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Map.of("result", result, "requestId", "req-1", "mappedMetrics", Map.of("itrIncome", 28921624)),
                "req-1",
                stepId);

        assertThat(out.alreadyExisted()).isFalse();
        assertThat(savedReturns.stream().anyMatch(CiItrReturn::isEffective)).isTrue();
        assertThat(savedReturns.stream()
                .filter(CiItrReturn::isEffective)
                .anyMatch(r -> "2025-26".equals(r.getAssessmentYear()))).isTrue();
        assertThat(savedReturns.stream()
                .filter(r -> r.getSourceReference() != null)
                .anyMatch(r -> r.getSourceReference().equals("kyc_step_result:" + stepId))).isTrue();
        assertThat(savedReturns.stream().noneMatch(r -> r.getMetadata() != null
                && String.valueOf(r.getMetadata()).toLowerCase().contains("password"))).isTrue();

        CiMetricResult turnover = savedMetrics.stream()
                .filter(m -> TaxMetricService.TURNOVER_LATEST.equals(m.getMetricCode()))
                .findFirst().orElseThrow();
        assertThat(turnover.getOutcome()).isEqualTo(TaxMetricOutcome.PASS.name());
        assertThat(String.valueOf(turnover.getValue().get("v"))).isEqualTo("28921624");

        assertThat(savedMetrics.stream()
                .filter(m -> TaxMetricService.XSRC_AIS_INCOME.equals(m.getMetricCode()))
                .findFirst().orElseThrow().getOutcome())
                .isEqualTo(TaxMetricOutcome.DATA_INSUFFICIENT.name());
    }
}
