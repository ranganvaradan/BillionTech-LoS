package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-STUDIO-GATE2 — write-off must not map to Proposed EDI; token-safe EDI; resolution states.
 */
class PolicyStudioGate2ParameterResolutionTest {

    private CmRuleAuthoringService authoring;

    @BeforeEach
    void setUp() {
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
        authoring = new CmRuleAuthoringService();
    }

    @Test
    void creditDoesNotContainEdiAsProposedEdi() {
        assertThat(BusinessConceptMatching.isProposedEdiPhrase("credit cards")).isFalse();
        assertThat(BusinessConceptMatching.isProposedEdiPhrase("No Loan Write-Offs except Credit Cards")).isFalse();
        assertThat(BusinessConceptMatching.isProposedEdiPhrase("proposed edi")).isTrue();
        assertThat(BusinessConceptMatching.isProposedEdiPhrase("ADB >= EDI")).isTrue();
    }

    @Test
    void writeOffGoldenNotProposedEdi() {
        Map<String, Object> r = BusinessConceptResolver.resolve(
                "No Loan Write-Offs are allowed, except for Credit Cards");
        assertThat(r.get("mappedToProposedEdi")).isNotEqualTo(true);
        assertThat(String.valueOf(r.get("canonicalParameter"))).isNotEqualTo("application.proposed_edi");
        assertThat(String.valueOf(r.get("resolutionState")))
                .isIn(BusinessConceptResolver.READY_DERIVED, BusinessConceptResolver.READY_EXISTING,
                        BusinessConceptResolver.NEEDS_DERIVATION, BusinessConceptResolver.NEEDS_PARAMETER_SELECTION);
        assertThat(String.valueOf(r.get("canonicalParameter")))
                .isEqualTo(BusinessConceptResolver.WRITEOFF_NON_CC);
        assertThat(r.get("executable")).isEqualTo(true);
    }

    @Test
    void describeWriteOffDoesNotSuggestEdi() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "No Loan Write-Offs are allowed, except for Credit Cards"));
        assertThat(p.get("parameterId")).isNotEqualTo("application.proposed_edi");
        assertThat(String.valueOf(p.get("parameterId"))).doesNotContain("proposed_edi");
        assertThat(p.get("mappedToProposedEdi")).isNotEqualTo(true);
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("parameterId")).isEqualTo(BusinessConceptResolver.WRITEOFF_NON_CC);
    }

    @Test
    void registrySearchWriteOffDoesNotReturnEdi() {
        Map<String, Object> search = CanonicalParameterRegistry.shared()
                .search("No Loan Write-Offs except Credit Cards");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) search.get("results");
        if (hits == null) {
            hits = (List<Map<String, Object>>) search.get("parameters");
        }
        if (hits != null) {
            assertThat(hits.stream().noneMatch(h ->
                    "application.proposed_edi".equals(h.get("id"))
                            || "application.proposed_edi".equals(h.get("parameterId")))).isTrue();
        }
    }

    @Test
    void sourceConstraintBureauForWriteOff() {
        Map<String, Object> r = BusinessConceptResolver.resolve(
                "loan write-offs", "Bureau");
        assertThat(r.get("suggestedSource")).isEqualTo("Bureau");
        assertThat(String.valueOf(r.get("resolutionState"))).isNotEqualTo(BusinessConceptResolver.NEEDS_CLARIFICATION);
    }

    @Test
    void sourceConstraintBankingRejectsWriteOff() {
        Map<String, Object> r = BusinessConceptResolver.resolve(
                "No Loan Write-Offs except Credit Cards", "Bank Statement");
        assertThat(r.get("resolutionState")).isEqualTo(BusinessConceptResolver.DATA_SOURCE_UNAVAILABLE);
        assertThat(r.get("executable")).isEqualTo(false);
    }

    @Test
    void ambiguousConceptNeedsClarificationNotEdi() {
        Map<String, Object> r = BusinessConceptResolver.resolve("xyzzy unexplained widget score");
        assertThat(String.valueOf(r.get("resolutionState")))
                .isIn(BusinessConceptResolver.NEEDS_CLARIFICATION,
                        BusinessConceptResolver.NEEDS_PARAMETER_SELECTION,
                        BusinessConceptResolver.DATA_SOURCE_UNAVAILABLE,
                        BusinessConceptResolver.UNSUPPORTED);
        assertThat(String.valueOf(r.getOrDefault("canonicalParameter", ""))).isNotEqualTo("application.proposed_edi");
    }

    @Test
    void operandKeyWriteOffNotEdi() {
        assertThat(ParameterResolutionSupport.normalizeOperandKey(
                "No Loan Write-Offs except Credit Cards")).isEqualTo("writeoff_non_cc");
        assertThat(ParameterResolutionSupport.normalizeOperandKey("credit cards")).isNotEqualTo("proposed_edi");
    }

    @Test
    void bureauScoreStillReadyExisting() {
        Map<String, Object> r = BusinessConceptResolver.resolve("Bureau score must be 650 or above");
        // may be NEEDS_PARAMETER_SELECTION if multiple score params — but not EDI
        assertThat(String.valueOf(r.getOrDefault("canonicalParameter", ""))).isNotEqualTo("application.proposed_edi");
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score should be 650 or above"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("parameterId")).isEqualTo("bureau.score");
    }
}
