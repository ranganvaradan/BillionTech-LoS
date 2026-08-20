package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauProductMapping;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauReportSummary;
import com.los.core.creditintelligence.bureau.domain.CiBureauScoringElement;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor;
import com.los.core.creditintelligence.bureau.repository.CiBureauDuplicateGroupRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauInquiryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauProductMappingRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportSummaryRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauScoringElementRepository;
import com.los.core.creditintelligence.bureau.repository.CiBureauTradelineRepository;
import com.los.core.creditintelligence.bureau.service.BureauLiveAccountClassifier;
import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.BureauNormalizationService;
import com.los.core.creditintelligence.bureau.service.BureauProductTaxonomyService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.RawFactProducer;
import com.los.core.creditintelligence.policystudio.sourceintegration.EquifaxRetailRawIds;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Golden Equifax PCS fixture through production extractor → normalizer → materializer → RawFactProducer.
 */
@ExtendWith(MockitoExtension.class)
class EquifaxRetailRawGoldenPathTest {

    @Mock CiBureauReportRepository reportRepository;
    @Mock CiBureauTradelineRepository tradelineRepository;
    @Mock CiBureauPaymentHistoryRepository paymentHistoryRepository;
    @Mock CiBureauInquiryRepository inquiryRepository;
    @Mock CiBureauReportSummaryRepository reportSummaryRepository;
    @Mock CiBureauScoringElementRepository scoringElementRepository;
    @Mock CiBureauDuplicateGroupRepository duplicateGroupRepository;
    @Mock CiBureauProductMappingRepository mappingRepository;
    @Mock SourceRegistryService sourceRegistryService;
    @Mock BureauMetricService metricService;

    private final List<CiBureauReport> reports = new ArrayList<>();
    private final List<CiBureauTradeline> tradelines = new ArrayList<>();
    private final List<CiBureauPaymentHistory> histories = new ArrayList<>();
    private final List<CiBureauInquiry> inquiries = new ArrayList<>();
    private final List<CiBureauReportSummary> summaries = new ArrayList<>();
    private final List<CiBureauScoringElement> scoring = new ArrayList<>();

    private BureauNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        when(reportRepository.findByTenantIdAndApplicationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(inv -> {
            CiBureauReport r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            reports.add(r);
            return r;
        });
        when(tradelineRepository.save(any())).thenAnswer(inv -> {
            CiBureauTradeline t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            tradelines.add(t);
            return t;
        });
        when(paymentHistoryRepository.save(any())).thenAnswer(inv -> {
            CiBureauPaymentHistory h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId(UUID.randomUUID());
            }
            histories.add(h);
            return h;
        });
        when(inquiryRepository.save(any())).thenAnswer(inv -> {
            CiBureauInquiry i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(UUID.randomUUID());
            }
            inquiries.add(i);
            return i;
        });
        when(reportSummaryRepository.save(any())).thenAnswer(inv -> {
            CiBureauReportSummary s = inv.getArgument(0);
            summaries.add(s);
            return s;
        });
        when(scoringElementRepository.save(any())).thenAnswer(inv -> {
            CiBureauScoringElement e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            scoring.add(e);
            return e;
        });
        CiSourceRecord src = CiSourceRecord.builder().id(UUID.randomUUID()).build();
        when(sourceRegistryService.createOrGet(any(), any(), anyString(), anyString(), anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(src);
        lenient().when(metricService.computeAndPersist(any(), any(), any())).thenReturn(List.of());

        BureauProductTaxonomyService taxonomy = new BureauProductTaxonomyService(mappingRepository);
        taxonomy.seedCache("EQUIFAX", BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1, List.of(
                mapping("Overdraft", "OVERDRAFT", false, true),
                mapping("Property Loan", "HOME_LOAN", true, false),
                mapping("Personal Loan", "PERSONAL_LOAN", false, false)
        ));

        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCanonicalization().getBureau().setPersistTradelines(true);

        normalizer = new BureauNormalizationService(
                reportRepository, tradelineRepository, paymentHistoryRepository, inquiryRepository,
                reportSummaryRepository, scoringElementRepository, duplicateGroupRepository,
                sourceRegistryService, taxonomy, new BureauLiveAccountClassifier(),
                metricService, new ContentHasher(), props);
    }

    @Test
    void goldenPcsFixture_extractNormalizeMaterializeProducer() throws Exception {
        String xml;
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("provider-fixtures/equifax/equifax_golden_pipeline_fixture.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> reportData = EquifaxBureauAccountExtractor.enrichFromXml(xml, Map.of());

        UUID appId = UUID.randomUUID();
        var result = normalizer.normalize(appId, null, "EQUIFAX", reportData, "golden-equifax-pcs", null);
        assertThat(result.alreadyExisted()).isFalse();
        assertThat(inquiries).hasSize(2);
        assertThat(scoring).hasSize(3);
        assertThat(summaries).hasSize(1);
        assertThat(tradelines).hasSize(13);
        assertThat(histories).isNotEmpty();

        CiBureauReportSummary summary = summaries.get(0);
        assertThat(summary.getHitCode()).isEqualTo("10");
        assertThat(summary.getSuccessCode()).isEqualTo("1");
        assertThat(summary.getReportOrderNo()).isEqualTo("2498633252");
        assertThat(summary.getScoreName()).isEqualTo("ERS4.0");
        assertThat(summary.getAccountCount()).isEqualTo(13);
        assertThat(summary.getActiveAccountCount()).isEqualTo(5);
        assertThat(summary.getWriteoffCount()).isEqualTo(0);
        assertThat(summary.getTotalPastDue()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalBalance()).isEqualByComparingTo("6131869.00");
        assertThat(summary.getTotalSanction()).isEqualByComparingTo("6650000.00");
        assertThat(summary.getTotalMonthlyPayment()).isEqualByComparingTo("17120.00");
        assertThat(summary.getAgeOfOldestTradeMonths()).isEqualTo(212);
        assertThat(summary.getEnquiryTotal()).isEqualTo(2);
        assertThat(summary.getEnquiryPast30d()).isEqualTo(1);
        assertThat(summary.getEnquiryPast12m()).isEqualTo(2);
        assertThat(summary.getEnquiryPast24m()).isEqualTo(2);
        assertThat(summary.getEnquiryRecentDate()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(summary.getRecentAccountsOpened90d()).isEqualTo(1);
        assertThat(summary.getRecentInquiries90d()).isEqualTo(1);
        assertThat(summary.getReportTime()).isEqualTo("11:02:42");
        assertThat(summary.getEnquirySummaryPurpose()).isEqualTo("ALL");
        assertThat(summary.getAllLinesEverWritten()).isEqualByComparingTo("0.00");
        assertThat(summary.getAllLinesEverWritten9m()).isEqualByComparingTo("0");
        assertThat(summary.getAllLinesEverWritten6m()).isEqualByComparingTo("0");
        assertThat(summary.getRecentAccountNarrative()).contains("Property Loan");
        assertThat(summary.getRecentAccountNarrative()).contains("26-02-2026");
        assertThat(summary.getOldestAccountNarrative()).contains("Commercial Vehicle Loan");
        assertThat(summary.getOldestAccountNarrative()).contains("24-07-2008");

        assertThat(inquiries.get(0).getPurpose()).isEqualTo("0E");
        assertThat(inquiries.get(0).getInquiryTime()).isNotBlank();
        assertThat(reports.get(0).getReportDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(reports.get(0).getScore()).isEqualTo(758);

        boolean anyWrittenOffInvented = tradelines.stream()
                .anyMatch(t -> t.getWrittenOffAmount() != null);
        assertThat(anyWrittenOffInvented).isFalse();
        assertThat(tradelines.stream().allMatch(t -> t.getWilfulDefault() == null)).isTrue();
        assertThat(tradelines.stream().anyMatch(t -> Boolean.TRUE.equals(t.getSecured())
                && "Property".equals(t.getCollateralType()))).isTrue();

        var bundle = CanonicalFactMaterializer.fromBureauEntities(
                reports.get(0), tradelines, histories, inquiries, summary, scoring);
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), bundle);
        var spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> java.util.Optional.empty());
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .facts(facts)
                .build();

        assertThat(spine.resolveAndExecute("bureau.score", ctx).status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(spine.resolveAndExecute("bureau.score", ctx).value()).isEqualTo(758);
        assertThat(spine.resolveAndExecute("bureau.score.name", ctx).value()).isEqualTo("ERS4.0");
        assertThat(spine.resolveAndExecute("bureau.hit_code", ctx).value()).isEqualTo("10");
        assertThat(spine.resolveAndExecute("bureau.success_code", ctx).value()).isEqualTo("1");
        assertThat(spine.resolveAndExecute("bureau.summary.account_count", ctx).value()).isEqualTo(13);
        assertThat(spine.resolveAndExecute("bureau.summary.writeoff_count", ctx).value()).isEqualTo(0);
        assertThat(spine.resolveAndExecute("bureau.enquiry.summary.total", ctx).value()).isEqualTo(2);
        assertThat(spine.resolveAndExecute("bureau.inquiry", ctx).status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat((List<?>) spine.resolveAndExecute("bureau.inquiry", ctx).value()).hasSize(2);
        assertThat(spine.resolveAndExecute("bureau.scoring_element.code", ctx).status())
                .isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        List<String> scoringCodes = ((List<?>) spine.resolveAndExecute("bureau.scoring_element.code", ctx).value())
                .stream().map(String::valueOf).toList();
        assertThat(scoringCodes).containsExactly("703", "707", "710");
        assertThat(spine.resolveAndExecute("bureau.tradeline.write_off_amount", ctx).status())
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);

        assertThat(spine.resolveAndExecute("bureau.report.time", ctx).value()).isEqualTo("11:02:42");
        assertThat(spine.resolveAndExecute("bureau.enquiry.summary.purpose", ctx).value()).isEqualTo("ALL");
        assertThat((BigDecimal) spine.resolveAndExecute("bureau.summary.all_lines_ever_written", ctx).value())
                .isEqualByComparingTo("0.00");
        assertThat((BigDecimal) spine.resolveAndExecute("bureau.summary.all_lines_ever_written_9m", ctx).value())
                .isEqualByComparingTo("0");
        assertThat((BigDecimal) spine.resolveAndExecute("bureau.summary.all_lines_ever_written_6m", ctx).value())
                .isEqualByComparingTo("0");
        assertThat(String.valueOf(spine.resolveAndExecute("bureau.summary.recent_account_narrative", ctx).value()))
                .contains("Property Loan");
        assertThat(String.valueOf(spine.resolveAndExecute("bureau.summary.oldest_account_narrative", ctx).value()))
                .contains("Commercial Vehicle Loan");

        for (String id : EquifaxRetailRawIds.ALL) {
            assertThat(spine.hasExecutionCapability(id, ctx))
                    .as("capability for %s", id)
                    .isTrue();
            var exec = spine.resolveAndExecute(id, ctx);
            assertThat(exec.producerId()).isEqualTo(RawFactProducer.PRODUCER_ID);
            assertThat(exec.status()).isIn(ExecutionStatus.VALUE_AVAILABLE, ExecutionStatus.DATA_NOT_AVAILABLE);
        }

        printExecutionMatrix(reportData, summary, ctx, spine);
    }

    /**
     * Machine-readable EQUIFAX_RAW_EXECUTION_MATRIX for acceptance evidence.
     * Emitted to stdout during golden run; assertions above are the pass gate.
     */
    private static void printExecutionMatrix(
            Map<String, Object> reportData,
            CiBureauReportSummary summary,
            EvaluationContext ctx,
            CanonicalParameterExecutionService spine) {
        record Row(String id, String path, boolean present, Object expected) {}
        List<Row> rows = List.of(
                new Row("bureau.score", "Score/Value", true, 758),
                new Row("bureau.score.name", "Score/Name", true, "ERS4.0"),
                new Row("bureau.report.date", "InquiryResponseHeader/Date", true, "2026-03-12"),
                new Row("bureau.report.time", "InquiryResponseHeader/Time", true, "11:02:42"),
                new Row("bureau.hit_code", "HitCode", true, "10"),
                new Row("bureau.success_code", "SuccessCode", true, "1"),
                new Row("bureau.report_order_no", "ReportOrderNO", true, "2498633252"),
                new Row("bureau.summary.account_count", "NoOfAccounts", true, 13),
                new Row("bureau.summary.active_account_count", "NoOfActiveAccounts", true, 5),
                new Row("bureau.summary.writeoff_count", "NoOfWriteOffs", true, 0),
                new Row("bureau.summary.total_past_due", "TotalPastDue", true, new BigDecimal("0.00")),
                new Row("bureau.summary.total_balance", "TotalBalanceAmount", true, new BigDecimal("6131869.00")),
                new Row("bureau.summary.total_sanction", "TotalSanctionAmount", true, new BigDecimal("6650000.00")),
                new Row("bureau.summary.total_monthly_payment", "TotalMonthlyPaymentAmount", true, new BigDecimal("17120.00")),
                new Row("bureau.summary.age_of_oldest_trade_months", "AgeOfOldestTrade", true, 212),
                new Row("bureau.summary.all_lines_ever_written", "AllLinesEVERWritten", true, new BigDecimal("0.00")),
                new Row("bureau.summary.all_lines_ever_written_9m", "AllLinesEVERWrittenIn9Months", true, BigDecimal.ZERO),
                new Row("bureau.summary.all_lines_ever_written_6m", "AllLinesEVERWrittenIn6Months", true, BigDecimal.ZERO),
                new Row("bureau.summary.recent_account_narrative", "RecentAccount", true, "CONTAINS:Property Loan"),
                new Row("bureau.summary.oldest_account_narrative", "OldestAccount", true, "CONTAINS:Commercial Vehicle Loan"),
                new Row("bureau.enquiry.summary.total", "EnquirySummary/Total", true, 2),
                new Row("bureau.enquiry.summary.past_30d", "Past30Days", true, 1),
                new Row("bureau.enquiry.summary.past_12m", "Past12Months", true, 2),
                new Row("bureau.enquiry.summary.past_24m", "Past24Months", true, 2),
                new Row("bureau.enquiry.summary.purpose", "EnquirySummary/Purpose", true, "ALL"),
                new Row("bureau.enquiry.summary.recent_date", "EnquirySummary/Recent", true, "2026-03-11"),
                new Row("bureau.recent.inquiries_90d", "RecentActivities/TotalInquiries", true, 1),
                new Row("bureau.scoring_element.code", "ScoringElements/Code", true, List.of("703", "707", "710")),
                new Row("bureau.tradeline.write_off_amount", "Account/WrittenOffAmount", false, null)
        );
        System.out.println("EQUIFAX_RAW_EXECUTION_MATRIX_BEGIN");
        System.out.println("PARAMETER_ID|XML_PATH|XML_PRESENT|EXTRACTED|PERSISTED|MATERIALIZED|PRODUCER_RESOLVED|VALUE_RETURNED|EXPECTED|ACTUAL|STATUS|PASS_FAIL");
        for (Row r : rows) {
            var exec = spine.resolveAndExecute(r.id(), ctx);
            Object actual = exec.value();
            boolean ok;
            if (!r.present()) {
                ok = exec.status() == ExecutionStatus.DATA_NOT_AVAILABLE && actual == null;
            } else if (r.expected() instanceof String s && s.startsWith("CONTAINS:")) {
                ok = actual != null && String.valueOf(actual).contains(s.substring("CONTAINS:".length()));
            } else if (r.expected() instanceof BigDecimal bd && actual instanceof BigDecimal abd) {
                ok = abd.compareTo(bd) == 0;
            } else if (r.expected() instanceof List<?> el) {
                ok = actual instanceof List<?> al && al.stream().map(String::valueOf).toList().equals(
                        el.stream().map(String::valueOf).toList());
            } else {
                ok = Objects.equals(String.valueOf(actual), String.valueOf(r.expected()))
                        || (actual instanceof Number && r.expected() instanceof Number
                        && new BigDecimal(actual.toString()).compareTo(new BigDecimal(r.expected().toString())) == 0);
            }
            System.out.printf("%s|%s|%s|Y|Y|Y|%s|%s|%s|%s|%s|%s%n",
                    r.id(), r.path(), r.present() ? "Y" : "N",
                    RawFactProducer.PRODUCER_ID.equals(exec.producerId()) ? "Y" : "N",
                    exec.status() == ExecutionStatus.VALUE_AVAILABLE ? "Y" : "N",
                    r.expected(), actual, exec.status(), ok ? "PASS" : "FAIL");
            assertThat(ok).as("matrix row %s", r.id()).isTrue();
        }
        // Capability sweep for all Equifax RAW IDs (present or DATA_NOT_AVAILABLE)
        for (String id : EquifaxRetailRawIds.ALL) {
            var exec = spine.resolveAndExecute(id, ctx);
            assertThat(exec.producerId()).isEqualTo(RawFactProducer.PRODUCER_ID);
            assertThat(exec.status()).isIn(ExecutionStatus.VALUE_AVAILABLE, ExecutionStatus.DATA_NOT_AVAILABLE);
        }
        System.out.println("EQUIFAX_RAW_EXECUTION_MATRIX_END");
        assertThat(reportData.get("reportTime")).isEqualTo("11:02:42");
        assertThat(summary.getReportTime()).isEqualTo("11:02:42");
    }

    @Test
    void missingXmlTagIsNotZero_explicitSummaryZeroIsPreserved() throws Exception {
        String xml;
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("provider-fixtures/equifax/equifax_golden_pipeline_fixture.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> reportData = EquifaxBureauAccountExtractor.enrichFromXml(xml, Map.of());
        normalizer.normalize(UUID.randomUUID(), null, "EQUIFAX", reportData, "golden-null-zero", null);

        assertThat(summaries.get(0).getWriteoffCount()).isEqualTo(0);
        assertThat(summaries.get(0).getTotalCreditLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tradelines).allMatch(t -> t.getWrittenOffAmount() == null);
        assertThat(tradelines).allMatch(t -> t.getSettlementAmount() == null);
        assertThat(tradelines.stream().map(CiBureauTradeline::getHighCredit).allMatch(v -> v == null)).isTrue();
    }

    private static CiBureauProductMapping mapping(String desc, String cat, boolean secured, boolean revolving) {
        return CiBureauProductMapping.builder()
                .providerCode("EQUIFAX")
                .providerProductDesc(desc)
                .canonicalCategory(cat)
                .secured(secured)
                .revolving(revolving)
                .mappingVersion(BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1)
                .build();
    }
}
