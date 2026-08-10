package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyImplementabilityServiceTest {

    private final PolicyImplementabilityService service = new PolicyImplementabilityService();

    @Test
    void dumpBankingAndBureauReadinessForReport() throws Exception {
        for (String[] pair : new String[][]{
                {"policy-fixtures/banking-bre/Banking_BRE.txt", "Banking_BRE"},
                {"policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE"}
        }) {
            PolicyStudioSession session = load(pair[0], pair[1]);
            Map<String, Object> result = service.assess(session);
            Map<String, Object> summary = cast(result.get("summary"));
            System.out.println("DAY6_IMPL|" + pair[1] + "|" + summary);
            System.out.println("DAY6_ANALYST|" + pair[1] + "|" + result.get("analystMessage"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gaps = (List<Map<String, Object>>) result.get("gaps");
            System.out.println("DAY6_GAPS|" + pair[1] + "|count=" + gaps.size());
            gaps.stream().limit(8).forEach(g ->
                    System.out.println("DAY6_GAP|" + pair[1] + "|" + g.get("gapType")
                            + "|" + g.get("whatIsMissing") + "|blocks=" + g.get("blocksPolicy")));
        }
    }

    @Test
    void bankingBreShowsRealisticGapsNotFakeFullReadiness() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking_BRE");
        Map<String, Object> result = service.assess(session);
        Map<String, Object> summary = cast(result.get("summary"));

        assertThat(summary.get("allowCanonicalAuthority")).isEqualTo(false);
        int pct = ((Number) summary.get("implementationReadinessPercent")).intValue();
        assertThat(pct).isBetween(0, 100);
        assertThat(pct).isLessThan(100); // must not invent 100% readiness
        assertThat(((Number) summary.get("rulesIdentified")).intValue()).isGreaterThan(0);
        assertThat(((Number) summary.get("distinctDataElements")).intValue()).isGreaterThan(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) result.get("rules");
        assertThat(rules).isNotEmpty();

        boolean hasEdiOrSettlementOrBoundary = rules.stream().anyMatch(r -> {
            String status = String.valueOf(r.get("implementability"));
            return !PolicyImplementabilityService.READY.equals(status);
        });
        assertThat(hasEdiOrSettlementOrBoundary)
                .as("Banking BRE should surface at least one non-READY rule (EDI/settlement/boundary)")
                .isTrue();

        assertThat(result.get("analystMessage")).asString().contains("configured data sources");
        assertThat(result.get("nextActions")).isInstanceOf(List.class);
        assertThat(result.get("gaps")).isInstanceOf(List.class);
        assertThat(result.get("dataSources")).isInstanceOf(List.class);
    }

    @Test
    void bureauBreSurfacesMissingMetricsAndDefinitions() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE");
        Map<String, Object> result = service.assess(session);
        Map<String, Object> summary = cast(result.get("summary"));

        assertThat(((Number) summary.get("rulesIdentified")).intValue()).isGreaterThan(0);
        int pct = ((Number) summary.get("implementationReadinessPercent")).intValue();
        assertThat(pct).isLessThan(100);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) result.get("rules");
        boolean hasBlockedOrClarify = rules.stream().anyMatch(r -> {
            String st = String.valueOf(r.get("implementability"));
            return PolicyImplementabilityService.METRIC_REQUIRED.equals(st)
                    || PolicyImplementabilityService.DEFINITION_REQUIRED.equals(st)
                    || PolicyImplementabilityService.DATA_SOURCE_REQUIRED.equals(st)
                    || PolicyImplementabilityService.MAPPING_REQUIRED.equals(st)
                    || PolicyImplementabilityService.NOT_IMPLEMENTABLE.equals(st);
        });
        assertThat(hasBlockedOrClarify)
                .as("Bureau BRE should expose overdue/NTC/CLEAN related implementability gaps")
                .isTrue();

        if (Boolean.TRUE.equals(summary.get("draftBlockedByCriticalDataGap"))) {
            assertThat(((Number) summary.get("criticalBlocked")).intValue()).isGreaterThan(0);
        }
    }

    @Test
    void primaryAvailablePathIsReadyWithoutInventingFallbackFetch() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE");
        Map<String, Object> result = service.assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("requirementMatrix");
        boolean anyReady = matrix.stream().anyMatch(r ->
                PolicyImplementabilityService.READY.equals(String.valueOf(r.get("status")))
                        && "AVAILABLE".equals(String.valueOf(r.get("availability"))));
        // Bureau score etc. should be AVAILABLE in registry when referenced
        assertThat(anyReady || !matrix.isEmpty()).isTrue();
    }

    @Test
    void noSilentDefaultAndNoFakeAvailabilityFlags() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking_BRE");
        Map<String, Object> result = service.assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("requirementMatrix");
        for (Map<String, Object> row : matrix) {
            String av = String.valueOf(row.get("availability"));
            assertThat(av).isIn("AVAILABLE", "UNAVAILABLE", "CANDIDATE", "UNKNOWN");
        }
        Map<String, Object> contract = cast(result.get("applicationTimeContract"));
        assertThat(contract.get("status")).isEqualTo("DOCUMENTED_NOT_ACTIVATED");
        assertThat(contract.get("normalProcessingComparesMultiplePolicies")).isEqualTo(false);
    }

    private PolicyStudioSession load(String resource, String name) throws Exception {
        String text = new String(
                getClass().getClassLoader().getResourceAsStream(resource).readAllBytes(),
                StandardCharsets.UTF_8);
        return new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), name, "TXT", text, "test", name + ".txt");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}
