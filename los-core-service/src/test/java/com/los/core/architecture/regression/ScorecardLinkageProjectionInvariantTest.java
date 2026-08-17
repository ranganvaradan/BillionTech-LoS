package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import com.los.core.creditintelligence.policystudio.scorecard.PolicyVersionScorecardLinkage;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioDurableResolutionStore;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioPersistenceService;
import com.los.core.creditintelligence.staging.ProspectPolicyViewBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SCORECARD-LINKAGE-PROJECTION-INVARIANT-1 — document FK is the only linkage authority.
 */
class ScorecardLinkageProjectionInvariantTest {

    static final UUID VIKASAM_POLICY_ID = UUID.fromString("4543e643-c3a0-4a57-a92c-370dff8b2fa9");
    static final UUID VIKASAM_SCORECARD_ID = UUID.fromString("cc38f5a0-fab7-4001-8167-5a8f47d2487a");
    static final UUID DUPLICATE_REVERSE_LINK_ID = UUID.fromString("a504db06-f6ce-47f4-a858-2b9b6acbad40");

    @TempDir
    Path durableDir;

    @Test
    void s1_authoritativeFkProjectedOntoPolicyStudioHeader() {
        PolicyStudioSession session = session(VIKASAM_POLICY_ID, VIKASAM_SCORECARD_ID, 1);
        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, session, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) out.get("policyHeader");
        assertThat(header.get("scorecardId")).isEqualTo(VIKASAM_SCORECARD_ID.toString());
        assertThat(header.get("scorecardLinkageKnown")).isEqualTo(true);
        assertThat(header.get("scorecardLinked")).isEqualTo(true);
        assertThat(header.get("scorecardLinkageAuthority"))
                .isEqualTo(PolicyVersionScorecardLinkage.AUTHORITY);
        assertThat(header.get("scorecardLinkOwnerType"))
                .isEqualTo(PolicyVersionScorecardLinkage.OWNER_TYPE);
        assertThat(header.get("scorecardLinkOwnerId")).isEqualTo(VIKASAM_POLICY_ID.toString());
        assertThat(header.get("scorecardLinkOwnerVersion")).isEqualTo(1);
        assertThat(out.get("scorecardId")).isEqualTo(VIKASAM_SCORECARD_ID.toString());
        assertThat(PolicyVersionScorecardLinkage.linkageWithoutOwnerIdentityCount(
                VIKASAM_POLICY_ID, 1, VIKASAM_SCORECARD_ID)).isZero();
    }

    @Test
    void s2_staleSnapshotNullCannotSuppressDocumentFk() {
        Fixtures fx = fixtures();
        PolicyStudioSession session = session(VIKASAM_POLICY_ID, null, 1);
        fx.persistence.saveSessionSnapshot(session);
        CiPolicyDocument durable = fx.docs.get(VIKASAM_POLICY_ID);
        durable.setScorecardId(VIKASAM_SCORECARD_ID);
        fx.persistence.simulateProcessRestart();

        PolicyStudioSession rebound = fx.persistence.requireSession(VIKASAM_POLICY_ID);
        assertThat(rebound.getDocument().getScorecardId()).isEqualTo(VIKASAM_SCORECARD_ID);

        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, rebound, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) out.get("policyHeader");
        assertThat(header.get("scorecardId")).isEqualTo(VIKASAM_SCORECARD_ID.toString());
        assertThat(PolicyVersionScorecardLinkage.falseNoScorecardDisplayCount(
                true, VIKASAM_SCORECARD_ID, false)).isZero();
    }

    @Test
    void s3_staleSnapshotWrongIdCannotOverrideDocumentFk() {
        Fixtures fx = fixtures();
        PolicyStudioSession session = session(VIKASAM_POLICY_ID, DUPLICATE_REVERSE_LINK_ID, 1);
        fx.persistence.saveSessionSnapshot(session);
        fx.docs.get(VIKASAM_POLICY_ID).setScorecardId(VIKASAM_SCORECARD_ID);
        fx.persistence.simulateProcessRestart();

        PolicyStudioSession rebound = fx.persistence.requireSession(VIKASAM_POLICY_ID);
        assertThat(rebound.getDocument().getScorecardId()).isEqualTo(VIKASAM_SCORECARD_ID);
        assertThat(rebound.getDocument().getScorecardId()).isNotEqualTo(DUPLICATE_REVERSE_LINK_ID);

        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, rebound, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) out.get("policyHeader");
        assertThat(header.get("scorecardId")).isEqualTo(VIKASAM_SCORECARD_ID.toString());
    }

    @Test
    void s4_genuineUnlinkStillProjectsNoScorecard() {
        PolicyStudioSession session = session(UUID.randomUUID(), null, 1);
        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, session, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) out.get("policyHeader");
        assertThat(header.get("scorecardLinkageKnown")).isEqualTo(true);
        assertThat(header.get("scorecardId")).isNull();
        assertThat(header.get("scorecardLinked")).isEqualTo(false);
        assertThat(PolicyVersionScorecardLinkage.falseNoScorecardDisplayCount(
                true, null, true)).isZero();
    }

    @Test
    void s5_unknownLinkageCannotDisplayNoScorecard() {
        assertThat(PolicyVersionScorecardLinkage.falseNoScorecardDisplayCount(
                false, null, true)).isEqualTo(1);
        assertThat(PolicyVersionScorecardLinkage.falseNoScorecardDisplayCount(
                false, VIKASAM_SCORECARD_ID, false)).isZero();
    }

    @Test
    void s6_s7_vikasamCanonicalIdNotDuplicateReverseLink() {
        UUID resolved = PolicyVersionScorecardLinkage.canonicalScorecardId(
                VIKASAM_SCORECARD_ID, DUPLICATE_REVERSE_LINK_ID, VIKASAM_SCORECARD_ID);
        assertThat(resolved).isEqualTo(VIKASAM_SCORECARD_ID);
        assertThat(resolved).isNotEqualTo(DUPLICATE_REVERSE_LINK_ID);

        var duplicateFirst = new PolicyVersionScorecardLinkage.ScorecardIdentity(
                DUPLICATE_REVERSE_LINK_ID, "Vikasam Bureau — Scorecard", "DRAFT", "POLICY_WEIGHTED_V2");
        var canonical = new PolicyVersionScorecardLinkage.ScorecardIdentity(
                VIKASAM_SCORECARD_ID, "Vikasam Bureau — Scorecard", "DRAFT", "POLICY_WEIGHTED_V2");
        var identity = PolicyVersionScorecardLinkage.identityByCanonicalId(
                VIKASAM_SCORECARD_ID, canonical, duplicateFirst);
        assertThat(identity).isNotNull();
        assertThat(identity.id()).isEqualTo(VIKASAM_SCORECARD_ID);
        assertThat(identity.name()).isEqualTo("Vikasam Bureau — Scorecard");
        assertThat(identity.status()).isEqualTo("DRAFT");
        assertThat(identity.scoringMode()).isEqualTo("POLICY_WEIGHTED_V2");

        var stolen = PolicyVersionScorecardLinkage.identityByCanonicalId(
                VIKASAM_SCORECARD_ID, null, duplicateFirst);
        assertThat(stolen).isNull();
    }

    @Test
    void s8_crossVersionIsolation_mixCountZero() {
        UUID versionA = UUID.randomUUID();
        UUID versionB = UUID.randomUUID();
        UUID scorecardX = UUID.randomUUID();
        UUID scorecardY = UUID.randomUUID();
        assertThat(PolicyVersionScorecardLinkage.crossVersionMixCount(
                versionA, scorecardX, versionB, scorecardY, scorecardY)).isZero();
        assertThat(PolicyVersionScorecardLinkage.crossVersionMixCount(
                versionA, scorecardX, versionB, null, null)).isZero();
        assertThat(PolicyVersionScorecardLinkage.crossVersionMixCount(
                versionA, scorecardX, versionB, null, scorecardX)).isEqualTo(1);

        PolicyStudioSession a = session(versionA, scorecardX, 1);
        PolicyStudioSession b = session(versionB, null, 2);
        Map<String, Object> outA = new LinkedHashMap<>();
        Map<String, Object> outB = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(outA, a, Map.of());
        ProspectPolicyViewBuilder.enrich(outB, b, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> headerB = (Map<String, Object>) outB.get("policyHeader");
        UUID projectedB = headerB.get("scorecardId") == null
                ? null : UUID.fromString(String.valueOf(headerB.get("scorecardId")));
        assertThat(PolicyVersionScorecardLinkage.crossVersionMixCount(
                versionA, scorecardX, versionB, null, projectedB)).isZero();
        assertThat(headerB.get("scorecardLinkOwnerId")).isEqualTo(versionB.toString());
        assertThat(headerB.get("scorecardId")).isNull();
    }

    @Test
    void staticGuards_projectionAndFrontendNeverDefaultUnknownToNoScorecard() throws Exception {
        Path uiRoot = Path.of("../ui-service/src").toAbsolutePath().normalize();
        if (!Files.isDirectory(uiRoot)) {
            uiRoot = Path.of("ui-service/src").toAbsolutePath().normalize();
        }
        Path javaRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        if (!Files.isDirectory(javaRoot)) {
            javaRoot = Path.of("los-core-service/src/main/java").toAbsolutePath().normalize();
        }
        String view = Files.readString(javaRoot.resolve(
                "com/los/core/creditintelligence/staging/ProspectPolicyViewBuilder.java"));
        assertThat(view).contains("PolicyVersionScorecardLinkage.applyProjection");
        String persist = Files.readString(javaRoot.resolve(
                "com/los/core/creditintelligence/policystudio/service/PolicyStudioPersistenceService.java"));
        assertThat(persist).contains("overlayAuthoritativeDocumentFields");
        assertThat(persist).contains("overlayFromDurableDocument");
        String demo = Files.readString(javaRoot.resolve(
                "com/los/core/creditintelligence/staging/StagingPolicyStudioDemoService.java"));
        assertThat(demo).contains("overlayAuthoritativeScorecardLinkage");
        assertThat(demo).contains("scorecardRepository.findById(canonical)");
        assertThat(demo).doesNotContain("findByPolicyDocumentId");
        String tab = Files.readString(uiRoot.resolve("pages/creditIntelligence/CiPolicyScorecardTab.tsx"));
        assertThat(tab).contains("resolveScorecardLinkageDisplay");
        assertThat(tab).contains("scorecard-linkage-loading");
        assertThat(tab).contains("scorecard-linkage-linked");
        assertThat(tab).contains("scorecard-linkage-none");
        assertThat(tab).contains("linkage.kind === 'NONE' && mode === 'NONE'");
        assertThat(tab).contains("linkage.kind === 'LINKED' ? 'Unlink' : 'No scorecard'");
        String display = Files.readString(uiRoot.resolve("lib/policyStudio/scorecardLinkageDisplay.ts"));
        assertThat(display).contains("kind: 'LOADING'");
        assertThat(display).contains("kind: 'NONE'");
        assertThat(display).contains("kind: 'LINKED'");
    }

    private Fixtures fixtures() {
        ConcurrentHashMap<UUID, CiPolicyDocument> docs = new ConcurrentHashMap<>();
        ConcurrentHashMap<UUID, CiPolicyStudioSessionSnapshot> snaps = new ConcurrentHashMap<>();
        CiPolicyDocumentRepository docRepo = mock(CiPolicyDocumentRepository.class);
        when(docRepo.save(any())).thenAnswer(inv -> {
            CiPolicyDocument d = inv.getArgument(0);
            docs.put(d.getId(), d);
            return d;
        });
        when(docRepo.saveAndFlush(any())).thenAnswer(inv -> {
            CiPolicyDocument d = inv.getArgument(0);
            docs.put(d.getId(), d);
            return d;
        });
        when(docRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(docs.get(inv.getArgument(0))));
        CiPolicyStudioSessionSnapshotRepository snapRepo = mock(CiPolicyStudioSessionSnapshotRepository.class);
        when(snapRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(snaps.get(inv.getArgument(0))));
        when(snapRepo.save(any())).thenAnswer(inv -> {
            CiPolicyStudioSessionSnapshot s = inv.getArgument(0);
            snaps.put(s.getPolicyDocumentId(), s);
            return s;
        });
        when(snapRepo.saveAndFlush(any())).thenAnswer(inv -> {
            CiPolicyStudioSessionSnapshot s = inv.getArgument(0);
            snaps.put(s.getPolicyDocumentId(), s);
            return s;
        });
        PolicyStudioPersistenceService persistence = new PolicyStudioPersistenceService(
                new PolicyStudioDurableResolutionStore(durableDir.toString()), docRepo, snapRepo);
        return new Fixtures(docs, snaps, persistence);
    }

    private static PolicyStudioSession session(UUID docId, UUID scorecardId, int version) {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(docId)
                .tenantId(tenant)
                .name("Vikasam Bureau")
                .documentType("TXT")
                .contentHash("hash-" + docId)
                .sourceText("policy")
                .documentVersion(version)
                .scorecardId(scorecardId)
                .metadata(new LinkedHashMap<>())
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        session.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId("CM_TEST")
                .expression(Map.of("op", "EQ", "left", Map.of("const", 1), "right", Map.of("const", 1)))
                .metadata(Map.of())
                .build());
        return session;
    }

    private record Fixtures(
            ConcurrentHashMap<UUID, CiPolicyDocument> docs,
            ConcurrentHashMap<UUID, CiPolicyStudioSessionSnapshot> snaps,
            PolicyStudioPersistenceService persistence) {}
}
