package com.los.core.creditintelligence;

import com.los.core.creditintelligence.api.CreditIntelligenceAdminController;
import com.los.core.creditintelligence.repository.CiCreditEvaluationRepository;
import com.los.core.creditintelligence.repository.CiEvaluationStageRepository;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.repository.CiStandardRuleResultRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.CreditIntelligenceValidationReportService;
import com.los.core.creditintelligence.service.ShadowCreditEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditIntelligenceAdminControllerTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiUnderwritingFactRepository factRepository;
    @Mock
    private CiPolicyVersionRepository policyVersionRepository;
    @Mock
    private CiCreditEvaluationRepository evaluationRepository;
    @Mock
    private CiEvaluationStageRepository stageRepository;
    @Mock
    private CiStandardRuleResultRepository ruleResultRepository;
    @Mock
    private ShadowCreditEvaluationService shadowCreditEvaluationService;
    @Mock
    private CreditIntelligenceValidationReportService validationReportService;

    private CreditIntelligenceAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CreditIntelligenceAdminController(
                snapshotRepository, factRepository, policyVersionRepository,
                evaluationRepository, stageRepository, ruleResultRepository,
                shadowCreditEvaluationService, validationReportService);
        ReflectionTestUtils.setField(controller, "internalToken", "secret-ci-token");
    }

    @Test
    void rejectsMissingTokenWhenConfigured() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.listSnapshots(UUID.randomUUID(), null, null));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void allowsMatchingToken() {
        UUID appId = UUID.randomUUID();
        when(snapshotRepository.findByApplicationIdOrderBySnapshotVersionDesc(appId)).thenReturn(List.of());
        var result = controller.listSnapshots(appId, "secret-ci-token", null);
        assertEquals(0, result.size());
    }
}
