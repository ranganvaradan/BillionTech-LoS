package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyLifecycleEventRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyShadowRoutingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyCatalogueServiceTest {

    @Mock
    private CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock
    private CiPolicyLifecycleEventRepository eventRepository;
    @Mock
    private CiPolicyShadowRoutingRepository routingRepository;
    @Mock
    private PolicyShadowEligibilityService eligibilityService;

    private PolicyCatalogueService catalogue;
    private final ConcurrentHashMap<UUID, CiPolicyApplicability> store = new ConcurrentHashMap<>();
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        props.setDefaultTenantId(tenantId);
        catalogue = new PolicyCatalogueService(
                applicabilityRepository, eventRepository, routingRepository,
                new PolicyApplicabilityResolver(), props, eligibilityService);

        lenient().doAnswer(inv -> {
            CiPolicyApplicability a = inv.getArgument(0);
            boolean linked = a.getPolicyVersionId() != null || a.getExecutablePackageId() != null;
            a.setLinkageClass(linked ? "PROPER_IMMUTABLE_PACKAGE_LINK" : "DEMO_ONLY_UNLINKED");
            a.setShadowEligibility(linked ? "ELIGIBLE_FOR_SHADOW_ROUTING" : "DEMO_ONLY_NOT_ROUTABLE");
            a.setShadowRoutable(linked);
            if (linked && a.getContentHash() == null) {
                a.setContentHash("hash-" + a.getId());
            }
            return null;
        }).when(eligibilityService).apply(any());

        when(applicabilityRepository.save(any())).thenAnswer(inv -> {
            CiPolicyApplicability a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            store.put(a.getId(), a);
            return a;
        });
        when(applicabilityRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0))));
        lenient().when(applicabilityRepository.findByTenantIdAndPolicyDocumentIdAndPolicyVersionLabel(any(), any(), any()))
                .thenAnswer(inv -> store.values().stream()
                        .filter(a -> tenantId.equals(a.getTenantId())
                                && inv.getArgument(1).equals(a.getPolicyDocumentId())
                                && inv.getArgument(2).equals(a.getPolicyVersionLabel()))
                        .findFirst());
        lenient().when(applicabilityRepository.findResolvableByTenant(any())).thenAnswer(inv ->
                store.values().stream()
                        .filter(a -> List.of("ACTIVE", "SCHEDULED", "APPROVED").contains(a.getBusinessStatus()))
                        .toList());
        when(applicabilityRepository.findOverlappingForUpdate(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    UUID exclude = inv.getArgument(1);
                    LocalDate from = inv.getArgument(2);
                    LocalDate until = inv.getArgument(3);
                    if (from == null) {
                        return List.of();
                    }
                    List<CiPolicyApplicability> out = new ArrayList<>();
                    for (CiPolicyApplicability a : store.values()) {
                        if (a.getId().equals(exclude)) continue;
                        if (!List.of("ACTIVE", "SCHEDULED", "APPROVED").contains(a.getBusinessStatus())) continue;
                        if (a.getEffectiveFrom() == null) continue;
                        LocalDate aEnd = a.getEffectiveUntil() == null ? LocalDate.MAX : a.getEffectiveUntil();
                        LocalDate bEnd = until == null ? LocalDate.MAX : until;
                        if (!from.isAfter(aEnd) && !a.getEffectiveFrom().isAfter(bEnd)) {
                            out.add(a);
                        }
                    }
                    return out;
                });
        lenient().when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(routingRepository.countBySelectedApplicabilityId(any())).thenReturn(0L);
    }

    @Test
    void schedule_persistsAndResolvesExactlyOne() {
        UUID docId = UUID.randomUUID();
        CiPolicyApplicability draft = catalogue.upsertDraft(tenantId, Map.of(
                "policyDocumentId", docId.toString(),
                "policyName", "DigiLeap Credit Policy",
                "policyVersion", "v1",
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-04-01",
                "effectiveUntil", "2026-08-31",
                "createdBy", "cm"));
        catalogue.transition(draft.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        Map<String, Object> scheduled = catalogue.schedule(draft.getId(), Map.of(
                "effectiveFrom", "2026-04-01",
                "effectiveUntil", "2026-08-31",
                "products", List.of("DIGILEAP"),
                "businessDate", "2026-08-25"));
        assertThat(scheduled.get("status")).isIn(PolicyBusinessLifecycleStatus.ACTIVE, PolicyBusinessLifecycleStatus.SCHEDULED);
        assertThat(scheduled.get("allowCanonicalAuthority")).isEqualTo(false);

        Map<String, Object> resolved = catalogue.resolve(tenantId, new ApplicationPolicyQuery(
                "APP-1", "DIGILEAP", null, null, null, null, null, null, LocalDate.of(2026, 8, 25)));
        // Unlinked catalogue match must NOT silently execute — P2 outcome
        assertThat(resolved.get("outcome")).isEqualTo(PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE);
        assertThat(resolved.get("durableCatalogue")).isEqualTo(true);
        assertThat(resolved.get("shadowRoutable")).isEqualTo(false);
    }

    @Test
    void resolve_linkedPackage_isExactlyOne() {
        UUID docId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("policyDocumentId", docId.toString());
        body.put("policyName", "DigiLeap Credit Policy");
        body.put("policyVersion", "v2");
        body.put("products", List.of("DIGILEAP"));
        body.put("policyVersionId", versionId.toString());
        body.put("approvedBy", "cm");
        body.put("checker", "checker");
        body.put("dataReadinessStatus", "PASSED");
        body.put("testsStatus", "APPROVED");
        body.put("simulationReviewStatus", "REVIEWED");
        body.put("createdBy", "cm");
        CiPolicyApplicability draft = catalogue.upsertDraft(tenantId, body);
        catalogue.transition(draft.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        catalogue.schedule(draft.getId(), Map.of(
                "effectiveFrom", "2026-04-01",
                "effectiveUntil", "2026-08-31",
                "products", List.of("DIGILEAP"),
                "businessDate", "2026-08-25"));

        Map<String, Object> resolved = catalogue.resolve(tenantId, new ApplicationPolicyQuery(
                "APP-2", "DIGILEAP", null, null, null, null, null, null, LocalDate.of(2026, 8, 25)));
        assertThat(resolved.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolved.get("resolvedPolicyVersionId")).isEqualTo(versionId.toString());
        assertThat(resolved.get("shadowRoutable")).isEqualTo(true);
    }

    @Test
    void overlappingSchedule_blocked() {
        CiPolicyApplicability v3 = catalogue.upsertDraft(tenantId, Map.of(
                "policyDocumentId", UUID.randomUUID().toString(),
                "policyName", "DigiLeap Credit Policy",
                "policyVersion", "v3",
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-01",
                "createdBy", "cm"));
        catalogue.transition(v3.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        catalogue.schedule(v3.getId(), Map.of(
                "effectiveFrom", "2026-09-01",
                "products", List.of("DIGILEAP"),
                "businessDate", "2026-09-01"));

        CiPolicyApplicability v4 = catalogue.upsertDraft(tenantId, Map.of(
                "policyDocumentId", UUID.randomUUID().toString(),
                "policyName", "DigiLeap Credit Policy",
                "policyVersion", "v4",
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-15",
                "createdBy", "cm"));
        catalogue.transition(v4.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");

        assertThatThrownBy(() -> catalogue.schedule(v4.getId(), Map.of(
                "effectiveFrom", "2026-09-15",
                "products", List.of("DIGILEAP"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DIGILEAP");
    }

    @Test
    void differentProduct_overlappingDates_allowed() {
        CiPolicyApplicability digi = catalogue.upsertDraft(tenantId, Map.of(
                "policyDocumentId", UUID.randomUUID().toString(),
                "policyName", "DigiLeap",
                "policyVersion", "v1",
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-01"));
        catalogue.transition(digi.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        catalogue.schedule(digi.getId(), Map.of(
                "effectiveFrom", "2026-09-01",
                "products", List.of("DIGILEAP"),
                "businessDate", "2026-09-01"));

        CiPolicyApplicability smart = catalogue.upsertDraft(tenantId, Map.of(
                "policyDocumentId", UUID.randomUUID().toString(),
                "policyName", "Smart Switch",
                "policyVersion", "v1",
                "products", List.of("SMART_SWITCH"),
                "effectiveFrom", "2026-09-01"));
        catalogue.transition(smart.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        Map<String, Object> scheduled = catalogue.schedule(smart.getId(), Map.of(
                "effectiveFrom", "2026-09-01",
                "products", List.of("SMART_SWITCH"),
                "businessDate", "2026-09-01"));
        assertThat(scheduled.get("status")).isIn(PolicyBusinessLifecycleStatus.ACTIVE, PolicyBusinessLifecycleStatus.SCHEDULED);
    }

    @Test
    void amountBand_scopesCorrectly() {
        UUID versionId = UUID.randomUUID();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("policyDocumentId", UUID.randomUUID().toString());
        body.put("policyName", "DigiLeap");
        body.put("policyVersion", "v1");
        body.put("products", List.of("DIGILEAP"));
        body.put("minLoanAmount", "100000");
        body.put("maxLoanAmount", "500000");
        body.put("effectiveFrom", "2026-01-01");
        body.put("policyVersionId", versionId.toString());
        CiPolicyApplicability banded = catalogue.upsertDraft(tenantId, body);
        catalogue.transition(banded.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                PolicyCatalogueService.EVT_APPROVED, "cm", "ok");
        catalogue.schedule(banded.getId(), Map.of(
                "effectiveFrom", "2026-01-01",
                "products", List.of("DIGILEAP"),
                "minLoanAmount", "100000",
                "maxLoanAmount", "500000",
                "businessDate", "2026-06-01"));

        assertThat(catalogue.resolve(tenantId, new ApplicationPolicyQuery(
                "A", "DIGILEAP", null, null, null, null, null,
                new BigDecimal("50000"), LocalDate.of(2026, 6, 1))).get("outcome"))
                .isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
        assertThat(catalogue.resolve(tenantId, new ApplicationPolicyQuery(
                "A", "DIGILEAP", null, null, null, null, null,
                new BigDecimal("250000"), LocalDate.of(2026, 6, 1))).get("outcome"))
                .isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
    }
}
