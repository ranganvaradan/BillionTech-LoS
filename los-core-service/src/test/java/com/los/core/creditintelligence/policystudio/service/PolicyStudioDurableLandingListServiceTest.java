package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyBusinessLifecycleStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-STUDIO-DURABLE-LANDING-LIST-1 — cold-start membership + enrichment goldens.
 */
class PolicyStudioDurableLandingListServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID VIKASAM_ID = UUID.fromString("4543e643-c3a0-4a57-a92c-370dff8b2fa9");
    private static final UUID CLEAN_A = UUID.fromString("34e80644-8a77-4d12-aae9-1aed0cd89f10");
    private static final UUID CLEAN_B = UUID.fromString("a622c7da-2c41-48ce-b63e-f45c003b648f");

    private PolicyStudioDurableLandingListService service;
    private PolicyStudioPersistenceService persistence;

    @BeforeEach
    void setUp() {
        service = new PolicyStudioDurableLandingListService();
        persistence = new PolicyStudioPersistenceService();
        persistence.clearAllForTests();
    }

    @Test
    void durableDraft_noApplicability_noSession_isListed() {
        CiPolicyDocument doc = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        CiPolicyStudioSessionSnapshot snap = snapshot(VIKASAM_ID, 13);

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT,
                List.of(doc),
                List.of(snap),
                List.of(),
                List.of(),
                Map.of(),
                null,
                Map.of());

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("documentId")).isEqualTo(VIKASAM_ID.toString());
        assertThat(row.get("policyName")).isEqualTo("Vikasam Bureau");
        assertThat(row.get("underwritingRuleCount")).isEqualTo(13L);
        assertThat(row.get("sessionExists")).isEqualTo(false);
        assertThat(String.valueOf(row.get("scopeSummary"))).containsIgnoringCase("Not yet configured");
        assertThat(persistence.listAllSessions()).isEmpty();
    }

    @Test
    void durableDraft_afterRestart_isListed_withoutOpenById() {
        CiPolicyDocument doc = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        CiPolicyStudioSessionSnapshot snap = snapshot(VIKASAM_ID, 13);

        // Pretend a prior session existed then JVM restarted.
        PolicyStudioSession prior = sessionWithRules(doc, 13);
        persistence.saveSessionSnapshot(prior);
        persistence.simulateProcessRestart();
        assertThat(persistence.listAllSessions()).isEmpty();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT,
                List.of(doc),
                List.of(snap),
                List.of(),
                List.of(graph(VIKASAM_ID, 13)),
                Map.of(),
                null,
                Map.of());

        assertThat(rows.stream().map(r -> r.get("documentId"))).containsExactly(VIKASAM_ID.toString());
        assertThat(rows.get(0).get("underwritingRuleCount")).isEqualTo(13L);
        assertThat(rows.get(0).get("sessionExists")).isEqualTo(false);
        assertThat(persistence.listAllSessions()).isEmpty();
    }

    @Test
    void samePolicyWithSession_oneRow() {
        CiPolicyDocument doc = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        PolicyStudioSession session = sessionWithRules(doc, 13);
        Map<UUID, PolicyStudioSession> mem = Map.of(VIKASAM_ID, session);

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT,
                List.of(doc),
                List.of(snapshot(VIKASAM_ID, 13)),
                List.of(),
                List.of(),
                mem,
                null,
                Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("sessionExists")).isEqualTo(true);
        assertThat(rows.get(0).get("enrichmentSource")).isEqualTo("SESSION");
    }

    @Test
    void samePolicyWithApplicability_oneRow() {
        CiPolicyDocument doc = document(CLEAN_A, "CLEAN UAT Policy A — STARTER LOAN", "DRAFT_READY");
        CiPolicyApplicability app = CiPolicyApplicability.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .policyDocumentId(CLEAN_A)
                .policyName("CLEAN UAT Policy A — STARTER LOAN")
                .policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.ACTIVE)
                .products(List.of("STARTER_LOAN"))
                .build();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT,
                List.of(doc),
                List.of(),
                List.of(app),
                List.of(),
                Map.of(),
                null,
                Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status")).isEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
        assertThat(rows.get(0).get("kind")).isEqualTo("catalogue");
    }

    @Test
    void activeAndApprovedPolicies_listed() {
        CiPolicyDocument a = document(CLEAN_A, "CLEAN UAT Policy A — STARTER LOAN", "DRAFT_READY");
        CiPolicyDocument b = document(CLEAN_B, "CLEAN UAT Policy B — BANK STARTER", "DRAFT_READY");
        CiPolicyApplicability appA = CiPolicyApplicability.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).policyDocumentId(CLEAN_A)
                .policyName(a.getName()).policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.ACTIVE).build();
        CiPolicyApplicability appB = CiPolicyApplicability.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).policyDocumentId(CLEAN_B)
                .policyName(b.getName()).policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.APPROVED).build();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT, List.of(a, b), List.of(), List.of(appA, appB), List.of(), Map.of(), null, Map.of());

        Map<String, String> byId = rows.stream().collect(Collectors.toMap(
                r -> String.valueOf(r.get("documentId")),
                r -> String.valueOf(r.get("status"))));
        assertThat(byId.get(CLEAN_A.toString())).isEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
        assertThat(byId.get(CLEAN_B.toString())).isEqualTo(PolicyBusinessLifecycleStatus.APPROVED);
    }

    @Test
    void retiredPolicy_listedWithRetiredStatus() {
        UUID id = UUID.randomUUID();
        CiPolicyDocument doc = document(id, "Retired Sample", "DRAFT_READY");
        CiPolicyApplicability app = CiPolicyApplicability.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).policyDocumentId(id)
                .policyName(doc.getName()).policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.RETIRED).build();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT, List.of(doc), List.of(), List.of(app), List.of(), Map.of(), null, Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status")).isEqualTo(PolicyBusinessLifecycleStatus.RETIRED);
    }

    @Test
    void ruleCountsFromDurableSnapshot_whenSessionAbsent() {
        CiPolicyDocument doc = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT, List.of(doc), List.of(snapshot(VIKASAM_ID, 13)), List.of(), List.of(),
                Map.of(), null, Map.of());
        assertThat(rows.get(0).get("underwritingRuleCount")).isEqualTo(13L);
        assertThat(rows.get(0).get("enrichmentSource")).isEqualTo("DURABLE_SNAPSHOT");
    }

    @Test
    void openByIdDoesNotChangeListMembership() {
        CiPolicyDocument doc = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        CiPolicyStudioSessionSnapshot snap = snapshot(VIKASAM_ID, 13);

        List<Map<String, Object>> before = service.listFromFixtures(
                TENANT, List.of(doc), List.of(snap), List.of(), List.of(), Map.of(), null, Map.of());

        // Simulate open-by-id rebound into memory.
        PolicyStudioSession rebound = PolicyStudioSessionSnapshotCodec.fromPayload(snap.getPayload());
        persistence.saveSessionSnapshot(rebound);

        List<Map<String, Object>> after = service.listFromFixtures(
                TENANT, List.of(doc), List.of(snap), List.of(), List.of(),
                Map.of(VIKASAM_ID, rebound), null, Map.of());

        assertThat(before).hasSize(1);
        assertThat(after).hasSize(1);
        assertThat(before.get(0).get("documentId")).isEqualTo(after.get(0).get("documentId"));
    }

    @Test
    void exactIdDedupe_notNameBased() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        CiPolicyDocument d1 = document(id1, "Same Name", "DRAFT_READY");
        CiPolicyDocument d2 = document(id2, "Same Name", "DRAFT_READY");

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT, List.of(d1, d2), List.of(), List.of(), List.of(), Map.of(), null, Map.of());

        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(r -> r.get("documentId")))
                .containsExactlyInAnyOrder(id1.toString(), id2.toString());
    }

    @Test
    void tenantIsolation_excludesOtherTenantDocuments() {
        CiPolicyDocument mine = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        CiPolicyDocument other = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(OTHER_TENANT)
                .name("Other Tenant Policy")
                .contentHash("other")
                .status("DRAFT_READY")
                .documentVersion(1)
                .uploadedAt(Instant.now())
                .metadata(new LinkedHashMap<>())
                .build();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT, List.of(mine, other), List.of(), List.of(), List.of(), Map.of(), null, Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("documentId")).isEqualTo(VIKASAM_ID.toString());
    }

    @Test
    void cleanUatAndVikasam_coldStartGolden() {
        CiPolicyDocument vikasam = document(VIKASAM_ID, "Vikasam Bureau", "DRAFT_READY");
        CiPolicyDocument a = document(CLEAN_A, "CLEAN UAT Policy A — STARTER LOAN", "DRAFT_READY");
        CiPolicyDocument b = document(CLEAN_B, "CLEAN UAT Policy B — BANK STARTER", "DRAFT_READY");
        CiPolicyApplicability appA = CiPolicyApplicability.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).policyDocumentId(CLEAN_A)
                .policyName(a.getName()).policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.ACTIVE).build();
        CiPolicyApplicability appB = CiPolicyApplicability.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).policyDocumentId(CLEAN_B)
                .policyName(b.getName()).policyVersionLabel("v1")
                .businessStatus(PolicyBusinessLifecycleStatus.APPROVED).build();

        List<Map<String, Object>> rows = service.listFromFixtures(
                TENANT,
                List.of(vikasam, a, b),
                List.of(snapshot(VIKASAM_ID, 13)),
                List.of(appA, appB),
                List.of(graph(VIKASAM_ID, 13)),
                Map.of(),
                null,
                Map.of());

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("documentId")))
                .containsExactlyInAnyOrder(
                        VIKASAM_ID.toString(), CLEAN_A.toString(), CLEAN_B.toString());
        Map<String, Object> vRow = rows.stream()
                .filter(r -> VIKASAM_ID.toString().equals(r.get("documentId")))
                .findFirst().orElseThrow();
        assertThat(vRow.get("underwritingRuleCount")).isEqualTo(13L);
        assertThat(vRow.get("sessionExists")).isEqualTo(false);
    }

    @Test
    void memoryOnlyDraft_stillListedWhenJpaAbsent() {
        CiPolicyDocument doc = document(UUID.randomUUID(), "Scratch Only", "DRAFT_READY");
        PolicyStudioSession session = sessionWithRules(doc, 2);
        persistence.saveSessionSnapshot(session);

        List<Map<String, Object>> rows = service.list(
                TENANT, persistence, null, Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("documentId")).isEqualTo(doc.getId().toString());
    }

    private static CiPolicyDocument document(UUID id, String name, String status) {
        return CiPolicyDocument.builder()
                .id(id)
                .tenantId(TENANT)
                .name(name)
                .contentHash(id.toString().replace("-", ""))
                .status(status)
                .documentVersion(1)
                .uploadedAt(Instant.now())
                .createdAt(Instant.now())
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private static CiPolicyStudioSessionSnapshot snapshot(UUID docId, int ruleCount) {
        PolicyStudioSession session = sessionWithRules(document(docId, "Vikasam Bureau", "DRAFT_READY"), ruleCount);
        return CiPolicyStudioSessionSnapshot.builder()
                .policyDocumentId(docId)
                .tenantId(TENANT)
                .payload(PolicyStudioSessionSnapshotCodec.toPayload(session))
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private static CiPolicyRuleGraph graph(UUID docId, int ruleCount) {
        return CiPolicyRuleGraph.builder()
                .id(UUID.randomUUID())
                .policyDocumentId(docId)
                .documentVersion(1)
                .graphHash("hash-" + docId)
                .ruleCount(ruleCount)
                .status("MATERIALIZED")
                .build();
    }

    private static PolicyStudioSession sessionWithRules(CiPolicyDocument doc, int ruleCount) {
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        List<CiPolicyRuleCandidate> rules = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            rules.add(CiPolicyRuleCandidate.builder()
                    .id(UUID.randomUUID())
                    .systemRuleId("RULE_" + i)
                    .ruleVersion("DRAFT")
                    .expression(Map.of("op", "EQ"))
                    .metadata(Map.of())
                    .build());
        }
        session.getRuleCandidates().addAll(rules);
        return session;
    }
}
