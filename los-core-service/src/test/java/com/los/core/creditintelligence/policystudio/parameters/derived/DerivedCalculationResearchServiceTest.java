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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    void cleanHistorySuggestIsNeedsInputNotProxyRef() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        assertEquals("NEEDS_INPUT", res.get("proposalStatus"));
        assertNull(res.get("proposedExpression"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) res.get("candidateDependencies");
        assertFalse(deps.isEmpty());
        assertTrue(deps.stream().anyMatch(d ->
                "bureau.tradeline.payment_history".equals(d.get("parameterId"))));
        @SuppressWarnings("unchecked")
        List<String> limitations = (List<String>) res.get("limitations");
        assertNotNull(limitations);
        assertTrue(limitations.stream().anyMatch(l ->
                l.toLowerCase().contains("max_dpd") || l.toLowerCase().contains("rejected")
                        || l.toLowerCase().contains("overlap")));
    }

    @Test
    void maxDpdProxyCannotBeEditedOrAcceptedForCleanHistory() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        assertThrows(Exception.class, () ->
                research.updateProposalExpression(proposalId,
                        Map.of("op", "REF", "id", "bureau.max_dpd_12m"), "tester"));
        // Force expression into proposal (bypass edit) then Accept must still reject
        proposals.stream().filter(p -> proposalId.equals(p.getId())).findFirst().ifPresent(p -> {
            p.setProposedExpression(Map.of("op", "REF", "id", "bureau.max_dpd_12m"));
            p.setProposalStatus("READY_FOR_REVIEW");
        });
        assertThrows(Exception.class, () -> research.accept(proposalId, "approver"));
        assertTrue(definitions.isEmpty());
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
        // Insert a TESTED definition directly (bypassing authoring gates) to exercise W6 eval
        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(TARGET)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(Map.of("op", "REF", "id", "bureau.max_dpd_6m"))
                .dependencyIds(List.of("bureau.max_dpd_6m"))
                .versionNo(1)
                .build();
        definitions.add(row);
        var eval = definitionService.evaluateCanonical(TARGET, null, Map.of());
        assertEquals(SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT, eval.status());
        assertNull(eval.value());
    }

    @Test
    void cycleDetectionRejectsSelfReferenceOnAccept() {
        Map<String, Object> res = research.suggest(TARGET, null, "tester");
        UUID proposalId = UUID.fromString(String.valueOf(res.get("id")));
        // Self-REF fails semantic (vocabulary/config) or cycle — either way no definition
        assertThrows(Exception.class, () ->
                research.updateProposalExpression(proposalId, Map.of("op", "REF", "id", TARGET), "tester"));
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
    void productionReadyRemainsIndependentOfAuthoringApproval() {
        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(TARGET)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_DEFINED)
                .expressionJson(Map.of("op", "CONST", "value", 1))
                .dependencyIds(List.of())
                .versionNo(1)
                .build();
        definitions.add(row);
        assertEquals("DEFINED", definitionService.toView(row).get("status"));
    }
}
