package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueSeed;
import com.los.core.creditintelligence.policystudio.parameters.GacatSourceFamily;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.sourceintegration.EquifaxRetailRawIds;
import com.los.core.creditintelligence.policystudio.sourceintegration.EquifaxRetailSourceCardUniverse;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadiness;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadinessReason;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EQUIFAX-SOURCE-CARD-COUNT-RECONCILIATION-1.
 * Expected card counts are derived from the Equifax-capable canonical set, not hard-coded 117.
 */
class EquifaxSourceCardCountReconciliationTest {

    private static final Set<String> HONEST_NOT_READY_DERIVED = Set.of(
            "bureau.thin_file_indicator",
            "bureau.restructured_account_count",
            "bureau.account_sold_count",
            "bureau.dbt_account_count",
            "bureau.pwos_account_count",
            "bureau.lss_account_count");

    private final CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
    private final DataParametersAdminService admin = new DataParametersAdminService();

    @Test
    void capabilitySchemaAlias_collapsesToBureauRetail() {
        CanonicalParameterDefinition raw = registry.findById("bureau.score").orElseThrow();
        CanonicalParameterDefinition aliased = new CanonicalParameterDefinition(
                "bureau.inquiries.last_3m",
                "alias probe",
                "BUREAU_RETAIL",
                CanonicalParameterDefinition.DERIVED,
                raw.unit(),
                raw.period(),
                raw.availability(),
                raw.calculationSummary(),
                List.of(),
                raw.existingImplementationBinding(),
                List.of(),
                null,
                null,
                raw.capability());
        assertThat(aliased.evaluatedFrom()).isEqualTo(GacatSourceFamily.BUREAU_RETAIL);
        assertThat(GacatSourceFamily.sameFamily("BUREAU_RETAIL", "Bureau Retail")).isTrue();
    }

    @Test
    void equifaxCard_matchesCanonicalEquifaxUniverse_andExcludesReasonCode() {
        List<CanonicalParameterDefinition> expected = EquifaxRetailSourceCardUniverse.fromRegistry(registry.all())
                .stream()
                .sorted(Comparator.comparing(CanonicalParameterDefinition::id))
                .toList();
        Set<String> expectedIds = expected.stream()
                .map(CanonicalParameterDefinition::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> overview = admin.overview();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) overview.get("sourceCapabilitySummary");
        List<Map<String, Object>> equifaxCards = cards.stream()
                .filter(row -> GacatSourceFamily.isBureauRetail(String.valueOf(row.get("source")))
                        || String.valueOf(row.get("providerLabel")).toLowerCase().contains("equifax"))
                .toList();
        assertThat(equifaxCards).as("exactly one Equifax / Bureau Retail source card").hasSize(1);
        Map<String, Object> card = equifaxCards.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> canonical = (Map<String, Object>) card.get("canonicalCounts");

        int expectedReady = 0;
        int expectedNotReady = 0;
        int expectedBp = 0;
        int expectedIng = 0;
        int unexplainedRaw = 0;
        int unexplainedDerived = 0;
        int noncanonicalReadiness = 0;
        List<String> cardNotReady = new ArrayList<>();
        for (CanonicalParameterDefinition def : expected) {
            Map<String, Object> truth = CanonicalParameterStateService.state(def.id());
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = truth.get("semantic") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));
            String readiness = String.valueOf(truth.getOrDefault("businessReadiness",
                    truth.getOrDefault("primaryStatus", "")));
            String reason = String.valueOf(truth.getOrDefault("businessReadinessReason", ""));
            if ("INGREDIENT".equals(paramClass)) {
                expectedIng++;
            } else if (!"CONFIGURATION".equals(paramClass) && !"DECISION_OUTPUT".equals(paramClass)) {
                expectedBp++;
            }
            if (BusinessReadiness.READY.name().equals(readiness)) {
                expectedReady++;
            } else if (BusinessReadiness.NOT_READY.name().equals(readiness)
                    && !BusinessReadinessReason.NOT_APPLICABLE.name().equals(reason)) {
                expectedNotReady++;
                cardNotReady.add(def.id());
            }
            if (CanonicalParameterDefinition.RAW.equals(def.type())
                    && !"INGREDIENT".equals(paramClass)
                    && !"BUSINESS_PARAMETER".equals(paramClass)
                    && !"CONFIGURATION".equals(paramClass)
                    && !"DECISION_OUTPUT".equals(paramClass)) {
                unexplainedRaw++;
            }
            if (CanonicalParameterDefinition.DERIVED.equals(def.type())
                    && !"BUSINESS_PARAMETER".equals(paramClass)
                    && !"CONFIGURATION".equals(paramClass)
                    && !"DECISION_OUTPUT".equals(paramClass)) {
                unexplainedDerived++;
            }
        }

        int cardTotal = ((Number) canonical.get("catalogueListed")).intValue();
        int cardReady = ((Number) canonical.get("ready")).intValue();
        int cardNotReadyCount = ((Number) canonical.get("notReady")).intValue();
        int cardBp = ((Number) canonical.get("businessParameters")).intValue();
        int cardIng = ((Number) canonical.get("sourceIngredients")).intValue();

        int totalMismatch = Math.abs(cardTotal - expected.size());
        int readyMismatch = Math.abs(cardReady - expectedReady);
        int notReadyMismatch = Math.abs(cardNotReadyCount - expectedNotReady);

        Map<String, Object> browse = admin.browseBySource("Bureau Retail");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> browseRaw = (List<Map<String, Object>>) browse.get("raw");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> browseDerived = (List<Map<String, Object>>) browse.get("derived");
        Set<String> browseIds = new TreeSet<>();
        browseRaw.forEach(m -> browseIds.add(String.valueOf(m.get("id"))));
        browseDerived.forEach(m -> browseIds.add(String.valueOf(m.get("id"))));

        int wrongProvider = 0;
        if (browseIds.contains("bureau.reason_code") || expectedIds.contains("bureau.reason_code")) {
            wrongProvider++;
        }
        if (cards.stream().anyMatch(row -> "BUREAU_RETAIL".equals(String.valueOf(row.get("source"))))) {
            wrongProvider++;
        }
        for (String id : browseIds) {
            CanonicalParameterDefinition def = registry.findById(id).orElse(null);
            if (def != null && CanonicalParameterDefinition.RAW.equals(def.type())
                    && !EquifaxRetailRawIds.ALL.contains(id)) {
                wrongProvider++;
            }
        }
        if (!browseIds.equals(expectedIds)) {
            totalMismatch += Math.abs(browseIds.size() - expectedIds.size());
        }

        for (CanonicalParameterDefinition def : expected) {
            Map<String, Object> truth = CanonicalParameterStateService.state(def.id());
            String canonicalReady = String.valueOf(truth.getOrDefault("businessReadiness", ""));
            // Card aggregation uses CanonicalParameterStateService — no independent authority.
            if (canonicalReady.isBlank()) {
                noncanonicalReadiness++;
            }
        }

        assertThat(canonical.get("businessReadinessAuthority"))
                .isEqualTo(CanonicalParameterStateService.AUTHORITY);
        @SuppressWarnings("unchecked")
        Map<String, Object> facing = (Map<String, Object>) card.get("lenderFacing");
        assertThat(facing.get("countAuthority")).isEqualTo(CanonicalParameterStateService.AUTHORITY);

        System.out.println("EQUIFAX_SOURCE_CARD_PARAMETER_MATRIX_BEGIN");
        System.out.println("PARAMETER_ID|TYPE|PARAM_CLASS|CALC_MODE|READINESS|REASON|EQUIFAX_RAW_SET|CARD_BUCKET");
        for (CanonicalParameterDefinition def : expected) {
            Map<String, Object> truth = CanonicalParameterStateService.state(def.id());
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = truth.get("semantic") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));
            String calcMode = String.valueOf(semantic.getOrDefault("calculationMode", ""));
            String br = String.valueOf(truth.getOrDefault("businessReadiness", ""));
            String reason = String.valueOf(truth.getOrDefault("businessReadinessReason", ""));
            boolean inEqRaw = EquifaxRetailRawIds.ALL.contains(def.id());
            String bucket = "INGREDIENT".equals(paramClass) ? "SOURCE_INGREDIENT" : "BUSINESS_PARAMETER";
            System.out.println(String.join("|",
                    def.id(), def.type(), paramClass, calcMode, br, reason,
                    inEqRaw ? "Y" : "N", bucket));
        }
        System.out.println("EQUIFAX_SOURCE_CARD_PARAMETER_MATRIX_END");
        System.out.println("EQUIFAX_SOURCE_CARD_TOTAL_MISMATCH_COUNT=" + totalMismatch);
        System.out.println("EQUIFAX_SOURCE_CARD_READY_MISMATCH_COUNT=" + readyMismatch);
        System.out.println("EQUIFAX_SOURCE_CARD_NOT_READY_MISMATCH_COUNT=" + notReadyMismatch);
        System.out.println("SOURCE_CARD_PARAMETER_WITH_WRONG_PROVIDER_COUNT=" + wrongProvider);
        System.out.println("SOURCE_CARD_PARAMETER_WITH_NONCANONICAL_READINESS_COUNT=" + noncanonicalReadiness);
        System.out.println("SOURCE_CARD_RAW_CLASSIFICATION_UNEXPLAINED_COUNT=" + unexplainedRaw);
        System.out.println("SOURCE_CARD_DERIVED_CLASSIFICATION_UNEXPLAINED_COUNT=" + unexplainedDerived);
        System.out.println("CARD_TOTAL=" + cardTotal + " EXPECTED=" + expected.size());
        System.out.println("CARD_READY=" + cardReady + " EXPECTED=" + expectedReady);
        System.out.println("CARD_NOT_READY=" + cardNotReadyCount + " EXPECTED=" + expectedNotReady);
        System.out.println("CARD_BP=" + cardBp + " CARD_ING=" + cardIng);
        System.out.println("NOT_READY_IDS=" + cardNotReady);

        assertThat(totalMismatch).as("EQUIFAX_SOURCE_CARD_TOTAL_MISMATCH_COUNT").isZero();
        assertThat(readyMismatch).as("EQUIFAX_SOURCE_CARD_READY_MISMATCH_COUNT").isZero();
        assertThat(notReadyMismatch).as("EQUIFAX_SOURCE_CARD_NOT_READY_MISMATCH_COUNT").isZero();
        assertThat(wrongProvider).as("SOURCE_CARD_PARAMETER_WITH_WRONG_PROVIDER_COUNT").isZero();
        assertThat(noncanonicalReadiness).as("SOURCE_CARD_PARAMETER_WITH_NONCANONICAL_READINESS_COUNT").isZero();
        assertThat(unexplainedRaw).as("SOURCE_CARD_RAW_CLASSIFICATION_UNEXPLAINED_COUNT").isZero();
        assertThat(unexplainedDerived).as("SOURCE_CARD_DERIVED_CLASSIFICATION_UNEXPLAINED_COUNT").isZero();
        assertThat(cardBp).isEqualTo(expectedBp);
        assertThat(cardIng).isEqualTo(expectedIng);
        assertThat(browseIds).isEqualTo(expectedIds);
        assertThat(browseIds).doesNotContain("bureau.reason_code");
        assertThat(expectedIds).doesNotContain("bureau.reason_code");
        assertThat(registry.findById("bureau.reason_code")).isPresent();
        assertThat(EquifaxRetailSourceCardUniverse.belongsOnEquifaxCard(
                registry.findById("bureau.reason_code").orElseThrow())).isFalse();

        long rawOnCard = expected.stream().filter(d -> CanonicalParameterDefinition.RAW.equals(d.type())).count();
        long derivedOnCard = expected.stream().filter(d -> CanonicalParameterDefinition.DERIVED.equals(d.type())).count();
        long equifaxRawGacat = EquifaxRetailRawIds.ALL.stream()
                .filter(id -> registry.findById(id).isPresent())
                .count();
        assertThat(rawOnCard).isEqualTo(equifaxRawGacat);
        assertThat(derivedOnCard).isEqualTo(GacatCatalogueSeed.BUREAU_RETAIL_DERIVED_TARGET_COUNT);
        assertThat(registry.findById("bureau.inquiries.last_3m").orElseThrow().evaluatedFrom())
                .isEqualTo(GacatSourceFamily.BUREAU_RETAIL);
        assertThat(expectedIds).contains("bureau.inquiries.last_3m");

        for (String id : HONEST_NOT_READY_DERIVED) {
            assertThat(expectedIds).as(id + " on Equifax card").contains(id);
            Map<String, Object> truth = CanonicalParameterStateService.state(id);
            assertThat(truth.get("businessReadiness")).as(id).isEqualTo(BusinessReadiness.NOT_READY.name());
            assertThat(cardNotReady).as(id + " counted NOT_READY").contains(id);
        }
    }
}
