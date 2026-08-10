package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityIngestionBindingServiceTest {

    private CapabilityIngestionMatcher matcher;
    private CapabilityIngestionBindingService binder;
    private PolicyStudioOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        matcher = new CapabilityIngestionMatcher(catalogue);
        binder = new CapabilityIngestionBindingService(matcher, catalogue);
        orchestrator = new PolicyStudioOrchestrator();
        orchestrator.setIngestionBindingService(binder);
    }

    @Test
    void fiveClause_preservesExplicitThresholds_notHardcodedDefaults() throws Exception {
        String text = readFixture("policy-fixtures/ingestion/five-clause-simple.txt");
        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "Five clause", "TXT", text, "test", null);

        assertThat(param(session, "BUREAU.MIN_SCORE", "minimumScore")).isEqualTo(700L);
        assertThat(param(session, "ELIG.BUSINESS_VINTAGE_MIN", "minimumValue")).isEqualTo(24L);
        assertThat(param(session, "ELIG.BUSINESS_VINTAGE_MIN", "unit")).isEqualTo("MONTHS");
        assertThat(param(session, "FIN.FOIR_MAX", "maximumPercentage")).isEqualTo(50L);
        assertThat(param(session, "BANK.TURNOVER_PCT_GST_MIN", "minimumPercentage")).isEqualTo(75L);
        assertThat(param(session, "BANK.CHEQUE_BOUNCE_MAX", "windowMonths")).isEqualTo(3L);
        assertThat(param(session, "BANK.CHEQUE_BOUNCE_MAX", "maximumCount")).isEqualTo(0L);

        // Must not fall back to hardcoded 650 when text says 700
        assertThat(param(session, "BUREAU.MIN_SCORE", "minimumScore")).isNotEqualTo(650L);
    }

    @Test
    void bureau700_dscr_crore_and_classifications() {
        assertThat(matcher.match("Bureau score should be minimum 700").extractedParameters().get("minimumScore"))
                .isEqualTo(700L);
        assertThat(matcher.match("FOIR should not exceed 50%").extractedParameters().get("maximumPercentage"))
                .isEqualTo(50L);
        assertThat(matcher.match("Banking turnover should be at least 75% of GST turnover")
                .extractedParameters().get("minimumPercentage")).isEqualTo(75L);
        assertThat(matcher.match("No cheque bounce during preceding three months")
                .extractedParameters()).containsEntry("windowMonths", 3L).containsEntry("maximumCount", 0L);
        assertThat(matcher.match("Business should have operated for at least 24 months")
                .extractedParameters()).containsEntry("minimumValue", 24L).containsEntry("unit", "MONTHS");
        assertThat(matcher.match("DSCR should be minimum 1.25").extractedParameters().get("minimumRatio"))
                .isEqualTo(new java.math.BigDecimal("1.25"));
        assertThat(matcher.match("Maximum exposure shall be ₹1 crore").extractedParameters().get("maximumAmount"))
                .isEqualTo(10_000_000L);

        assertThat(matcher.match("Management quality should be satisfactory").classification())
                .isEqualTo(IngestionMatchClassification.MANUAL_REVIEW);
        assertThat(matcher.match("Promoter experience must be at least 10 years").classification())
                .isEqualTo(IngestionMatchClassification.MANUAL_INPUT);
        assertThat(matcher.match("Copy of PAN, GST returns and bank statements must be submitted").classification())
                .isEqualTo(IngestionMatchClassification.DOCUMENT_REQUIREMENT);
        assertThat(matcher.match("Per-anchor concentration caps apply").classification())
                .isEqualTo(IngestionMatchClassification.PORTFOLIO_CONTROL);
        assertThat(matcher.match("SMA classification and NPA norms apply post disbursement").classification())
                .isEqualTo(IngestionMatchClassification.SERVICING_RULE);
        assertThat(matcher.match("This policy document shall be read with the authority statements").classification())
                .isEqualTo(IngestionMatchClassification.NARRATIVE);
    }

    @Test
    void duplicateAndConflictDetection() {
        PolicyStudioSession session = new PolicyStudioSession();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("dup")
                .documentType("TXT")
                .contentHash("x")
                .status("PARSED")
                .documentVersion(1)
                .sourceText("x")
                .uploadedBy("t")
                .metadata(new java.util.LinkedHashMap<>())
                .build();
        session.setDocument(doc);

        // Simulate two clauses already extracted via orchestrator path
        String text = "Bureau score should be minimum 700.\nCIBIL must be 700 or higher.\nBureau score must be at least 650.";
        PolicyStudioSession bound = orchestrator.processUpload(
                UUID.randomUUID(), "dup-conflict", "TXT", text, "test", null);

        long bureau = bound.getRuleCandidates().stream()
                .filter(r -> "BUREAU.MIN_SCORE".equals(String.valueOf(
                        r.getMetadata() == null ? null : r.getMetadata().get("businessCapabilityId"))))
                .count();
        assertThat(bureau).isGreaterThanOrEqualTo(2);
        boolean flagged = bound.getRuleCandidates().stream().anyMatch(r ->
                Boolean.TRUE.equals(r.getMetadata().get("potentialDuplicate"))
                        || Boolean.TRUE.equals(r.getMetadata().get("capabilityConflict")));
        assertThat(flagged).isTrue();
    }

    @Test
    void acceptAllReady_onlyHighConfidenceCatalogue() throws Exception {
        String text = readFixture("policy-fixtures/ingestion/five-clause-simple.txt");
        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "accept-all", "TXT", text, "test", null);
        long eligible = session.getRuleCandidates().stream()
                .filter(r -> {
                    Map<String, Object> m = r.getMetadata();
                    return m != null
                            && Boolean.TRUE.equals(m.get("catalogueBacked"))
                            && "HIGH".equals(m.get("matchConfidence"))
                            && !Boolean.TRUE.equals(m.get("NEEDS_INPUT"))
                            && !Boolean.TRUE.equals(m.get("excludedFromActivation"));
                })
                .count();
        assertThat(eligible).isGreaterThanOrEqualTo(5);

        // Document / narrative never eligible
        assertThat(session.getRuleCandidates().stream().noneMatch(r -> {
            Map<String, Object> m = r.getMetadata();
            return m != null && Boolean.TRUE.equals(m.get("classificationOnly"))
                    && Boolean.TRUE.equals(m.get("activationIncluded"));
        })).isTrue();
    }

    @Test
    void scfSample_classifiesUnderwritingAndNonUnderwriting() throws Exception {
        String text = readFixture("policy-fixtures/ingestion/scf-policy-sample.txt");
        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "SCF sample", "TXT", text, "test", null);

        assertThat(find(session, "BUREAU.MIN_SCORE")).isPresent();
        assertThat(param(session, "BUREAU.MIN_SCORE", "minimumScore")).isEqualTo(650L);
        assertThat(param(session, "LIMIT.ABS_CAP", "maximumAmount")).isEqualTo(10_000_000L);
        assertThat(param(session, "FIN.DSCR_MIN", "minimumRatio")).isEqualTo(new java.math.BigDecimal("1.25"));

        Map<String, Object> summary = summaryOf(session);
        assertThat(((Number) summary.get("existingAutomatedCapabilities")).intValue()).isGreaterThanOrEqualTo(10);
        assertThat(((Number) summary.get("documentRequirements")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) summary.get("portfolioControls")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) summary.get("servicingOrNarrative")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("allowCanonicalAuthority")).isEqualTo(false);

        // Provenance preserved
        CiPolicyRuleCandidate bureau = find(session, "BUREAU.MIN_SCORE").orElseThrow();
        assertThat(bureau.getLineage().get("sourceText")).isNotNull();
        assertThat(bureau.getMetadata().get("provenance")).isEqualTo("DOCUMENT_INGESTION_MATCH");
    }

    @Test
    void saveDraft_notBlockedByUnresolvedClassifications() throws Exception {
        String text = readFixture("policy-fixtures/ingestion/scf-policy-sample.txt");
        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "SCF draft", "TXT", text, "test", null);
        // Presence of documents/servicing/manual must not remove catalogue rules
        assertThat(session.getRuleCandidates()).isNotEmpty();
        assertThat(session.getDocument().getStatus()).isIn("DRAFT_READY", "REVIEW_REQUIRED");
        assertThat(summaryOf(session).get("primaryCta")).isEqualTo("Save Draft");
    }

    @Test
    void allowCanonicalAuthority_remainsFalse() throws Exception {
        String text = "Bureau score should be minimum 700.";
        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "auth", "TXT", text, "test", null);
        CiPolicyRuleCandidate r = find(session, "BUREAU.MIN_SCORE").orElseThrow();
        assertThat(r.getMetadata().get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(summaryOf(session).get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void parameterDiffers_whenPolicyDiffersFromCatalogueDefault() {
        CapabilityIngestionMatcher.MatchResult m = matcher.match("Bureau score should be minimum 700");
        assertThat(m.parameterDiffers()).isTrue();
        assertThat(m.classification()).isEqualTo(IngestionMatchClassification.EXISTING_CAPABILITY_PARAMETER_CHANGE);
    }

    private static String readFixture(String path) throws Exception {
        try (var in = CapabilityIngestionBindingServiceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Object param(PolicyStudioSession session, String capId, String key) {
        return find(session, capId)
                .map(r -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) r.getMetadata().get("parameters");
                    return p == null ? null : p.get(key);
                })
                .orElse(null);
    }

    private static java.util.Optional<CiPolicyRuleCandidate> find(PolicyStudioSession session, String capId) {
        return session.getRuleCandidates().stream()
                .filter(r -> r.getMetadata() != null
                        && Objects.equals(capId, String.valueOf(r.getMetadata().get("businessCapabilityId"))))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summaryOf(PolicyStudioSession session) {
        Object s = session.getPreview() == null ? null : session.getPreview().get("ingestionBinding");
        if (s instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (session.getDocument() != null && session.getDocument().getMetadata() != null) {
            Object d = session.getDocument().getMetadata().get("ingestionBinding");
            if (d instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        }
        return Map.of();
    }
}
