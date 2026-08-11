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
        assertTrue(Boolean.TRUE.equals(ops.get(1).get("unresolved")));
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
        assertTrue(Boolean.TRUE.equals(edi.get("unresolved")));
        assertEquals(ParameterResolutionSupport.STATUS_UNRESOLVED, edi.get("status"));
        assertTrue(Boolean.TRUE.equals(edi.get("resolveAction")));
        // Registry has application.proposed_edi but must NOT auto-bind
        assertNotEquals("application.proposed_edi", edi.get("parameterId"));
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
        Map<String, Object> unresolved = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of()).get(1);
        assertTrue(Boolean.TRUE.equals(unresolved.get("unresolved")));
        assertFalse(Boolean.TRUE.equals(unresolved.get("unavailable")));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("proposed_edi", ParameterResolutionSupport.unavailable(
                "application.proposed_edi", "Field not supplied on application", "Proposed EDI"));
        Map<String, Object> meta = Map.of(ParameterResolutionSupport.META_KEY, res);
        Map<String, Object> face = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                meta,
                Map.of()).get(1);
        assertTrue(Boolean.TRUE.equals(face.get("unavailable")));
        assertFalse(Boolean.TRUE.equals(face.get("unresolved")));
        assertNotEquals(unresolved.get("message"), face.get("message"));
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
        assertTrue(Boolean.TRUE.equals(clean.get("unresolved")));
        assertTrue(Boolean.TRUE.equals(clean.get("resolveAction")));

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
        assertEquals("SESSION_DRAFT_ONLY", mapped.get("persistence"));
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
