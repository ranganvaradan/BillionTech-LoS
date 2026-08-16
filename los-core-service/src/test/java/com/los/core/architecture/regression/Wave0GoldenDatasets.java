package com.los.core.architecture.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.policystudio.parameters.derived.BusinessCalculationAssistant;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic golden datasets + EvaluationContext builders for Wave 0.
 * Fixed POLICY_TEST asOf = 2026-08-01 (documents W6 now() separately as KNOWN_GAP).
 */
public final class Wave0GoldenDatasets {

    public static final LocalDate POLICY_TEST_AS_OF = LocalDate.of(2026, 8, 1);
    public static final LocalDate DSL_CLOCK_DATE = LocalDate.of(2024, 6, 15);
    public static final String VIKASAM_POLICY_ID = "4543e643-c3a0-4a57-a92c-370dff8b2fa9";

    public static final List<String> VIKASAM_13 = List.of(
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Wave0GoldenDatasets() {}

    public static JsonNode loadResource(String relativePath) {
        String path = "architecture-regression/" + relativePath;
        try (InputStream in = Wave0GoldenDatasets.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return MAPPER.readTree(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load " + path, ex);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Full golden context: bureau + banking + gst + app + kyc facts for spine execution. */
    public static EvaluationContext fullGoldenContext(EvaluationMode mode) {
        JsonNode bureau = loadResource("datasets/bureau-retail-golden.json");
        JsonNode banking = loadResource("datasets/banking-golden.json");
        JsonNode gst = loadResource("datasets/gst-golden.json");
        JsonNode app = loadResource("datasets/application-golden.json");
        JsonNode kyc = loadResource("datasets/kyc-golden.json");

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(POLICY_TEST_AS_OF);

        b.fact("bureau.score", bureau.path("score").asInt());
        b.fact("bureau.tradeline.suit_filed", 1);
        b.fact("bureau.tradeline.payment_history", paymentHistoryList(bureau.path("paymentHistory")));

        putPrecomputed(b, bureau.path("precomputedMetrics"));
        putPrecomputed(b, banking.path("precomputedMetrics"));
        putPrecomputed(b, gst.path("precomputedMetrics"));

        JsonNode appFacts = app.path("facts");
        Iterator<String> appFields = appFacts.fieldNames();
        while (appFields.hasNext()) {
            String k = appFields.next();
            b.fact(k, numericOrText(appFacts.get(k)));
        }

        JsonNode panCase = kyc.path("cases").get(0).path("facts");
        Iterator<String> kycFields = panCase.fieldNames();
        while (kycFields.hasNext()) {
            String k = kycFields.next();
            b.fact(k, numericOrText(panCase.get(k)));
        }

        JsonNode itr = loadResource("datasets/itr-tax-golden.json");
        Iterator<String> itrFields = itr.path("facts").fieldNames();
        while (itrFields.hasNext()) {
            String k = itrFields.next();
            b.fact(k, numericOrText(itr.path("facts").get(k)));
        }

        return b.build();
    }

    /** Context without payment_history — materialization gap case. */
    public static EvaluationContext contextWithoutPaymentHistory(EvaluationMode mode) {
        EvaluationContext full = fullGoldenContext(mode);
        Map<String, Object> facts = new LinkedHashMap<>(full.facts());
        facts.remove("bureau.tradeline.payment_history");
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(POLICY_TEST_AS_OF);
        for (Map.Entry<String, Object> e : facts.entrySet()) {
            b.fact(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : full.inputs().entrySet()) {
            b.input(e.getKey(), e.getValue());
        }
        return b.build();
    }

    public static List<Map<String, Object>> paymentHistoryList(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", n.path("month").asText());
            row.put("dpd", n.path("dpd").asInt());
            out.add(row);
        }
        return out;
    }

    private static void putPrecomputed(EvaluationContext.Builder b, JsonNode metrics) {
        if (metrics == null || !metrics.isObject()) {
            return;
        }
        Iterator<String> fields = metrics.fieldNames();
        while (fields.hasNext()) {
            String k = fields.next();
            b.fact(k, numericOrText(metrics.get(k)));
        }
    }

    private static Object numericOrText(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return n.asLong();
        }
        if (n.isFloatingPointNumber()) {
            return n.asDouble();
        }
        return n.asText();
    }

    /**
     * Authored defs for Wave 0 unit baseline:
     * - clean_history: valid MONTHS_SINCE
     * - dpd_30: intentionally INVALID MONTHS_SINCE on COUNT intent (documents CURRENT unit-test reality;
     *   Client DB may differ — classified KNOWN_GAP / environment-dependent)
     */
    public static AuthoredDerivedProducerSource wave0AuthoredDefinitions() {
        Map<String, CiGacatDerivedCalculationDefinition> byId = new LinkedHashMap<>();

        Map<String, Object> cleanExpr = BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                BusinessCalculationAssistant.HISTORY_PAYMENT, 0);
        byId.put("bureau.credit_after_overdue.clean_history_months",
                CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("7d06dd5c-0000-4000-8000-000000000001"))
                        .canonicalParameterId("bureau.credit_after_overdue.clean_history_months")
                        .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                        .expressionJson(cleanExpr)
                        .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                        .versionNo(1)
                        .build());

        Map<String, Object> badDpd = Map.of(
                "op", "MONTHS_SINCE_LAST_MATCH",
                "history", Map.of("op", "REF", "id", BusinessCalculationAssistant.HISTORY_PAYMENT),
                "matchField", "dpd",
                "matchOp", "GTE",
                "matchValue", 30,
                "dateField", "month",
                "asOf", Map.of("op", "EVAL_AS_OF"));
        byId.put("bureau.dpd_30_plus_count_6m",
                CiGacatDerivedCalculationDefinition.builder()
                        .id(UUID.fromString("dd0218c5-0000-4000-8000-000000000001"))
                        .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                        .status(DerivedCalculationDefinitionService.STATUS_PRODUCTION_READY)
                        .expressionJson(badDpd)
                        .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                        .versionNo(1)
                        .build());

        return (id, tenantId) -> Optional.ofNullable(byId.get(id));
    }

    /** Correct COUNT_PERIODS def for optional comparison (not default Wave 0 spine). */
    public static CiGacatDerivedCalculationDefinition correctDpd30Definition() {
        Map<String, Object> expr = BusinessCalculationAssistant.countPeriodsMatchingExpression(
                BusinessCalculationAssistant.HISTORY_PAYMENT, "GTE", 30, 6);
        return CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.fromString("d3a3c150-0000-4000-8000-000000000002"))
                .canonicalParameterId("bureau.dpd_30_plus_count_6m")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(expr)
                .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                .versionNo(2)
                .build();
    }

    @FunctionalInterface
    public interface AuthoredDerivedProducerSource
            extends com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer.AuthoredDefinitionSource {
    }
}
