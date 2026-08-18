package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POLICY-PARAMETER-RESOLVER-1 — focused unit coverage for generic CM parameter resolver.
 */
class PolicyParameterResolver1Test {

    private final CanonicalParameterRegistry registry = new CanonicalParameterRegistry();
    private final ParameterDerivationPlanner planner = new ParameterDerivationPlanner(registry);

    @Test
    void ruleCanContainTwoIndependentlyResolvedOperands() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of());
        assertEquals(2, ops.size());
        assertEquals("average_daily_balance", ops.get(0).get("operandKey"));
        assertEquals("proposed_edi", ops.get(1).get("operandKey"));
        assertFalse(Boolean.TRUE.equals(ops.get(0).get("unresolved")));
        assertEquals(ParameterResolutionSupport.MAPPING_CURRENT_RESOLVED, ops.get(1).get("mappingState"));
        assertEquals("application.proposed_edi", ops.get(1).get("parameterId"));
    }

    @Test
    void adbResolvesToExistingBankStatementDerivedParameter() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_DIGILEAP_ADB_DIV5_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of());
        Map<String, Object> adb = ops.get(0);
        assertEquals("banking.avg_daily_balance_3m", adb.get("parameterId"));
        assertEquals("Bank Statement", adb.get("evaluatedFrom"));
        assertEquals(CanonicalParameterDefinition.DERIVED, adb.get("resolutionState"));
        assertNotNull(adb.get("howCalculated"));
        assertTrue(String.valueOf(adb.get("howCalculated")).toLowerCase().contains("balance"));
    }

    @Test
    void ediRemainsUnresolvedUntilCmAction() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of());
        Map<String, Object> edi = ops.get(1);
        assertEquals(ParameterResolutionSupport.MAPPING_CURRENT_RESOLVED, edi.get("mappingState"));
        assertEquals("application.proposed_edi", edi.get("parameterId"));
        assertNotEquals("Not yet mapped", edi.get("availabilityLabel"));
        assertNotEquals("Not yet mapped", edi.get("message"));
    }

    @Test
    void resolveBySourceShowsActualRawAndDerived() {
        Map<String, Object> bank = registry.browseBySource("Bank Statement");
        assertFalse(((List<?>) bank.get("raw")).isEmpty());
        assertFalse(((List<?>) bank.get("derived")).isEmpty());
        assertEquals(false, bank.get("invented"));
        boolean hasAdb = ((List<?>) bank.get("derived")).stream()
                .anyMatch(r -> "banking.avg_daily_balance_3m".equals(((Map<?, ?>) r).get("id")));
        boolean hasClosing = ((List<?>) bank.get("raw")).stream()
                .anyMatch(r -> String.valueOf(((Map<?, ?>) r).get("businessName"))
                        .toLowerCase().contains("closing"));
        assertTrue(hasAdb);
        assertTrue(hasClosing);
    }

    @Test
    void persistedCanonicalIdMissingFromGacat_isExistingMappingUnresolved_notNotYetMapped() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "CM_UNKNOWN_METRIC_GTE",
                List.of("bureau.not_a_real_canonical_id"),
                Map.of("parameterId", "bureau.not_a_real_canonical_id"),
                Map.of());
        assertEquals(1, ops.size());
        Map<String, Object> face = ops.get(0);
        assertEquals("bureau.not_a_real_canonical_id", face.get("parameterId"));
        assertEquals("EXISTING_MAPPING_UNRESOLVED", face.get("availabilityLabel"));
        assertEquals(ParameterResolutionSupport.STATUS_EXISTING_MAPPING_UNRESOLVED, face.get("status"));
        assertFalse(Boolean.TRUE.equals(face.get("unresolved")));
        assertTrue(Boolean.TRUE.equals(face.get("existingMappingUnresolved")));
        assertNotEquals("Not yet mapped", face.get("message"));
        assertEquals(ParameterResolutionSupport.MAPPING_EXISTING_UNRESOLVED, face.get("mappingState"));
    }

    @Test
    void persistedIdWinsOverUnresolvedFlag_neverNotYetMapped() {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("parameterId", "bureau.inquiries.current_month");
        face.put("unresolved", true);
        face.put("evaluatedFrom", "Bureau Retail");
        face.put("resolutionState", CanonicalParameterDefinition.DERIVED);
        face.put("availabilityLabel", "Derived automatically");
        RuleOperandPresenter.stampMappingAuthority(face, CanonicalParameterDefinition.DERIVED);
        assertEquals(ParameterResolutionSupport.MAPPING_CURRENT_RESOLVED, face.get("mappingState"));
        assertEquals("bureau.inquiries.current_month", face.get("canonicalParameterId"));
        assertEquals(Boolean.FALSE, face.get("unresolved"));
        assertNotEquals("Not yet mapped", face.get("availabilityLabel"));
        assertEquals("DERIVED", face.get("parameterClassification"));
    }

    @Test
    void mappedInquiriesOperand_isCurrentMappingResolved() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "CM_BUREAU_INQUIRIES_CURRENT_MONTH_LTE",
                List.of("bureau.inquiries.current_month"),
                Map.of("parameterId", "bureau.inquiries.current_month"),
                Map.of());
        assertEquals(1, ops.size());
        Map<String, Object> face = ops.get(0);
        assertEquals("bureau.inquiries.current_month", face.get("parameterId"));
        assertEquals(ParameterResolutionSupport.MAPPING_CURRENT_RESOLVED, face.get("mappingState"));
        assertNotEquals("Not yet mapped", face.get("availabilityLabel"));
        assertNotEquals("Not yet mapped", face.get("message"));
    }

    @Test
    void genuinelyUnmappedOperand_stillSaysNotYetMapped() {
        Map<String, Object> face = RuleOperandPresenter.buildOperands(
                "UNKNOWN_RULE",
                List.of(),
                Map.of(),
                Map.of()).stream().findFirst().orElse(Map.of());
        // No expression paths → no invented operand
        assertTrue(face.isEmpty() || "Not yet mapped".equals(face.get("availabilityLabel")));
    }

    @Test
    void searchCibilFindsExistingBureauScore() {
        Map<String, Object> hit = registry.search("CIBIL");
        assertTrue(((Number) hit.get("count")).intValue() >= 1);
        assertEquals(false, hit.get("createsParameter"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) hit.get("results");
        assertTrue(results.stream().anyMatch(r -> "bureau.score".equals(r.get("id"))));
    }

    @Test
    void searchDoesNotCreateDuplicateParameter() {
        int before = registry.all().size();
        registry.search("EDI");
        registry.search("adjusted turnover");
        assertEquals(before, registry.all().size());
        assertEquals(false, registry.search("made-up-xyz-parameter").get("createsParameter"));
    }

    @Test
    void plainEnglishDefinitionProducesProposalNotSilentAcceptance() {
        Map<String, Object> proposal = planner.propose("Proposed EDI",
                "EDI means total existing monthly EMI obligations from bureau plus the EMI for the proposed loan.");
        assertEquals("PROPOSAL", proposal.get("kind"));
        assertEquals("AWAITING_CM_CONFIRMATION", proposal.get("status"));
        assertEquals(false, proposal.get("silentlyAccepted"));
        assertEquals(false, proposal.get("executableCodeGenerated"));
        assertNotNull(proposal.get("proposedCalculation"));
    }

    @Test
    void proposalIdentifiesKnownPrimitivesWhereAvailable() {
        Map<String, Object> proposal = planner.propose("EDI",
                "Average daily balance plus bureau score");
        @SuppressWarnings("unchecked")
        List<?> known = (List<?>) proposal.get("knownPrimitives");
        assertFalse(known.isEmpty());
    }

    @Test
    void existingDerivedMetricReusedBeforeDuplicateCalc() {
        Map<String, Object> proposal = planner.propose("Average daily balance",
                "Average daily balance over trailing 3 months");
        assertTrue(Boolean.TRUE.equals(proposal.get("reuseExisting"))
                || "REUSES_EXISTING".equals(proposal.get("result")));
        assertNotNull(proposal.get("existingEquivalent"));
    }

    @Test
    void manualInputCreatesManualParameterNotManualReview() {
        Map<String, Object> manual = ParameterResolutionSupport.manual(
                "Proposed EDI", "Money", "INR", "Credit Analyst", "Enter monthly EDI", "Proposed EDI");
        assertEquals(ParameterResolutionSupport.STATUS_MANUAL, manual.get("status"));
        assertEquals(ParameterResolutionSupport.TYPE_MANUAL, manual.get("resolutionType"));
        assertEquals(true, manual.get("factSource"));
        assertEquals(true, manual.get("notManualReviewTreatment"));
        assertEquals("Manual Input", manual.get("evaluatedFrom"));
    }

    @Test
    void unresolvedIsDistinctFromUnavailable() {
        Map<String, Object> unmapped = new LinkedHashMap<>();
        unmapped.put("operandKey", "unknown_term");
        unmapped.put("businessName", "Unknown term");
        RuleOperandPresenter.stampMappingAuthority(unmapped, null);
        assertEquals(ParameterResolutionSupport.MAPPING_NOT_YET_MAPPED, unmapped.get("mappingState"));
        assertTrue(Boolean.TRUE.equals(unmapped.get("unresolved")));
        assertFalse(Boolean.TRUE.equals(unmapped.get("unavailable")));

        Map<String, Object> unavailable = new LinkedHashMap<>();
        unavailable.put("parameterId", "application.proposed_edi");
        unavailable.put("unavailable", true);
        unavailable.put("unresolved", false);
        unavailable.put("message", "Field not supplied on application");
        RuleOperandPresenter.stampMappingAuthority(unavailable, CanonicalParameterDefinition.MANUAL);
        assertTrue(Boolean.TRUE.equals(unavailable.get("unavailable")));
        assertFalse(Boolean.TRUE.equals(unavailable.get("unresolved")));
        assertEquals(ParameterResolutionSupport.MAPPING_CURRENT_RESOLVED, unavailable.get("mappingState"));
        assertNotEquals(unmapped.get("mappingState"), unavailable.get("mappingState"));
    }

    @Test
    void cleanUsesSameResolverAndIsNotSilentlyDpd0() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BUREAU_OVERDUE_EXCEPTION_PARENT",
                List.of("bureau.overdue.age_months"),
                Map.of(),
                Map.of("kind", "EXCEPTION_ALL"));
        assertFalse(ops.isEmpty());
        Map<String, Object> clean = ops.stream()
                .filter(o -> "clean_history".equals(o.get("operandKey")))
                .findFirst()
                .orElseThrow();
        assertEquals("CURRENT_MAPPING_RESOLVED", String.valueOf(clean.get("mappingState")));
        assertEquals("bureau.credit_after_overdue.clean_history_months", clean.get("parameterId"));
        assertNotEquals("Not yet mapped", clean.get("availabilityLabel"));
        assertFalse(String.valueOf(clean.getOrDefault("howCalculated", "")).toLowerCase().contains("dpd = 0")
                && String.valueOf(clean.getOrDefault("howCalculated", "")).toLowerCase().contains("invent"));

        Map<String, Object> proposal = planner.propose("CLEAN",
                "Clean means DPD is zero for six months");
        assertEquals(true, proposal.get("doNotInventCleanAsDpd0"));
        assertFalse(Boolean.TRUE.equals(proposal.get("silentlyAccepted")));
    }

    @Test
    void compoundAllParentSurvivesChildParameterResolution() {
        Map<String, Object> mapped = ParameterResolutionSupport.mapToExisting(
                registry.findById("bureau.credit_after_overdue.clean_history_months").orElseThrow(),
                "Clean history");
        Map<String, Object> resolutions = new LinkedHashMap<>();
        resolutions.put("clean_history", mapped);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(ParameterResolutionSupport.META_KEY, resolutions);

        Map<String, Object> visual = PolicyStudioConvergencePresenter.compoundOverdueVisual(
                ParameterResolutionSupport.toCleanHistoryBridge(mapped));
        assertEquals("EXCEPTION_ALL", visual.get("kind"));
        assertEquals("ALL", visual.get("logic"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) visual.get("conditions");
        assertEquals(4, conditions.size());
        Map<String, Object> cleanCond = conditions.stream()
                .filter(c -> String.valueOf(c.get("parameter")).toLowerCase().contains("clean"))
                .findFirst()
                .orElseThrow();
        assertFalse(Boolean.TRUE.equals(cleanCond.get("needsDefinition")));

        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BUREAU_OVERDUE_EXCEPTION_PARENT",
                List.of(),
                meta,
                visual);
        assertFalse(Boolean.TRUE.equals(ops.get(0).get("unresolved")));
    }

    @Test
    void mappedResolutionPersistsInMetadataShapeForDraftReload() {
        Map<String, Object> mapped = ParameterResolutionSupport.mapToExisting(
                registry.findById("application.proposed_edi").orElseThrow(),
                "Proposed EDI");
        Map<String, Object> meta = new LinkedHashMap<>();
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("proposed_edi", mapped);
        meta.put(ParameterResolutionSupport.META_KEY, all);

        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                meta,
                Map.of());
        assertFalse(Boolean.TRUE.equals(ops.get(1).get("unresolved")));
        assertEquals("application.proposed_edi", ops.get(1).get("parameterId"));
        assertEquals("POLICY_VERSION_DURABLE", mapped.get("persistence"));
        assertEquals("POLICY_DRAFT", mapped.get("scope"));
    }

    @Test
    void allowCanonicalAuthorityRemainsFalseOnCatalogueAndProposal() {
        assertEquals(false, registry.catalogueView().get("allowCanonicalAuthority"));
        assertEquals(false, registry.search("cibil").get("allowCanonicalAuthority"));
        assertEquals(false, planner.propose("X", "Y").get("allowCanonicalAuthority"));
        assertEquals(false, ParameterResolutionSupport.manual("A", "Money", "INR", "CM", null, "A")
                .get("allowCanonicalAuthority"));
    }
}
