package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueAuthority;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorecardGovernance1Test {

    @Mock UnderwritingScorecardRepository repository;
    @Mock AdminConfigAuditSupport audit;
    @Mock ScorecardConvergenceService convergence;

    UnderwritingScorecardAdminService admin;
    ScorecardGovernanceService gov;

    ScorecardGovernanceService.Actor maker =
            new ScorecardGovernanceService.Actor("maker-1", "credit.manager", "CREDIT_MANAGER");
    ScorecardGovernanceService.Actor checker =
            new ScorecardGovernanceService.Actor("checker-1", "policy.checker", "POLICY_CHECKER");

    @BeforeEach
    void setup() {
        GacatCatalogueAuthority.configure(false, true);
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                "JAVA_SEED_TEST_ONLY");
        admin = new UnderwritingScorecardAdminService(repository, audit);
        gov = new ScorecardGovernanceService(repository, convergence, audit, admin);
    }

    @Test
    void versionDiff_bureauPointsChange() {
        UnderwritingScorecard v1 = draftCard(1);
        UnderwritingScorecard v2 = draftCard(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) v2.getScorecardJson().get("rows"));
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> c = new LinkedHashMap<>(r);
            if ("BUREAU_SCORE".equals(c.get("parameter")) && "GTE:750".equals(c.get("condition"))) {
                c.put("score", 40);
            }
            next.add(c);
        }
        v2.setScorecardJson(Map.of("rows", next));
        Map<String, Object> diff = ScorecardVersionDiff.diff(v1, v2);
        assertTrue(((Number) diff.get("changeCount")).intValue() >= 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) diff.get("changes");
        assertTrue(changes.stream().anyMatch(ch ->
                "BAND_POINTS".equals(ch.get("type"))
                        && String.valueOf(ch.get("subject")).contains("GTE:750")
                        && "35".equals(String.valueOf(ch.get("from")))
                        && "40".equals(String.valueOf(ch.get("to")))));
    }

    @Test
    void makerCheckerActivate_supersedesPriorActive() {
        UUID lineage = UUID.randomUUID();
        UnderwritingScorecard v1 = activeCard(lineage, 1, 35);
        UnderwritingScorecard v2 = readyDraft(lineage, v1.getId(), 2, 40);
        AtomicReference<UnderwritingScorecard> storeV2 = new AtomicReference<>(v2);
        Map<UUID, UnderwritingScorecard> store = new LinkedHashMap<>();
        store.put(v1.getId(), v1);
        store.put(v2.getId(), v2);

        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(store.values()));
        when(repository.save(any())).thenAnswer(inv -> {
            UnderwritingScorecard s = inv.getArgument(0);
            store.put(s.getId(), s);
            if (s.getId().equals(v2.getId())) storeV2.set(s);
            return s;
        });
        when(convergence.preview(any())).thenReturn(Map.of(
                "earnedPoints", 100, "maxPoints", 100, "normalizedPercent", 100,
                "policyDecision", "APPROVE", "applicationMutated", false));

        gov.recordPreview(v2.getId(), Map.of("inputs", Map.of("BUREAU_SCORE", 760)), maker);
        gov.submitForReview(v2.getId(), maker, "ready");
        assertEquals("IN_REVIEW", storeV2.get().getStatus());

        // CREDIT_MANAGER is maker-only — cannot act as checker
        assertThrows(ForbiddenException.class, () -> gov.approve(v2.getId(), maker, "nope"));
        // Same actor identity with checker role cannot approve own submission
        ScorecardGovernanceService.Actor makerAsChecker =
                new ScorecardGovernanceService.Actor("maker-1", "credit.manager", "POLICY_CHECKER");
        BusinessRuleException selfEx = assertThrows(BusinessRuleException.class,
                () -> gov.approve(v2.getId(), makerAsChecker, "self"));
        assertEquals("SCORECARD_SELF_APPROVAL_FORBIDDEN", selfEx.getReason());

        gov.approve(v2.getId(), checker, "looks good");
        assertEquals("APPROVED", storeV2.get().getStatus());

        gov.activate(v2.getId(), maker);
        assertTrue(storeV2.get().isActive());
        assertEquals("ACTIVE", storeV2.get().getStatus());
        assertFalse(store.get(v1.getId()).isActive());
        assertEquals("RETIRED", store.get(v1.getId()).getStatus());
    }

    @Test
    void returnForChanges_retainsRemarks() {
        UUID lineage = UUID.randomUUID();
        UnderwritingScorecard v3 = readyDraft(lineage, null, 3, 35);
        Map<UUID, UnderwritingScorecard> store = new LinkedHashMap<>();
        store.put(v3.getId(), v3);
        when(repository.findById(v3.getId())).thenAnswer(inv -> Optional.of(store.get(v3.getId())));
        when(repository.save(any())).thenAnswer(inv -> {
            UnderwritingScorecard s = inv.getArgument(0);
            store.put(s.getId(), s);
            return s;
        });
        when(convergence.preview(any())).thenReturn(Map.of(
                "earnedPoints", 90, "maxPoints", 100, "normalizedPercent", 90,
                "policyDecision", "APPROVE"));

        gov.recordPreview(v3.getId(), Map.of("inputs", Map.of()), maker);
        gov.submitForReview(v3.getId(), maker, null);
        gov.returnForChanges(v3.getId(), checker, "Review FOIR band");
        assertEquals("DRAFT", store.get(v3.getId()).getStatus());
        assertEquals("Review FOIR band", store.get(v3.getId()).getGovernanceJson().get("remarks"));
        assertEquals("RETURNED", store.get(v3.getId()).getGovernanceJson().get("checkerDecision"));
    }

    @Test
    void safetyFailure_blocksSubmit() {
        UUID id = UUID.randomUUID();
        UnderwritingScorecard draft = draftCard(1);
        draft.setId(id);
        draft.setLineageId(id);
        // no missing-data confirmation
        draft.setSafetyJson(Map.of("factorPolicies", Map.of()));
        when(repository.findById(id)).thenReturn(Optional.of(draft));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> gov.submitForReview(id, maker, null));
        assertEquals("SCORECARD_EXECUTION_NOT_READY", ex.getReason());
    }

    @Test
    void unauthorized_cannotSubmit() {
        assertThrows(ForbiddenException.class, () ->
                gov.submitForReview(UUID.randomUUID(),
                        new ScorecardGovernanceService.Actor("x", "x", "BORROWER"), null));
    }

    @Test
    void cloneOmitsApprovalEvidence() {
        UUID id = UUID.randomUUID();
        UnderwritingScorecard active = activeCard(id, 1, 35);
        Map<String, Object> govJson = new LinkedHashMap<>();
        govJson.put("approvedBy", "checker");
        govJson.put("submittedBy", "maker");
        govJson.put("lastPreview", Map.of("earnedPoints", 100));
        active.setGovernanceJson(govJson);
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.findAll()).thenReturn(List.of(active));
        when(repository.save(any())).thenAnswer(inv -> {
            UnderwritingScorecard s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        var draft = admin.createNewVersion(id);
        assertEquals("DRAFT", draft.getStatus());
        assertFalse(draft.getGovernanceJson().containsKey("approvedBy"));
        assertFalse(draft.getGovernanceJson().containsKey("submittedBy"));
        assertFalse(draft.getGovernanceJson().containsKey("lastPreview"));
        assertEquals(true, draft.getGovernanceJson().get("requireMakerChecker"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UnderwritingScorecard draftCard(int version) {
        UUID id = UUID.randomUUID();
        return UnderwritingScorecard.builder()
                .id(id)
                .name("Default scorecard — COMPANY — TERM_LOAN")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(version)
                .priority(100)
                .active(false)
                .status("DRAFT")
                .lineageId(id)
                .scorecardJson(Map.of("rows", companyRows(35)))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of()))
                .safetyJson(explicitSafety())
                .governanceJson(ScorecardGovernanceService.emptyGovernanceForClone())
                .build();
    }

    private UnderwritingScorecard activeCard(UUID lineage, int version, int bureauTop) {
        UUID id = UUID.randomUUID();
        return UnderwritingScorecard.builder()
                .id(id)
                .name("Default scorecard — COMPANY — TERM_LOAN")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(version)
                .priority(100)
                .active(true)
                .status("ACTIVE")
                .lineageId(lineage)
                .scorecardJson(Map.of("rows", companyRows(bureauTop)))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of()))
                .safetyJson(explicitSafety())
                .governanceJson(Map.of())
                .build();
    }

    private UnderwritingScorecard readyDraft(UUID lineage, UUID parentId, int version, int bureauTop) {
        UUID id = UUID.randomUUID();
        Map<String, Object> stamped = ScorecardCanonicalFactorMapper.stampScorecardJson(
                Map.of("rows", companyRows(bureauTop)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) stamped.get("rows");
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> c = new LinkedHashMap<>(r);
            if ("MONTHLY_INCOME".equals(c.get("parameter"))) {
                c.put("legacyCustomJustified", true);
                c.put("mappingStatus", "LEGACY_CUSTOM");
            }
            next.add(c);
        }
        stamped = new LinkedHashMap<>(stamped);
        stamped.put("rows", next);
        Map<String, Object> safety = explicitSafety();
        safety.put("missingDataPoliciesConfirmed", true);
        return UnderwritingScorecard.builder()
                .id(id)
                .name("Default scorecard — COMPANY — TERM_LOAN")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(version)
                .priority(100)
                .active(false)
                .status("DRAFT")
                .lineageId(lineage)
                .parentScorecardId(parentId)
                .scorecardJson(stamped)
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of()))
                .safetyJson(safety)
                .governanceJson(ScorecardGovernanceService.emptyGovernanceForClone())
                .build();
    }

    private static List<Map<String, Object>> companyRows(int bureauTop) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("d1", "BUREAU_SCORE", "BUREAU", "GTE:750", bureauTop));
        rows.add(row("d2", "BUREAU_SCORE", "BUREAU", "GTE:650", 25));
        rows.add(row("d3", "MONTHLY_INCOME", "SCORECARD", "GTE:50000", 25));
        rows.add(row("d4", "MONTHLY_INCOME", "SCORECARD", "GTE:25000", 15));
        rows.add(row("d5", "OBLIGATION_RATIO", "SCORECARD", "LTE:40", 20));
        rows.add(row("d6", "OBLIGATION_RATIO", "SCORECARD", "LTE:60", 10));
        rows.add(row("d7", "AVERAGE_BANK_BALANCE", "BANK_STATEMENT", "GTE:20000", 10));
        rows.add(row("d8", "KYC_QUALITY", "SYSTEM", "EQ:PASS", 10));
        return rows;
    }

    private static Map<String, Object> row(String id, String p, String s, String c, int score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("parameter", p);
        m.put("source", s);
        m.put("condition", c);
        m.put("weight", 1);
        m.put("score", score);
        return m;
    }

    private static Map<String, Object> explicitSafety() {
        Map<String, Object> policies = new LinkedHashMap<>();
        for (String p : List.of("BUREAU_SCORE", "MONTHLY_INCOME", "OBLIGATION_RATIO",
                "AVERAGE_BANK_BALANCE", "KYC_QUALITY")) {
            policies.put(p, Map.of("missingData", "REQUIRED"));
        }
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("factorPolicies", policies);
        safety.put("missingDataPoliciesExplicit", true);
        safety.put("missingDataPoliciesConfirmed", true);
        return safety;
    }
}
