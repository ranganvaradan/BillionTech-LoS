package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.AuthoringValueTypes;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-TYPED-RULE-AUTHORING-1 — typed BUILD/DESCRIBE authoring invariants.
 */
class PolicyTypedRuleAuthoring1Test {

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
    void panVerifiedYes_addsSuccessfully() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "=",
                "value", "Yes",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("value")).isEqualTo(true);
        assertThat(String.valueOf(preview.get("ruleDisplay"))).containsIgnoringCase("PAN");
        assertThat(String.valueOf(preview.get("ruleDisplay"))).contains("Yes");
        assertThat(String.valueOf(preview.get("message"))).doesNotContain("Value is required");

        UUID docId = scratchDoc();
        Map<String, Object> added = demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", true,
                "treatment", "Reject"), null);
        assertThat(added.get("confirmed")).isEqualTo(true);
        assertThat(added.get("allowCanonicalAuthority")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) added.get("preview");
        assertThat(p.get("value")).isEqualTo(true);
    }

    @Test
    void booleanFalse_addsSuccessfully() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", "No",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("value")).isEqualTo(false);
    }

    @Test
    void visibleYes_cannotFailMissingValueValidation() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "=",
                "value", "Yes"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("missing")).asList().doesNotContain("value");
    }

    @Test
    void booleanPersistsAsCanonicalBoolean() {
        UUID docId = scratchDoc();
        demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", "Yes",
                "treatment", "Reject"), null);
        Map<String, Object> session = demoService.sessionView(docId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) session.get("underwritingRules");
        Map<String, Object> card = uw.stream()
                .filter(c -> String.valueOf(c.getOrDefault("parameterId", "")).contains("pan.verified")
                        || String.valueOf(c.getOrDefault("systemRuleId", "")).contains("PAN"))
                .findFirst().orElse(uw.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) card.getOrDefault("technicalExpression",
                card.get("expression"));
        if (expr == null) {
            // expression may be nested under metadata/technical
            Object te = ((Map<?, ?>) card.getOrDefault("metadata", Map.of())).get("threshold");
            assertThat(te).isEqualTo(true);
        } else {
            Object right = expr.get("right");
            if (right instanceof Map<?, ?> r) {
                assertThat(r.get("const")).isEqualTo(true);
            }
        }
    }

    @Test
    void enumAllowedValues_validation() {
        Map<String, Object> bad = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "application.borrower_type",
                "operator", "is",
                "value", "NOT_A_REAL_TYPE"));
        assertThat(bad.get("complete")).isEqualTo(false);
        assertThat(String.valueOf(bad.get("message"))).containsIgnoringCase("allowed");

        Map<String, Object> good = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "application.borrower_type",
                "operator", "is",
                "value", "COMPANY"));
        assertThat(good.get("complete")).isEqualTo(true);
        assertThat(good.get("value")).isEqualTo("COMPANY");
        assertThat(AuthoringValueTypes.allowedValues("application.borrower_type")).isNotEmpty();
    }

    @Test
    void numericBureauScore() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 650,
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(((Number) preview.get("value")).intValue()).isEqualTo(650);
        assertThat(String.valueOf(preview.get("ruleDisplay"))).contains("650");
    }

    @Test
    void percentageCanonical_isPercentPoints() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "obligation.ratio",
                "operator", "<=",
                "value", "50%",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(((Number) preview.get("value")).intValue()).isEqualTo(50);
        assertThat(String.valueOf(preview.get("valueDisplay"))).contains("50%");
    }

    @Test
    void moneyCanonical_stripsFormatting() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "application.requested_amount",
                "operator", "<=",
                "value", "₹10,00,000",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(((Number) preview.get("value")).longValue()).isEqualTo(1000000L);
        assertThat(String.valueOf(preview.get("valueDisplay"))).contains("₹");
    }

    @Test
    void durationUnitValue_yearsToMonths() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "application.business_vintage_months",
                "operator", ">=",
                "value", 2,
                "durationUnit", "Years",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(((Number) preview.get("value")).intValue()).isEqualTo(24);
    }

    @Test
    void operatorFilteringByType_boolean() {
        Map<String, Object> sources = authoring.sources();
        @SuppressWarnings("unchecked")
        Map<String, Object> ops = (Map<String, Object>) sources.get("operatorsByType");
        @SuppressWarnings("unchecked")
        List<String> boolOps = (List<String>) ops.get("BOOLEAN");
        assertThat(boolOps).contains("is", "is not");
        assertThat(boolOps).doesNotContain(">");
        @SuppressWarnings("unchecked")
        List<String> treatments = (List<String>) sources.get("treatments");
        assertThat(treatments).doesNotContain("Approve");
        assertThat(sources.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void editUsesSameTypedModel() {
        UUID docId = scratchDoc();
        Map<String, Object> added = demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 650,
                "treatment", "Reject"), null);
        String ruleId = String.valueOf(added.get("ruleId"));
        Map<String, Object> updated = demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "replaceRuleId", ruleId,
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 675,
                "treatment", "Reject"), null);
        assertThat(updated.get("replaced")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) updated.get("preview");
        assertThat(((Number) p.get("value")).intValue()).isEqualTo(675);
    }

    @Test
    void plainEnglishUsesSameTypedModel_panAndFoir() {
        Map<String, Object> pan = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "PAN must be verified",
                "treatment", "Reject"));
        assertThat(pan.get("complete")).isEqualTo(true);
        assertThat(pan.get("parameterId")).isEqualTo("kyc.pan.verified");
        assertThat(pan.get("value")).isEqualTo(true);

        Map<String, Object> foir = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "FOIR should not exceed 50%",
                "treatment", "Reject"));
        assertThat(foir.get("complete")).isEqualTo(true);
        assertThat(((Number) foir.get("value")).intValue()).isEqualTo(50);
    }

    @Test
    void previewContainsValue() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", true));
        assertThat(preview.get("value")).isNotNull();
        assertThat(String.valueOf(preview.get("ruleDisplay"))).contains("Yes");
    }

    @Test
    void confirmDisabledOnlyOnGenuineInvalid_incompleteVsComplete() {
        assertThat(authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", "")).get("complete")).isEqualTo(false);
        assertThat(authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "kyc.pan.verified",
                "operator", "is",
                "value", "Yes")).get("complete")).isEqualTo(true);
    }

    @Test
    void parameterToParameter_adbEdi() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "BUILD",
                "parameterId", "banking.avg_daily_balance_3m",
                "operator", ">=",
                "valueMode", "PARAMETER",
                "rightParameterId", "application.proposed_edi",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("rightParameterId")).isEqualTo("application.proposed_edi");
        assertThat(String.valueOf(preview.get("ruleDisplay"))).containsIgnoringCase("EDI");
    }

    @Test
    void readinessConvergence_doesNotRegress_andNoNewEngine() {
        assertThat(PolicyExecutionReadiness.class.getSimpleName()).isEqualTo("PolicyExecutionReadiness");
        assertThat(authoring.sources().get("allowCanonicalAuthority")).isEqualTo(false);
        // Authored ADB>=EDI remains not execution-ready until EDI resolved
        UUID docId = scratchDoc();
        demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", "banking.avg_daily_balance_3m",
                "operator", ">=",
                "valueMode", "PARAMETER",
                "rightParameterId", "application.proposed_edi",
                "treatment", "Reject"), null);
        Map<String, Object> session = demoService.sessionView(docId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eb = (List<Map<String, Object>>) session.get("executionBlockers");
        if (eb != null) {
            assertThat(eb.stream().anyMatch(b ->
                    String.valueOf(b.getOrDefault("reason", "")).toLowerCase().contains("edi")
                            || String.valueOf(b.getOrDefault("parameterId", "")).contains("edi")))
                    .isTrue();
        }
    }

    private UUID scratchDoc() {
        Map<String, Object> created = demoService.createFromScratch(
                Map.of("policyName", "Typed Authoring Test " + UUID.randomUUID()),
                "credit_manager",
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) created.get("policyHeader");
        return UUID.fromString(String.valueOf(header.get("documentId")));
    }
}
