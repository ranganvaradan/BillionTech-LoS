package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-UX-1: Credit Manager draft-first rule review behaviours.
 */
class CreditManagerRuleReviewUxTest {

    @Test
    void ignoredAndDeletedRules_excludedFromImplementabilityBlockers() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking BRE", "TXT", text, "test", "Banking_BRE.txt");
        assertThat(session.getRuleCandidates()).isNotEmpty();

        CiPolicyRuleCandidate first = session.getRuleCandidates().get(0);
        Map<String, Object> meta = new LinkedHashMap<>(
                first.getMetadata() == null ? Map.of() : first.getMetadata());
        meta.put("disposition", "IGNORED");
        meta.put("excludedFromActivation", true);
        first.setMetadata(meta);
        first.setReviewStatus(ReviewState.REJECTED.name());

        assertThat(PolicyImplementabilityService.isExcludedFromActivation(first)).isTrue();

        Map<String, Object> assess = new PolicyImplementabilityService().assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) assess.get("rules");
        assertThat(rows.stream().anyMatch(r ->
                "EXCLUDED".equals(r.get("status"))
                        && String.valueOf(first.getId()).equals(String.valueOf(r.get("ruleId"))))).isTrue();
    }

    @Test
    void saveDraft_worksWithOpenMaterialAmbiguity() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking BRE", "TXT", text, "test", "Banking_BRE.txt");

        long openMat = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .count();
        // Fixture may or may not have MATERIAL opens; inject one to prove draft save is ungated.
        if (openMat == 0) {
            UUID clauseId = session.getClauses().isEmpty()
                    ? UUID.randomUUID()
                    : session.getClauses().get(0).getId();
            session.getAmbiguities().add(CiPolicyAmbiguity.builder()
                    .id(UUID.randomUUID())
                    .clauseId(clauseId)
                    .phrase("EDI")
                    .description("EDI definition unresolved for draft-first save test")
                    .severity("MATERIAL")
                    .resolutionStatus("OPEN")
                    .ambiguityType("UNKNOWN_BUSINESS_TERM")
                    .build());
        }

        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        PolicyLifecycleService lifecycle = new PolicyLifecycleService(
                new PolicyApplicabilityResolver(), null, props, null);

        Map<String, Object> saved = lifecycle.saveDraft(session, Map.of(
                "reasonForChange", "Credit Manager draft with unresolved ambiguity"));
        assertThat(saved.get("message")).asString().containsIgnoringCase("saved");
        assertThat(String.valueOf(saved.get("businessStatus"))).isEqualTo("DRAFT");
    }

    @Test
    void ruleCards_showIgnoredManualInputAcceptedStatuses() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking BRE", "TXT", text, "test", "Banking_BRE.txt");
        List<CiPolicyRuleCandidate> rules = session.getRuleCandidates();
        assertThat(rules.size()).isGreaterThanOrEqualTo(2);

        Map<String, Object> ignored = new LinkedHashMap<>(
                rules.get(0).getMetadata() == null ? Map.of() : rules.get(0).getMetadata());
        ignored.put("disposition", "IGNORED");
        ignored.put("excludedFromActivation", true);
        rules.get(0).setMetadata(ignored);

        Map<String, Object> manual = new LinkedHashMap<>(
                rules.get(1).getMetadata() == null ? Map.of() : rules.get(1).getMetadata());
        manual.put("disposition", "MANUAL_INPUT");
        manual.put("verificationMode", "MANUAL");
        manual.put("dataGapDisposition", "MANUAL_VERIFICATION");
        rules.get(1).setMetadata(manual);

        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, session, Map.of("demo", true, "kind", "banking"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) out.get("ruleCards");
        assertThat(cards.stream().anyMatch(c -> "Ignored".equals(c.get("status")))).isTrue();
        assertThat(cards.stream().anyMatch(c -> "Manual Input".equals(c.get("status")))).isTrue();
        assertThat(cards.stream().anyMatch(c -> c.get("businessGroup") != null)).isTrue();
    }

    @Test
    void allowCanonicalAuthority_remainsFalse() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        assertThat(props.getCutover().isAllowCanonicalAuthority()).isFalse();
    }
}
