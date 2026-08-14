package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STAGING-POLICY-ACTION-PARITY-P0 — session and catalogue landing actions share one resolver.
 */
class PolicyLandingActionsParityTest {

    private PolicyLifecycleService lifecycle;
    private PolicyStudioSession session;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        lifecycle = new PolicyLifecycleService(new PolicyApplicabilityResolver(), null, props, null);
        session = new PolicyStudioSession();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("Parity Policy")
                .documentType("TXT")
                .contentHash("hash")
                .status(DocumentStatus.DRAFT_READY.name())
                .documentVersion(1)
                .sourceText("sample")
                .metadata(new LinkedHashMap<>())
                .build();
        session.setDocument(doc);
    }

    @Test
    void approved_sessionAndCatalogue_match_includeRetire() {
        setSessionStatus(PolicyBusinessLifecycleStatus.APPROVED);
        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "APPROVED",
                "applicabilityId", UUID.randomUUID().toString(),
                "documentId", session.documentId().toString()));
        assertThat(sessionActs).containsExactly("OPEN", "COPY", "RETIRE");
        assertThat(catalogueActs).isEqualTo(sessionActs);
    }

    @Test
    void active_sessionAndCatalogue_match_includeRetire() {
        setSessionStatus(PolicyBusinessLifecycleStatus.ACTIVE);
        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "ACTIVE",
                "applicabilityId", UUID.randomUUID().toString()));
        assertThat(sessionActs).containsExactly("OPEN", "COPY", "RETIRE");
        assertThat(catalogueActs).isEqualTo(sessionActs);
    }

    @Test
    void scheduled_sessionAndCatalogue_match_includeRetire() {
        setSessionStatus(PolicyBusinessLifecycleStatus.SCHEDULED);
        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "SCHEDULED",
                "applicabilityId", UUID.randomUUID().toString()));
        assertThat(sessionActs).isEqualTo(catalogueActs);
        assertThat(catalogueActs).contains("RETIRE").doesNotContain("DELETE");
    }

    @Test
    void retired_noRetire_noDelete() {
        setSessionStatus(PolicyBusinessLifecycleStatus.RETIRED);
        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "RETIRED",
                "applicabilityId", UUID.randomUUID().toString()));
        assertThat(sessionActs).containsExactly("OPEN", "COPY");
        assertThat(catalogueActs).isEqualTo(sessionActs);
        assertThat(catalogueActs).doesNotContain("RETIRE", "DELETE");
    }

    @Test
    void superseded_noRetire_noDelete() {
        setSessionStatus(PolicyBusinessLifecycleStatus.SUPERSEDED);
        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "SUPERSEDED",
                "applicabilityId", UUID.randomUUID().toString()));
        assertThat(sessionActs).containsExactly("OPEN", "COPY");
        assertThat(catalogueActs).isEqualTo(sessionActs);
    }

    @Test
    void unprotectedDraft_sessionAllowsDelete_catalogueWithApplicabilityDoesNot() {
        setSessionStatus(PolicyBusinessLifecycleStatus.DRAFT);
        List<String> sessionActs = lifecycle.landingActions(session);
        assertThat(sessionActs).containsExactly("OPEN", "COPY", "DELETE");

        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "DRAFT",
                "applicabilityId", UUID.randomUUID().toString()));
        // Catalogue governance / no session → DELETE must not be invented.
        assertThat(catalogueActs).containsExactly("OPEN", "COPY");
        assertThat(catalogueActs).doesNotContain("DELETE", "RETIRE");
    }

    @Test
    void protectedDraft_withDurableApplicability_noDelete_sessionAndCatalogue() {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        life.put("durableApplicabilityId", UUID.randomUUID().toString());
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(PolicyLifecycleService.META_KEY, life)));

        List<String> sessionActs = lifecycle.landingActions(session);
        List<String> catalogueActs = lifecycle.landingActionsForCatalogueRow(Map.of(
                "status", "DRAFT",
                "applicabilityId", life.get("durableApplicabilityId")));
        assertThat(sessionActs).containsExactly("OPEN", "COPY");
        assertThat(catalogueActs).isEqualTo(sessionActs);
        assertThat(sessionActs).doesNotContain("DELETE");
    }

    private void setSessionStatus(String status) {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("businessStatus", status);
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(PolicyLifecycleService.META_KEY, life)));
    }
}
