package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyStudioSessionSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * POLICY-STUDIO-SESSION-PERSISTENCE-P0 — full session (OR AST) survives process restart
 * via JPA snapshot, not resolution-overlay-only.
 */
class PolicyStudioSessionPersistenceP0Test {

    @TempDir
    Path durableDir;

    @Test
    void codec_roundTripsCompoundOrExpression() {
        PolicyStudioSession session = sampleOrSession();
        Map<String, Object> payload = PolicyStudioSessionSnapshotCodec.toPayload(session);
        PolicyStudioSession back = PolicyStudioSessionSnapshotCodec.fromPayload(payload);
        assertThat(back).isNotNull();
        assertThat(back.getRuleCandidates()).hasSize(1);
        Map<String, Object> expr = back.getRuleCandidates().get(0).getExpression();
        assertThat(expr.get("op")).isEqualTo("OR");
        assertThat(String.valueOf(expr)).contains("bureau.score");
        assertThat(String.valueOf(expr)).contains("-1");
    }

    @Test
    void fullSessionSurvivesSimulatedRestartViaJpaSnapshot() {
        ConcurrentHashMap<UUID, CiPolicyDocument> docs = new ConcurrentHashMap<>();
        ConcurrentHashMap<UUID, CiPolicyStudioSessionSnapshot> snaps = new ConcurrentHashMap<>();

        CiPolicyDocumentRepository docRepo = mock(CiPolicyDocumentRepository.class);
        when(docRepo.save(any())).thenAnswer(inv -> {
            CiPolicyDocument d = inv.getArgument(0);
            docs.put(d.getId(), d);
            return d;
        });

        CiPolicyStudioSessionSnapshotRepository snapRepo = mock(CiPolicyStudioSessionSnapshotRepository.class);
        when(snapRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(snaps.get(inv.getArgument(0))));
        when(snapRepo.save(any())).thenAnswer(inv -> {
            CiPolicyStudioSessionSnapshot s = inv.getArgument(0);
            snaps.put(s.getPolicyDocumentId(), s);
            return s;
        });

        PolicyStudioDurableResolutionStore durable =
                new PolicyStudioDurableResolutionStore(durableDir.toString());
        PolicyStudioPersistenceService persistence =
                new PolicyStudioPersistenceService(durable, docRepo, snapRepo);

        PolicyStudioSession session = sampleOrSession();
        UUID docId = session.getDocument().getId();
        persistence.saveSessionSnapshot(session);
        assertThat(snaps).containsKey(docId);
        assertThat(docs).containsKey(docId);

        persistence.simulateProcessRestart();
        assertThat(persistence.loadSession(docId)).isNotNull();

        PolicyStudioSession rebound = persistence.requireSession(docId);
        assertThat(rebound.getRuleCandidates()).hasSize(1);
        Map<String, Object> expr = rebound.getRuleCandidates().get(0).getExpression();
        assertThat(expr.get("op")).isEqualTo("OR");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> args =
                (java.util.List<Map<String, Object>>) expr.get("args");
        assertThat(args).hasSize(2);
        assertThat(args.get(0).get("op")).isEqualTo("GT");
        assertThat(args.get(1).get("op")).isEqualTo("EQ");
    }

    private static PolicyStudioSession sampleOrSession() {
        UUID docId = UUID.randomUUID();
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(docId)
                .tenantId(tenant)
                .name("persistence-or")
                .documentType("TXT")
                .contentHash("abc")
                .sourceText("upload")
                .metadata(new LinkedHashMap<>(Map.of("kind", "upload", "demo", false)))
                .build();
        Map<String, Object> gt = new LinkedHashMap<>();
        gt.put("op", "GT");
        gt.put("left", Map.of("metric", "bureau.score"));
        gt.put("right", Map.of("const", 700));
        Map<String, Object> eq = new LinkedHashMap<>();
        eq.put("op", "EQ");
        eq.put("left", Map.of("metric", "bureau.score"));
        eq.put("right", Map.of("const", -1));
        Map<String, Object> or = new LinkedHashMap<>();
        or.put("op", "OR");
        or.put("args", java.util.List.of(gt, eq));

        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_OR_" + docId)
                .expression(or)
                .metadata(new LinkedHashMap<>(Map.of(
                        "cmAuthored", true,
                        "businessTitle", "Compound eligibility rule")))
                .lineage(new LinkedHashMap<>(Map.of(
                        "sourceText", "bureau score > 700 OR bureau score IS EQUAL TO -1")))
                .build();

        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        session.getRuleCandidates().add(rule);
        return session;
    }
}
