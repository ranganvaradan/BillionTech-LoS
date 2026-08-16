package com.los.core.creditintelligence.policystudio.parameters.derived;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * POLICY-DERIVED-PARAMETER-RESEARCH-AND-AUTHORING-1 — research → approve → V139 definition.
 * Mockito fakes avoid full-schema H2 JSONB DDL from {@code @DataJpaTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DerivedCalculationResearchServiceTest {

    private static final String TARGET = "bureau.credit_after_overdue.clean_history_months";

    @Mock
    CiGacatDerivedCalculationProposalRepository proposalRepository;

    @Mock
    CiGacatDerivedCalculationDefinitionRepository definitionRepository;

    private final List<CiGacatDerivedCalculationProposal> proposals = new CopyOnWriteArrayList<>();
    private final List<CiGacatDerivedCalculationDefinition> definitions = new CopyOnWriteArrayList<>();

    private DerivedCalculationResearchService research;
    private DerivedCalculationDefinitionService definitionService;

    @BeforeEach
    void setUp() {
        proposals.clear();
        definitions.clear();

        when(proposalRepository.save(any())).thenAnswer(inv -> {
            CiGacatDerivedCalculationProposal p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            proposals.removeIf(x -> p.getId().equals(x.getId()));
            proposals.add(p);
            return p;
        });
        when(proposalRepository.findById(any())).thenAnswer(inv ->
                proposals.stream().filter(p -> inv.getArgument(0).equals(p.getId())).findFirst());
        when(proposalRepository.findByTargetParameterIdOrderByCreatedAtDesc(any())).thenAnswer(inv ->
                proposals.stream()
                        .filter(p -> inv.getArgument(0).equals(p.getTargetParameterId()))
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .toList());

        when(definitionRepository.save(any())).thenAnswer(inv -> {
            CiGacatDerivedCalculationDefinition d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            definitions.removeIf(x -> d.getId().equals(x.getId()));
            definitions.add(d);
            return d;
        });
        when(definitionRepository.findById(any())).thenAnswer(inv ->
                definitions.stream().filter(d -> inv.getArgument(0).equals(d.getId())).findFirst());
        when(definitionRepository.findByCanonicalParameterIdOrderByVersionNoDesc(any())).thenAnswer(inv ->
                definitions.stream()
                        .filter(d -> inv.getArgument(0).equals(d.getCanonicalParameterId()))
                        .sorted((a, b) -> Integer.compare(
                                b.getVersionNo() == null ? 0 : b.getVersionNo(),
                                a.getVersionNo() == null ? 0 : a.getVersionNo()))
                        .toList());
        when(definitionRepository.findFirstByCanonicalParameterIdAndTenantIdIsNullAndStatusNotOrderByVersionNoDesc(
                any(), any())).thenAnswer(inv ->
                definitions.stream()
                        .filter(d -> inv.getArgument(0).equals(d.getCanonicalParameterId()))
                        .filter(d -> d.getTenantId() == null)
                        .filter(d -> !inv.getArgument(1).equals(d.getStatus()))
                        .sorted((a, b) -> Integer.compare(
                                b.getVersionNo() == null ? 0 : b.getVersionNo(),
                                a.getVersionNo() == null ? 0 : a.getVersionNo()))
                        .findFirst());
        when(definitionRepository.findFirstByCanonicalParameterIdAndTenantIdAndStatusNotOrderByVersionNoDesc(
                any(), any(), any())).thenAnswer(inv ->
                definitions.stream()
                        .filter(d -> inv.getArgument(0).equals(d.getCanonicalParameterId()))
                        .filter(d -> inv.getArgument(1).equals(d.getTenantId()))
                        .filter(d -> !inv.getArgument(2).equals(d.getStatus()))
                        .sorted((a, b) -> Integer.compare(
                                b.getVersionNo() == null ? 0 : b.getVersionNo(),
                                a.getVersionNo() == null ? 0 : a.getVersionNo()))
                        .findFirst());

        definitionService = new DerivedCalculationDefinitionService(definitionRepository);
        research = new DerivedCalculationResearchService(proposalRepository, definitionService);
    }

    @Test
    void suggestDoesNotCreateExecutableDefinition() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        assertNotNull(res.get("id"));
        assertTrue(definitions.isEmpty());
        assertEquals(true, res.get("advisoryOnly"));
    }

    @Test
    void suggestUsesExactCanonicalDependenciesFromCatalogue() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) res.get("candidateDependencies");
        assertNotNull(deps);
        assertFalse(deps.isEmpty(), "expected catalogue-derived candidate dependencies");
        for (Map<String, Object> d : deps) {
            assertNotNull(d.get("parameterId"));
            assertTrue(String.valueOf(d.get("parameterId")).contains("."));
            assertNotNull(d.get("parameterKind"));
        }
    }

    @Test
    void acceptCreatesDefinitionForExistingCanonicalNotDuplicate() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        if (res.get("proposedExpression") == null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deps = (List<Map<String, Object>>) res.get("candidateDependencies");
            String depId = String.valueOf(deps.get(0).get("parameterId"));
            research.updateProposalExpression(proposalId, Map.of("op", "REF", "id", depId), "tester");
        }
        Map<String, Object> accepted = research.accept(proposalId, "approver");
        assertEquals(true, accepted.get("targetCanonicalParameterReused"));
        assertEquals(false, accepted.get("duplicateParameterCreated"));
        @SuppressWarnings("unchecked")
        Map<String, Object> def = (Map<String, Object>) accepted.get("definition");
        assertEquals(TARGET, def.get("canonicalParameterId"));
        assertNotEquals(TARGET + "_v2", def.get("canonicalParameterId"));
        assertFalse(String.valueOf(def.get("canonicalParameterId")).startsWith("lender."));
        assertFalse(definitions.isEmpty());
    }

    @Test
    void rejectCreatesNoDefinition() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        research.reject(proposalId, "tester");
        assertTrue(definitions.isEmpty());
    }

    @Test
    void unsafeExpressionRejectedOnEdit() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        assertThrows(Exception.class, () ->
                research.updateProposalExpression(proposalId,
                        Map.of("op", "EVAL", "code", "System.exit(0)"), "tester"));
    }

    @Test
    void missingInputDoesNotDefaultToZeroOnW6Path() {
        Map<String, Object> draft = definitionService.saveDraft(Map.of(
                "canonicalParameterId", TARGET,
                "scope", "PLATFORM",
                "expression", Map.of("op", "REF", "id", "bureau.max_dpd_6m"),
                "description", "test"
        ), null, "tester");
        UUID id = UUID.fromString(String.valueOf(draft.get("id")));
        Map<String, Object> tested = definitionService.testWithSample(id, Map.of("bureau.max_dpd_6m", 3));
        assertEquals("TESTED", tested.get("status"));
        var eval = definitionService.evaluateCanonical(TARGET, null, Map.of());
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT, eval.status());
        assertNull(eval.value());
    }

    @Test
    void cycleDetectionRejectsSelfReferenceOnAccept() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        research.updateProposalExpression(proposalId, Map.of("op", "REF", "id", TARGET), "tester");
        assertThrows(Exception.class, () -> research.accept(proposalId, "approver"));
        assertTrue(definitions.isEmpty());
    }

    @Test
    void typeMismatchUnsafeRefRejectedWhenUnknownId() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        assertThrows(Exception.class, () ->
                research.updateProposalExpression(proposalId,
                        Map.of("op", "REF", "id", "invented.nonexistent.field"), "tester"));
    }

    @Test
    void duplicateActiveDefinitionVersionsRatherThanSilentOverwrite() {
        definitionService.saveDraft(Map.of(
                "canonicalParameterId", TARGET,
                "scope", "PLATFORM",
                "expression", Map.of("op", "REF", "id", "bureau.max_dpd_6m"),
                "description", "v1"
        ), null, "a");
        Map<String, Object> v2 = definitionService.saveDraft(Map.of(
                "canonicalParameterId", TARGET,
                "scope", "PLATFORM",
                "expression", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
                "description", "v2"
        ), null, "b");
        assertEquals(2, v2.get("versionNo"));
        assertEquals(2, definitions.size());
    }

    @Test
    void productionReadyRemainsIndependentOfAuthoringApproval() {
        Map<String, Object> draft = definitionService.saveDraft(Map.of(
                "canonicalParameterId", TARGET,
                "scope", "PLATFORM",
                "expression", Map.of("op", "REF", "id", "bureau.max_dpd_6m"),
                "description", "test"
        ), null, "tester");
        assertEquals("DEFINED", draft.get("status"));
        assertNotEquals("PRODUCTION_READY", draft.get("status"));
    }
}
