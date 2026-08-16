package com.los.core.architecture.regression;

import com.los.core.creditintelligence.bureau.domain.CiBureauInquiry;
import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.domain.CiBureauTradeline;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBankingMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalCompatibilityRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.execution.ProducerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAVE-3 — canonical materialization, exact-ID boundary, collection contracts,
 * banking fixture removal, provenance, explicit asOf. No production authored defs.
 */
class Wave3CanonicalMaterializationTest {

    private static final String SYN_PH_COUNT = "fixture.wave3.ph_dpd30_distinct_month_count";
    private static final String SYN_TL_SUM = "fixture.wave3.cc_overdue_sum";

    private CanonicalParameterExecutionService spine;

    @BeforeEach
    void setUp() {
        Map<String, Object> phExpr = Map.of(
                "op", "COUNT",
                "of", Map.of(
                        "op", "DISTINCT",
                        "of", Map.of(
                                "op", "PROJECT",
                                "field", "month",
                                "from", Map.of(
                                        "op", "FILTER",
                                        "from", Map.of("op", "REF", "id", CanonicalFactMaterializer.PAYMENT_HISTORY),
                                        "where", Map.of(
                                                "op", "GTE",
                                                "left", Map.of("op", "FIELD", "field", "dpd"),
                                                "right", Map.of("op", "CONST", "value", 30))))));
        CiGacatDerivedCalculationDefinition phDef = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("cccc0000-0000-4000-8000-000000000031"))
                .canonicalParameterId(SYN_PH_COUNT)
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(phExpr)
                .dependencyIds(List.of(CanonicalFactMaterializer.PAYMENT_HISTORY))
                .versionNo(1)
                .build();

        Map<String, Object> tlExpr = Map.of(
                "op", "SUM",
                "of", Map.of(
                        "op", "PROJECT",
                        "field", "overdue_amount",
                        "from", Map.of(
                                "op", "FILTER",
                                "from", Map.of("op", "REF", "id", CanonicalFactMaterializer.TRADELINES),
                                "where", Map.of(
                                        "op", "EQ",
                                        "left", Map.of("op", "FIELD", "field", "account_type"),
                                        "right", Map.of("op", "CONST", "value", "CREDIT_CARD")))));
        CiGacatDerivedCalculationDefinition tlDef = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("dddd0000-0000-4000-8000-000000000032"))
                .canonicalParameterId(SYN_TL_SUM)
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(tlExpr)
                .dependencyIds(List.of(CanonicalFactMaterializer.TRADELINES))
                .versionNo(1)
                .build();

        spine = ExecutionSpineProducerBootstrap.standalone((id, t) -> {
            if (SYN_PH_COUNT.equals(id)) return Optional.of(phDef);
            if (SYN_TL_SUM.equals(id)) return Optional.of(tlDef);
            return Optional.empty();
        });
        ExecutionCapabilityAuthority.install(spine);
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void exactCanonicalIdProjection_fromLegacyRemaps() {
        Map<String, Object> remapped = new LinkedHashMap<>();
        remapped.put("bureau.consumer.score", 745);
        remapped.put("bureau.dpd.max_6m", 12);
        remapped.put("bureau.inquiries.count_90d", 3);
        remapped.put("banking.balance.average_3m", new BigDecimal("50000"));
        remapped.put("compat.BUREAU_ENQUIRIES_3M", 9); // dangerous — must not become recent_inquiries_90d

        Map<String, Object> exact = CanonicalCompatibilityRegistry.projectExactCanonicalFacts(remapped);
        assertThat(exact).containsEntry("bureau.score", 745);
        assertThat(exact).containsEntry("bureau.max_dpd_6m", 12);
        assertThat(exact).containsEntry("bureau.recent_inquiries_90d", 3);
        assertThat(exact).containsEntry("banking.avg_daily_balance_3m", new BigDecimal("50000"));
        assertThat(exact.get("bureau.recent_inquiries_90d")).isEqualTo(3);
        assertThat(CanonicalCompatibilityRegistry.exactCanonicalIdForPath("compat.BUREAU_ENQUIRIES_3M"))
                .isNull();
    }

    @Test
    void paymentHistory_missingVsEmpty() {
        CiBureauReport absentReport = null;
        var absent = CanonicalFactMaterializer.fromBureauEntities(absentReport, List.of(), List.of(), List.of());
        Map<String, Object> factsAbsent = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), absent);
        assertThat(factsAbsent).doesNotContainKey(CanonicalFactMaterializer.PAYMENT_HISTORY);

        CiBureauReport report = CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tradelinesPresent(true)
                .reportDate(LocalDate.of(2026, 6, 15))
                .providerCode("EQUIFAX")
                .build();
        var emptyPh = CanonicalFactMaterializer.fromBureauEntities(report, List.of(
                sampleTradeline(UUID.randomUUID(), "HOME_LOAN", BigDecimal.ZERO)
        ), List.of(), List.of());
        Map<String, Object> factsEmpty = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), emptyPh);
        assertThat(factsEmpty).containsKey(CanonicalFactMaterializer.PAYMENT_HISTORY);
        assertThat((List<?>) factsEmpty.get(CanonicalFactMaterializer.PAYMENT_HISTORY)).isEmpty();

        ExecutionResult missing = spine.resolveAndExecute(
                CanonicalFactMaterializer.PAYMENT_HISTORY,
                EvaluationContext.builder().mode(EvaluationMode.UNDERWRITING).build());
        assertThat(missing.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(missing.capability()).isTrue();

        ExecutionResult empty = spine.resolveAndExecute(
                CanonicalFactMaterializer.PAYMENT_HISTORY,
                EvaluationContext.builder()
                        .mode(EvaluationMode.UNDERWRITING)
                        .fact(CanonicalFactMaterializer.PAYMENT_HISTORY, List.of())
                        .evaluationAsOf(LocalDate.of(2026, 6, 15))
                        .build());
        assertThat(empty.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat((List<?>) empty.value()).isEmpty();
    }

    @Test
    void tradelineAndInquiry_materializationContracts() {
        UUID tlId = UUID.randomUUID();
        CiBureauReport report = CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tradelinesPresent(true)
                .reportDate(LocalDate.of(2026, 6, 15))
                .providerCode("EQUIFAX")
                .build();
        CiBureauTradeline tl = sampleTradeline(tlId, "CREDIT_CARD", new BigDecimal("2500"));
        tl.setSuitFiled(true);
        tl.setAccountStatus("ACTIVE");
        tl.setSecured(false);
        CiBureauPaymentHistory ph = CiBureauPaymentHistory.builder()
                .id(UUID.randomUUID())
                .tradelineId(tlId)
                .month(LocalDate.of(2026, 3, 1))
                .dpd(45)
                .status("DPD")
                .build();
        CiBureauInquiry inq = CiBureauInquiry.builder()
                .id(UUID.randomUUID())
                .inquiryDate(LocalDate.of(2026, 5, 1))
                .memberName("BANK")
                .purpose("PL")
                .build();

        var bundle = CanonicalFactMaterializer.fromBureauEntities(report, List.of(tl), List.of(ph), List.of(inq));
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(
                Map.of("bureau.consumer.score", 720), bundle);

        assertThat(facts).containsKey("bureau.score");
        assertThat(facts).containsKey(CanonicalFactMaterializer.TRADELINES);
        assertThat(facts).containsKey(CanonicalFactMaterializer.PAYMENT_HISTORY);
        assertThat(facts).containsKey(CanonicalFactMaterializer.INQUIRIES);

        @SuppressWarnings("unchecked")
        Map<String, Object> tlRow = ((List<Map<String, Object>>) facts.get(CanonicalFactMaterializer.TRADELINES)).get(0);
        assertThat(tlRow.get("account_type")).isEqualTo("CREDIT_CARD");
        assertThat(tlRow.get("overdue_amount")).isEqualTo(new BigDecimal("2500"));
        assertThat(tlRow.get("suit_filed")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> phRow = ((List<Map<String, Object>>) facts.get(CanonicalFactMaterializer.PAYMENT_HISTORY)).get(0);
        assertThat(phRow.get("month")).isEqualTo("2026-03");
        assertThat(phRow.get("dpd")).isEqualTo(45);
        assertThat(phRow.get("tradelineId")).isEqualTo(tlId.toString());
    }

    @Test
    void bankingFixture_refusedOutsidePolicyTest() {
        BuiltInBankingMetricProducer banking = new BuiltInBankingMetricProducer();
        EvaluationContext uw = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .entity(BuiltInBankingMetricProducer.ENTITY_EMI_ENABLED, true)
                .entity(BuiltInBankingMetricProducer.ENTITY_ADB_ENABLED, true)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult emiUw = banking.execute(BuiltInBankingMetricProducer.EMI_BOUNCE, uw, (id, c) -> null);
        assertThat(emiUw.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(emiUw.capability()).isTrue();
        assertThat(String.valueOf(emiUw.reason())).contains("POLICY_TEST");

        ExecutionResult adbUw = banking.execute(BuiltInBankingMetricProducer.ADB_3M, uw, (id, c) -> null);
        assertThat(adbUw.status()).isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);

        EvaluationContext w6 = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .entity(BuiltInBankingMetricProducer.ENTITY_EMI_ENABLED, true)
                .build();
        assertThat(banking.execute(BuiltInBankingMetricProducer.EMI_BOUNCE, w6, (id, c) -> null).status())
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);

        EvaluationContext pt = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .entity(BuiltInBankingMetricProducer.ENTITY_EMI_ENABLED, true)
                .evaluationAsOf(LocalDate.of(2026, 8, 1))
                .build();
        ExecutionResult emiPt = banking.execute(BuiltInBankingMetricProducer.EMI_BOUNCE, pt, (id, c) -> null);
        assertThat(emiPt.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(emiPt.provenance()).containsEntry("fixtureAuthority", "POLICY_TEST_ONLY");
    }

    @Test
    void evaluationAsOf_neverInventedFromWallClock() {
        assertThat(CanonicalFactMaterializer.resolveEvaluationAsOf(null, null)).isNull();
        CiBureauReport report = CiBureauReport.builder()
                .reportDate(LocalDate.of(2026, 1, 10))
                .build();
        assertThat(CanonicalFactMaterializer.resolveEvaluationAsOf(null, report))
                .isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(CanonicalFactMaterializer.resolveEvaluationAsOf(LocalDate.of(2026, 2, 1), report))
                .isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void cpes_exactIdResolution_andRawProvenance() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 6, 15))
                .fact("bureau.score", 710)
                .fact(CanonicalFactMaterializer.PAYMENT_HISTORY, List.of(
                        Map.of("month", "2026-01", "dpd", 0)))
                .entity(CanonicalFactMaterializer.ENTITY_BUREAU_REPORT_ID, "report-1")
                .entity(CanonicalFactMaterializer.ENTITY_SOURCE_SNAPSHOT_VERSION, "snap-1")
                .build();

        ExecutionResult score = spine.resolveAndExecute("bureau.score", ctx);
        assertThat(score.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(score.value()).isEqualTo(710);
        assertThat(score.producerType()).isEqualTo(ProducerType.RAW);
        assertThat(score.provenance()).containsEntry("bureauReportId", "report-1");
        assertThat(score.provenance()).containsEntry("sourceSnapshotVersion", "snap-1");
    }

    @Test
    void realPaymentHistory_genericDsl_viaMaterializerAndCpes() {
        UUID tlId = UUID.randomUUID();
        CiBureauReport report = CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tradelinesPresent(true)
                .reportDate(LocalDate.of(2026, 6, 30))
                .build();
        List<CiBureauPaymentHistory> histories = List.of(
                ph(tlId, LocalDate.of(2026, 1, 1), 0),
                ph(tlId, LocalDate.of(2026, 2, 1), 35),
                ph(tlId, LocalDate.of(2026, 3, 1), 60),
                ph(tlId, LocalDate.of(2026, 3, 1), 45), // same month — DISTINCT month
                ph(tlId, LocalDate.of(2026, 4, 1), 0),
                ph(tlId, LocalDate.of(2026, 5, 1), 30));
        var bundle = CanonicalFactMaterializer.fromBureauEntities(
                report, List.of(sampleTradeline(tlId, "PERSONAL_LOAN", BigDecimal.ZERO)), histories, List.of());
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), bundle);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(LocalDate.of(2026, 6, 30));
        facts.forEach(b::fact);
        EvaluationContext execCtx = b.build();

        ExecutionResult phRaw = spine.resolveAndExecute(CanonicalFactMaterializer.PAYMENT_HISTORY, execCtx);
        assertThat(phRaw.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(phRaw.provenance().get("rowCount")).isEqualTo(6);

        ExecutionResult derived = spine.resolveAndExecute(SYN_PH_COUNT, execCtx);
        assertThat(derived.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(((Number) derived.value()).longValue()).isEqualTo(3L);
        assertThat(derived.provenance().get("operatorChain")).asList()
                .contains("FILTER", "DISTINCT", "COUNT");
        assertThat(derived.provenance().get("collectionInputs")).isNotNull();
    }

    @Test
    void realTradelines_genericDsl_viaMaterializerAndCpes() {
        CiBureauReport report = CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tradelinesPresent(true)
                .reportDate(LocalDate.of(2026, 6, 15))
                .build();
        List<CiBureauTradeline> tls = List.of(
                sampleTradeline(UUID.randomUUID(), "HOME_LOAN", new BigDecimal("10000")),
                sampleTradeline(UUID.randomUUID(), "CREDIT_CARD", new BigDecimal("5000")),
                sampleTradeline(UUID.randomUUID(), "CREDIT_CARD", new BigDecimal("2500")),
                sampleTradeline(UUID.randomUUID(), "PERSONAL_LOAN", new BigDecimal("3000")));
        var bundle = CanonicalFactMaterializer.fromBureauEntities(report, tls, List.of(), List.of());
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(Map.of(), bundle);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .evaluationAsOf(LocalDate.of(2026, 6, 15));
        facts.forEach(b::fact);
        EvaluationContext execCtx = b.build();

        ExecutionResult derived = spine.resolveAndExecute(SYN_TL_SUM, execCtx);
        assertThat(derived.status()).isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(((Number) derived.value()).doubleValue()).isEqualTo(7500.0d);
    }

    @Test
    void acquisitionSuccessDoesNotImplyValue_whenHistoryMissing() {
        // Report acquired, but PH omitted (tradelinesPresent=false, no rows)
        CiBureauReport report = CiBureauReport.builder()
                .id(UUID.randomUUID())
                .tradelinesPresent(false)
                .reportDate(LocalDate.of(2026, 6, 15))
                .build();
        var bundle = CanonicalFactMaterializer.fromBureauEntities(report, List.of(), List.of(), List.of());
        Map<String, Object> facts = CanonicalFactMaterializer.materializeExecutionFacts(
                Map.of("bureau.score", 700), bundle);
        assertThat(facts).containsKey(CanonicalFactMaterializer.TRADELINES);
        assertThat(facts).doesNotContainKey(CanonicalFactMaterializer.PAYMENT_HISTORY);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .entity("acquisitionSuccessDoesNotImplyValueAvailable", true)
                .evaluationAsOf(LocalDate.of(2026, 6, 15));
        facts.forEach(b::fact);
        EvaluationContext ctx = b.build();

        assertThat(spine.resolveAndExecute("bureau.score", ctx).status())
                .isEqualTo(ExecutionStatus.VALUE_AVAILABLE);
        assertThat(spine.resolveAndExecute(CanonicalFactMaterializer.PAYMENT_HISTORY, ctx).status())
                .isEqualTo(ExecutionStatus.DATA_NOT_AVAILABLE);
        assertThat(spine.resolveAndExecute(SYN_PH_COUNT, ctx).status())
                .isEqualTo(ExecutionStatus.DEPENDENCY_NOT_AVAILABLE);
    }

    @Test
    void dangerousAliasInventory_recordedNotReversed() {
        List<CanonicalCompatibilityRegistry.AliasEntry> dangerous = CanonicalCompatibilityRegistry.allEntries()
                .stream()
                .filter(e -> e.classification() == CanonicalCompatibilityRegistry.AliasClass.DANGEROUS_ALIAS_REJECTED)
                .toList();
        assertThat(dangerous).isNotEmpty();
        assertThat(dangerous).anyMatch(e -> e.aliasOrRemap().contains("ENQUIRIES_3M"));
    }

    private static CiBureauTradeline sampleTradeline(UUID id, String category, BigDecimal overdue) {
        return CiBureauTradeline.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .bureauReportId(UUID.randomUUID())
                .productCategory(category)
                .accountTypeRaw(category)
                .overdueAmount(overdue)
                .currentBalance(overdue)
                .build();
    }

    private static CiBureauPaymentHistory ph(UUID tlId, LocalDate month, int dpd) {
        return CiBureauPaymentHistory.builder()
                .id(UUID.randomUUID())
                .tradelineId(tlId)
                .month(month)
                .dpd(dpd)
                .status(dpd > 0 ? "DPD" : "OK")
                .build();
    }
}
