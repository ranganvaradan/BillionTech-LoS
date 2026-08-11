package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POLICY-RULE-AUTHORING-FIX-1 — structured + plain-English CM authoring, counting, edit rewrite.
 */
class PolicyRuleAuthoringFix1Test {

    private StagingPolicyStudioDemoService demoService;
    private CmRuleAuthoringService authoring;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CreditIntelligenceProperties properties = new CreditIntelligenceProperties();
        properties.setDefaultTenantId(tenantId);
        properties.getStagingDemo().setEnabled(true);
        properties.getCutover().setAllowCanonicalAuthority(false);
        authoring = new CmRuleAuthoringService();
        demoService = new StagingPolicyStudioDemoService(
                properties, new PolicyStudioOrchestrator(), new PolicyTextExtractionService());
    }

    @Test
    void scratchStartsWithZeroUnderwritingClassificationStubs() {
        Map<String, Object> view = demoService.createFromScratch(
                Map.of("policyName", "SME Term Loan Policy – Authoring Test"),
                "credit_manager",
                null);
        assertThat(((Number) view.get("underwritingRuleCount")).intValue()).isZero();
        @SuppressWarnings("unchecked")
        List<?> uw = (List<?>) view.get("underwritingRules");
        assertThat(uw == null ? List.of() : uw).isEmpty();
        @SuppressWarnings("unchecked")
        List<?> other = (List<?>) view.get("otherPolicyContent");
        assertThat(other == null ? List.of() : other).isEmpty();
        assertThat(view.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void previewBureauScorePlainEnglish() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score should be > 625",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("parameterId")).isEqualTo("bureau.score");
        assertThat(String.valueOf(preview.get("source"))).containsIgnoringCase("Bureau");
        assertThat(preview.get("operator")).isEqualTo(">");
        assertThat(Number.class.cast(preview.get("value")).intValue()).isEqualTo(625);
    }

    @Test
    void incompletePreviewDoesNotConfirm() {
        Map<String, Object> incomplete = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Something vague about risk appetite"));
        assertThat(incomplete.get("complete")).isEqualTo(false);
        assertThat(String.valueOf(incomplete.get("message"))).isNotBlank();
    }

    @Test
    void fiveRuleScratchWalkthroughAndEditPersists() {
        Map<String, Object> created = demoService.createFromScratch(
                Map.of("policyName", "SME Term Loan Policy – Authoring Test",
                        "product", "Business Term Loan",
                        "borrowerType", "Company"),
                "credit_manager",
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) created.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));

        String[] texts = {
                "Bureau score should be >= 650",
                "Business vintage should be at least 24 months",
                "FOIR should not exceed 50%",
                "Cheque bounce count in last 3 months must be 0",
                "Average banking turnover should be at least 75% of GST turnover"
        };
        String[] treatments = {"Reject", "Reject", "Reject", "Reject", "Manual Review"};
        for (int i = 0; i < texts.length; i++) {
            Map<String, Object> added = demoService.addPlainEnglishRule(docId, Map.of(
                    "confirm", true,
                    "mode", "DESCRIBE",
                    "text", texts[i],
                    "treatment", treatments[i]), null);
            assertThat(added.get("addedRuleCount")).isEqualTo(1);
            assertThat(added.get("confirmed")).isEqualTo(true);
        }

        Map<String, Object> session = demoService.sessionView(docId, null);
        assertThat(((Number) session.get("underwritingRuleCount")).intValue()).isEqualTo(5);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) session.get("underwritingRules");
        assertThat(uw).hasSize(5);
        assertThat(uw).noneMatch(c -> "CLASSIFICATION".equals(
                String.valueOf(((Map<?, ?>) c.getOrDefault("technicalExpression", Map.of())).get("op"))));
        assertThat(uw).noneMatch(c -> Boolean.TRUE.equals(c.get("classificationOnly")));

        String bureauRuleId = uw.stream()
                .filter(c -> "bureau.score".equals(String.valueOf(c.get("parameterId")))
                        || String.valueOf(c.getOrDefault("systemRuleId", "")).contains("BUREAU_SCORE")
                        || String.valueOf(c.getOrDefault("businessRule", "")).toLowerCase().contains("bureau"))
                .map(c -> String.valueOf(c.get("id")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> edited = demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 675,
                "treatment", "Reject",
                "replaceRuleId", bureauRuleId), null);
        assertThat(edited.get("replaced")).isEqualTo(true);

        Map<String, Object> after = demoService.sessionView(docId, null);
        assertThat(((Number) after.get("underwritingRuleCount")).intValue()).isEqualTo(5);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterUw = (List<Map<String, Object>>) after.get("underwritingRules");
        Map<String, Object> bureauAfter = afterUw.stream()
                .filter(c -> bureauRuleId.equals(String.valueOf(c.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(bureauAfter.get("threshold"))).contains("675");
        assertThat(String.valueOf(bureauAfter.get("businessRule"))).contains("675");

        Map<String, Object> copy = demoService.copyPolicy(
                docId, Map.of("policyName", "SME Term Loan Policy – Authoring Test (Copy)"), null);
        assertThat(((Number) copy.get("underwritingRuleCount")).intValue()).isEqualTo(5);
        assertThat(String.valueOf(copy.get("copiedFromLabel"))).contains("Copied from:");
        @SuppressWarnings("unchecked")
        Map<String, Object> copyHeader = (Map<String, Object>) copy.get("policyHeader");
        UUID copyId = UUID.fromString(String.valueOf(copyHeader.get("documentId")));
        assertThat(copyId).isNotEqualTo(docId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copyUw = (List<Map<String, Object>>) copy.get("underwritingRules");
        String copyBureauId = copyUw.stream()
                .filter(c -> "bureau.score".equals(String.valueOf(c.get("parameterId")))
                        || String.valueOf(c.getOrDefault("systemRuleId", "")).contains("BUREAU_SCORE"))
                .map(c -> String.valueOf(c.get("id")))
                .findFirst()
                .orElse(String.valueOf(copyUw.get(0).get("id")));
        demoService.addPlainEnglishRule(copyId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 700,
                "treatment", "Reject",
                "replaceRuleId", copyBureauId), null);

        Map<String, Object> originalAgain = demoService.sessionView(docId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> origUw = (List<Map<String, Object>>) originalAgain.get("underwritingRules");
        Map<String, Object> origBureau = origUw.stream()
                .filter(c -> bureauRuleId.equals(String.valueOf(c.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(origBureau.get("threshold"))).contains("675");
        assertThat(String.valueOf(origBureau.get("threshold"))).doesNotContain("700");
    }

    @Test
    void editIncompleteTextFailsVisibly() {
        Map<String, Object> created = demoService.createFromScratch(
                Map.of("policyName", "Edit Fail Visible"), "credit_manager", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) created.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));
        demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "DESCRIBE",
                "text", "Bureau score should be >= 650",
                "treatment", "Reject"), null);
        Map<String, Object> session = demoService.sessionView(docId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) session.get("underwritingRules");
        String ruleId = String.valueOf(uw.get(0).get("id"));
        assertThatThrownBy(() -> demoService.reviewRule(docId, UUID.fromString(ruleId), Map.of(
                "uiAction", "EDIT",
                "businessRule", "totally uninterpretable gobbledygook",
                "reason", "test"), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void groupCardsDoesNotCountNarrativeAsUnderwriting() {
        List<Map<String, Object>> cards = List.of(
                Map.of("status", "Needs clarification", "classificationOnly", true,
                        "businessGroup", "Narrative / Excluded",
                        "technicalExpression", Map.of("op", "CLASSIFICATION")),
                Map.of("status", "Ready", "cmAuthored", true, "businessGroup", "Bureau",
                        "parameterId", "bureau.score"),
                Map.of("status", "Data requirement", "dataRequirementOnly", true));
        Map<String, Object> grouped = PolicyStudioConvergencePresenter.groupCards(cards);
        assertThat(grouped.get("underwritingRuleCount")).isEqualTo(1);
        assertThat(grouped.get("otherPolicyContentCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) grouped.get("dataAndCalculations");
        assertThat(data).hasSize(1);
    }

    @Test
    void allowCanonicalAuthorityRemainsFalse() {
        assertThat(demoService.authoringSources(null).get("allowCanonicalAuthority")).isEqualTo(false);
        Map<String, Object> created = demoService.createFromScratch(
                Map.of("policyName", "Authority Check"), "credit_manager", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) created.get("policyHeader");
        UUID docId = UUID.fromString(String.valueOf(header.get("documentId")));
        assertThat(demoService.previewAuthoredRule(
                docId,
                Map.of("mode", "DESCRIBE", "text", "Bureau score should be > 625"),
                null).get("allowCanonicalAuthority")).isEqualTo(false);
    }
}
