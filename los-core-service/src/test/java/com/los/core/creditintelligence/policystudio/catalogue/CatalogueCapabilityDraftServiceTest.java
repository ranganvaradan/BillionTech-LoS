package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueCapabilityDraftServiceTest {

    private CatalogueCapabilityDraftService service;
    private CreditCapabilityCatalogueService catalogue;
    private PolicyStudioSession session;

    @BeforeEach
    void setUp() {
        catalogue = new CreditCapabilityCatalogueService();
        service = new CatalogueCapabilityDraftService(catalogue);
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("UX-2C draft")
                .documentType("TXT")
                .contentHash("ux2c")
                .status("DRAFT_READY")
                .documentVersion(1)
                .sourceText("manual draft")
                .uploadedBy("test")
                .metadata(new LinkedHashMap<>())
                .build();
        session = new PolicyStudioSession();
        session.setDocument(doc);
    }

    @Test
    void addBureauMinScore_persistsParametersAndAcceptedDisposition() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessCapabilityId", "BUREAU.MIN_SCORE");
        body.put("parameters", Map.of("minimumScore", 700));
        body.put("failureTreatment", "REJECT");

        CatalogueCapabilityDraftService.DraftMutation m = service.addOrUpdate(session, body);
        assertThat(m.created()).isTrue();
        CiPolicyRuleCandidate rule = m.rule();
        assertThat(rule.getMetadata().get("businessCapabilityId")).isEqualTo("BUREAU.MIN_SCORE");
        assertThat(rule.getMetadata().get("source")).isEqualTo("MANUAL_CATALOGUE_ADD");
        assertThat(rule.getMetadata().get("disposition")).isEqualTo("ACCEPTED");
        assertThat(rule.getMetadata().get("activationIncluded")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) rule.getMetadata().get("parameters");
        assertThat(params.get("minimumScore")).isEqualTo(700);
        assertThat(rule.getExpression().get("op")).isEqualTo("LT");
        assertThat(rule.getOnTrue()).isEqualTo("FAIL");
        assertThat(session.getRuleCandidates()).hasSize(1);
    }

    @Test
    void editBureauScore_updatesInPlace() {
        service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "BUREAU.MIN_SCORE",
                "parameters", Map.of("minimumScore", 700),
                "failureTreatment", "REJECT"));
        UUID ruleId = session.getRuleCandidates().get(0).getId();

        CatalogueCapabilityDraftService.DraftMutation edited = service.addOrUpdate(session, Map.of(
                "ruleId", ruleId.toString(),
                "businessCapabilityId", "BUREAU.MIN_SCORE",
                "parameters", Map.of("minimumScore", 725),
                "failureTreatment", "MANUAL_REVIEW"));

        assertThat(edited.created()).isFalse();
        assertThat(session.getRuleCandidates()).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) edited.rule().getMetadata().get("parameters");
        assertThat(params.get("minimumScore")).isEqualTo(725);
        assertThat(edited.rule().getOnTrue()).isEqualTo("REFER");
        assertThat(edited.rule().getMetadata().get("disposition")).isEqualTo("EDITED");
    }

    @Test
    void chequeBounce_windowAndCount() {
        CatalogueCapabilityDraftService.DraftMutation m = service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "BANK.CHEQUE_BOUNCE_MAX",
                "parameters", Map.of("windowMonths", 3, "maximumCount", 0),
                "failureTreatment", "REJECT"));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) m.rule().getMetadata().get("parameters");
        assertThat(params.get("windowMonths")).isEqualTo(3);
        assertThat(params.get("maximumCount")).isEqualTo(0);
    }

    @Test
    void treatmentManualReview_mapsToRefer() {
        CatalogueCapabilityDraftService.DraftMutation m = service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "FIN.FOIR_MAX",
                "parameters", Map.of("maximumPercentage", 50),
                "failureTreatment", "MANUAL_REVIEW"));
        assertThat(m.rule().getOnTrue()).isEqualTo("REFER");
        assertThat(m.rule().getMetadata().get("failureTreatment")).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void manualInput_whenAllowed() {
        CatalogueCapabilityDraftService.DraftMutation m = service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "ELIG.BUSINESS_VINTAGE_MIN",
                "parameters", Map.of("minimumValue", 24, "unit", "MONTHS"),
                "failureTreatment", "REJECT",
                "useManualInput", true,
                "manualInputLabel", "Years in business",
                "manualInputType", "NUMBER"));
        assertThat(m.rule().getMetadata().get("disposition")).isEqualTo("MANUAL_INPUT");
        assertThat(m.rule().getMetadata().get("verificationMode")).isEqualTo("MANUAL");
        assertThat(m.rule().getMetadata().get("manualInputLabel")).isEqualTo("Years in business");
        assertThat(String.valueOf(m.rule().getMetadata().get("businessSummary"))).contains("24 months");
    }

    @Test
    void invalidPercentage_rejected() {
        assertThatThrownBy(() -> service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "FIN.FOIR_MAX",
                "parameters", Map.of("maximumPercentage", 150),
                "failureTreatment", "REJECT")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("0 and 100");
    }

    @Test
    void unsupportedTreatment_rejected() {
        assertThatThrownBy(() -> service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "BUREAU.MIN_SCORE",
                "parameters", Map.of("minimumScore", 700),
                "failureTreatment", "LIMIT_ADJUSTMENT")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void fiveRuleWalkthrough_allPersist() {
        List<Map<String, Object>> rules = List.of(
                Map.of("businessCapabilityId", "BUREAU.MIN_SCORE",
                        "parameters", Map.of("minimumScore", 700), "failureTreatment", "REJECT"),
                Map.of("businessCapabilityId", "ELIG.BUSINESS_VINTAGE_MIN",
                        "parameters", Map.of("minimumValue", 24, "unit", "MONTHS"), "failureTreatment", "REJECT"),
                Map.of("businessCapabilityId", "FIN.FOIR_MAX",
                        "parameters", Map.of("maximumPercentage", 50), "failureTreatment", "REJECT"),
                Map.of("businessCapabilityId", "BANK.TURNOVER_PCT_GST_MIN",
                        "parameters", Map.of("minimumPercentage", 75), "failureTreatment", "REJECT"),
                Map.of("businessCapabilityId", "BANK.CHEQUE_BOUNCE_MAX",
                        "parameters", Map.of("windowMonths", 3, "maximumCount", 0), "failureTreatment", "REJECT"));
        for (Map<String, Object> body : rules) {
            service.addOrUpdate(session, body);
        }
        assertThat(session.getRuleCandidates()).hasSize(5);
        assertThat(session.getRuleCandidates())
                .extracting(r -> r.getMetadata().get("businessCapabilityId"))
                .containsExactlyInAnyOrder(
                        "BUREAU.MIN_SCORE",
                        "ELIG.BUSINESS_VINTAGE_MIN",
                        "FIN.FOIR_MAX",
                        "BANK.TURNOVER_PCT_GST_MIN",
                        "BANK.CHEQUE_BOUNCE_MAX");
    }

    @Test
    void allowCanonicalAuthority_remainsFalseOnMetadata() {
        CatalogueCapabilityDraftService.DraftMutation m = service.addOrUpdate(session, Map.of(
                "businessCapabilityId", "LIMIT.ABS_CAP",
                "parameters", Map.of("maximumAmount", 10_000_000),
                "failureTreatment", "REJECT"));
        assertThat(m.rule().getMetadata().get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void scfRepresentability_allPresent() {
        Map<String, Object> result = catalogue.scfRepresentability();
        assertThat(result.get("missing")).asList().isEmpty();
        assertThat(((Number) result.get("representableCount")).intValue())
                .isEqualTo(((Number) result.get("requiredCount")).intValue());
    }

    @Test
    void primaryCatalogue_hidesEligBureauAlias() {
        Map<String, Object> primary = catalogue.catalogueView(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> caps = (List<Map<String, Object>>) primary.get("capabilities");
        assertThat(caps).noneMatch(c -> "ELIG.MIN_BUREAU_SCORE".equals(c.get("businessCapabilityId")));
        assertThat(caps).noneMatch(c -> "BUREAU.MIN_SCORE".equals(c.get("businessCapabilityId")));
        assertThat(caps).anyMatch(c -> "bureau.score".equals(c.get("businessCapabilityId")));

        Map<String, Object> advanced = catalogue.catalogueView(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> advCaps = (List<Map<String, Object>>) advanced.get("capabilities");
        assertThat(advCaps).noneMatch(c -> "ELIG.MIN_BUREAU_SCORE".equals(c.get("businessCapabilityId")));
        assertThat(advCaps).noneMatch(c -> "BUREAU.MIN_SCORE".equals(c.get("businessCapabilityId")));
        assertThat(advCaps).anyMatch(c -> "bureau.score".equals(c.get("businessCapabilityId")));
        assertThat(catalogue.listCapabilities())
                .extracting(BusinessCapability::businessCapabilityId)
                .contains("BUREAU.MIN_SCORE");
    }

    @Test
    void search_findsFoirAndCibilAliases() {
        Map<String, Object> foir = catalogue.search("FOIR", false);
        assertThat(((Number) foir.get("matchCount")).intValue()).isGreaterThanOrEqualTo(1);
        Map<String, Object> cibil = catalogue.search("cibil", false);
        assertThat(((Number) cibil.get("matchCount")).intValue()).isGreaterThanOrEqualTo(1);
    }
}
