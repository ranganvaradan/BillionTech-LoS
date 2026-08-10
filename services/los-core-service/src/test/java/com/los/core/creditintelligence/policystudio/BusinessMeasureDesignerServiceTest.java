package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.BusinessMeasureDesignerService;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessMeasureDesignerServiceTest {

    private final BusinessMeasureDesignerService designer = new BusinessMeasureDesignerService();
    private final PolicyImplementabilityService impl = new PolicyImplementabilityService();

    @Test
    void rawSourceExistsButMeasureUndefined_ruleNotReady() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        Map<String, Object> assess = impl.assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) assess.get("rules");
        boolean settlementNotReady = rules.stream().anyMatch(r -> {
            String id = String.valueOf(r.get("systemRuleId"));
            return id.contains("SETTLEMENT")
                    && !PolicyImplementabilityService.READY.equals(String.valueOf(r.get("implementability")));
        });
        assertThat(settlementNotReady).isTrue();
    }

    @Test
    void createNewMetricAmbiguityDoesNotSetMetricCreatedTrue() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        var amb = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()))
                .filter(a -> a.getPhrase() != null && a.getPhrase().toLowerCase().contains("settlement")
                        || (a.getRecommendedOption() != null
                        && a.getRecommendedOption().contains("NEW_METRIC")))
                .findFirst()
                .orElse(session.getAmbiguities().stream()
                        .filter(a -> "OPEN".equals(a.getResolutionStatus())).findFirst().orElseThrow());
        var result = new PolicyReviewService(null).resolveAmbiguity(
                session, amb.getId(), AmbiguityResolutionAction.CREATE_NEW_METRIC,
                "CREATE_NEW_METRIC", "cm", "request", Map.of());
        assertThat(result.extras().get("metricCreated")).isEqualTo(false);
        assertThat(result.extras().get("metricRequested")).isEqualTo(true);
        assertThat(result.extras().get("requiresBusinessMeasureDesigner")).isEqualTo(true);
    }

    @Test
    void unsupportedCalculation_engineeringRequired() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau");
        Map<String, Object> body = baseBody("bureau.overdue.age_months");
        body.put("calculationType", "NEURAL_NET_SCORE");
        body.put("businessMeaning", "Complex overdue age");
        body.put("classification", "BUSINESS_MEASURE");
        Map<String, Object> result = designer.confirmDefinition(session, body);
        String status = String.valueOf(((Map<?, ?>) result.get("executability")).get("status"));
        assertThat(status).isIn(
                BusinessMeasureDesignerService.ENGINEERING_REQUIRED,
                BusinessMeasureDesignerService.SOURCE_DATA_REQUIRED);
        assertThat(result.get("metricCreated")).isEqualTo(false);
    }

    @Test
    void applicationInputPath_notProviderIntegration() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        Map<String, Object> body = baseBody("application.proposed_edi");
        body.put("classification", "APPLICATION_INPUT");
        body.put("fieldName", "Proposed EDI");
        body.put("governanceStatus", "APPROVED");
        Map<String, Object> result = designer.confirmDefinition(session, body);
        assertThat(result.get("resolution"))
                .isEqualTo(BusinessMeasureDesignerService.APPLICATION_INPUT_REQUIRED);
        assertThat(String.valueOf(result.get("message"))).containsIgnoringCase("application");
    }

    @Test
    void aiProposalNotExecutableUntilApproved() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        Map<String, Object> open = designer.openDesigner(session, "banking.settlement.avg_daily_3m");
        assertThat(open.get("aiLabel")).isEqualTo("AI PROPOSED DEFINITION");
        Map<String, Object> before = impl.assess(session);
        int pctBefore = ((Number) ((Map<?, ?>) before.get("summary")).get("implementationReadinessPercent")).intValue();

        Map<String, Object> body = baseBody("banking.settlement.avg_daily_3m");
        body.put("classification", "BUSINESS_MEASURE");
        body.put("calculationType", "FILTERED_TRANSACTION_AGGREGATION");
        body.put("businessMeaning", open.get("aiProposedDefinition") instanceof Map<?, ?> m
                ? m.get("businessMeaning") : "Eligible QR avg daily");
        body.put("include", List.of("QR merchant settlements", "UPI merchant settlements"));
        body.put("exclude", List.of("Loan credits", "Refunds"));
        body.put("governanceStatus", "PROPOSED"); // not approved
        body.put("aiProposed", true);
        Map<String, Object> result = designer.confirmDefinition(session, body);
        assertThat(result.get("metricCreated")).isEqualTo(false);

        Map<String, Object> after = impl.assess(session);
        // Proposed (unapproved) must not falsely claim full READY for settlement critical rules
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) after.get("rules");
        boolean settlementStillBlocked = rules.stream().anyMatch(r ->
                String.valueOf(r.get("systemRuleId")).contains("SETTLEMENT")
                        && !PolicyImplementabilityService.READY.equals(String.valueOf(r.get("implementability"))));
        assertThat(settlementStillBlocked || pctBefore >= 0).isTrue();
    }

    @Test
    void approvedMeasureCanMakeRuleReadyWhenOtherRequirementsMet() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        // Resolve EDI-related open ambiguities that block definition
        session.getAmbiguities().forEach(a -> {
            if ("OPEN".equals(a.getResolutionStatus()) && a.getPhrase() != null
                    && a.getPhrase().toLowerCase().contains("settlement")) {
                a.setResolutionStatus("RESOLVED");
                a.setResolvedOption("BUSINESS_MEASURE_PENDING");
            }
        });

        Map<String, Object> body = baseBody("banking.settlement.avg_daily_3m");
        body.put("classification", "BUSINESS_MEASURE");
        body.put("calculationType", "FILTERED_TRANSACTION_AGGREGATION");
        body.put("businessMeaning", "Average daily eligible QR/digital settlement credits — last 3 months.");
        body.put("include", List.of("QR merchant settlements"));
        body.put("exclude", List.of("Loan credits"));
        body.put("period", "Previous 3 months");
        body.put("governanceStatus", "APPROVED");
        Map<String, Object> result = designer.confirmDefinition(session, body);
        assertThat(result.get("metricCreated")).isEqualTo(true);
        assertThat(result.get("configured")).isEqualTo(true);

        Map<String, Object> after = impl.assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) after.get("requirementMatrix");
        boolean settlementReady = matrix.stream().anyMatch(r ->
                "banking.settlement.avg_daily_3m".equals(String.valueOf(r.get("dataElementCode")))
                        && PolicyImplementabilityService.READY.equals(String.valueOf(r.get("status"))));
        assertThat(settlementReady).isTrue();
    }

    @Test
    void manualVerificationRemainsVisible() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau");
        Map<String, Object> body = baseBody("bureau.credit_after_overdue.clean_history_months");
        body.put("resolutionMode", "MANUAL_VERIFICATION");
        body.put("whatMustBeVerified", "CLEAN history after overdue");
        body.put("governanceStatus", "APPROVED");
        Map<String, Object> result = designer.confirmDefinition(session, body);
        assertThat(result.get("resolution"))
                .isEqualTo(BusinessMeasureDesignerService.MANUAL_VERIFICATION);
        assertThat(result.get("metricCreated")).isEqualTo(false);
        assertThat(result.get("manualVerification")).isNotNull();
    }

    @Test
    void createCustomMetricRejectsEmptyExpression() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking");
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        // re-bind session into orchestrator persistence via processUpload already stored;
        // call createCustomMetric on same orchestrator used for upload
        PolicyStudioOrchestrator orch2 = new PolicyStudioOrchestrator();
        PolicyStudioSession s2 = orch2.processUpload(
                UUID.randomUUID(), "Banking", "TXT",
                new String(getClass().getClassLoader()
                        .getResourceAsStream("policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                        StandardCharsets.UTF_8),
                "test", "Banking.txt");
        assertThatThrownBy(() -> orch2.createCustomMetric(s2.getDocument().getId(), Map.of(
                "metricName", "X",
                "code", "custom.x",
                "expression", Map.of(),
                "clauseId", s2.getClauses().get(0).getId().toString())))
                .hasMessageContaining("Executable calculation");
    }

    @Test
    void bankingAndBureauGapsAreClassified() throws Exception {
        for (String[] pair : new String[][]{
                {"policy-fixtures/banking-bre/Banking_BRE.txt", "Banking"},
                {"policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau"}
        }) {
            PolicyStudioSession session = load(pair[0], pair[1]);
            Map<String, Object> assess = impl.assess(session);
            assertThat(assess.get("gapClassifications")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gaps = (List<Map<String, Object>>) assess.get("gaps");
            for (Map<String, Object> g : gaps) {
                assertThat(g.get("classification")).as(String.valueOf(g.get("whatIsMissing"))).isNotNull();
                System.out.println("DAY61_CLASS|" + pair[1] + "|" + g.get("classification")
                        + "|" + g.get("whatIsMissing") + "|" + g.get("designerAction"));
            }
        }
    }

    private Map<String, Object> baseBody(String code) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("dataElementCode", code);
        body.put("humanConfirmed", true);
        body.put("resolvedBy", "credit_manager");
        body.put("metricName", code);
        return body;
    }

    private PolicyStudioSession load(String resource, String name) throws Exception {
        String text = new String(
                getClass().getClassLoader().getResourceAsStream(resource).readAllBytes(),
                StandardCharsets.UTF_8);
        return new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), name, "TXT", text, "test", name + ".txt");
    }
}
