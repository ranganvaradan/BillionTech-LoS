package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyShadowEligibilityServiceTest {

    @Mock
    private CiPolicyVersionRepository policyVersionRepository;
    @Mock
    private CiExecutablePolicyPackageRepository executablePackageRepository;

    @InjectMocks
    private PolicyShadowEligibilityService service;

    @Test
    void unlinked_isNotExecutable() {
        CiPolicyApplicability a = CiPolicyApplicability.builder()
                .id(UUID.randomUUID())
                .policyName("DigiLeap")
                .policyVersionLabel("v1")
                .businessStatus("ACTIVE")
                .products(List.of("DIGILEAP"))
                .approvedBy("cm")
                .checker("checker")
                .dataReadinessStatus("PASSED")
                .testsStatus("APPROVED")
                .simulationReviewStatus("REVIEWED")
                .reasonForChange("P1 smoke v1")
                .build();
        var r = service.evaluate(a);
        assertThat(r.eligible()).isFalse();
        assertThat(r.shadowEligibility()).isEqualTo(PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE);
    }

    @Test
    void linkedVersion_isEligible() {
        UUID vid = UUID.randomUUID();
        when(policyVersionRepository.findById(vid)).thenReturn(Optional.of(CiPolicyVersion.builder()
                .id(vid)
                .contentHash("abc123")
                .status("PUBLISHED")
                .policyContent(java.util.Map.of("k", "v"))
                .build()));
        CiPolicyApplicability a = CiPolicyApplicability.builder()
                .id(UUID.randomUUID())
                .policyName("DigiLeap")
                .policyVersionLabel("v1")
                .businessStatus("ACTIVE")
                .products(List.of("DIGILEAP"))
                .policyVersionId(vid)
                .approvedBy("cm")
                .checker("checker")
                .dataReadinessStatus("PASSED")
                .testsStatus("APPROVED")
                .simulationReviewStatus("REVIEWED")
                .build();
        var r = service.evaluate(a);
        assertThat(r.eligible()).isTrue();
        assertThat(r.linkageClass()).isEqualTo(PolicyShadowRoutingOutcomes.LINK_PROPER);
        assertThat(r.contentHash()).isEqualTo("abc123");
    }

    @Test
    void executableShadowPackage_isEligible() {
        UUID eid = UUID.randomUUID();
        when(executablePackageRepository.findById(eid)).thenReturn(Optional.of(CiExecutablePolicyPackage.builder()
                .id(eid)
                .status(ExecutablePackageStatus.SHADOW.name())
                .contentHash("pkghash")
                .policyCode("X")
                .version("v1")
                .tenantId(UUID.randomUUID())
                .build()));
        CiPolicyApplicability a = CiPolicyApplicability.builder()
                .id(UUID.randomUUID())
                .policyName("DigiLeap")
                .policyVersionLabel("v1")
                .businessStatus("SCHEDULED")
                .products(List.of("DIGILEAP"))
                .executablePackageId(eid)
                .approvedBy("cm")
                .checker("checker")
                .dataReadinessStatus("PASSED")
                .testsStatus("APPROVED")
                .simulationReviewStatus("REVIEWED")
                .build();
        var r = service.evaluate(a);
        assertThat(r.eligible()).isTrue();
        assertThat(r.packageContentHash()).isEqualTo("pkghash");
    }
}
