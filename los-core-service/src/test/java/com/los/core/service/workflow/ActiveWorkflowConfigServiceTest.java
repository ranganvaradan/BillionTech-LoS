package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveWorkflowConfigServiceTest {

    @Mock
    private ApplicationWorkflowResolver applicationWorkflowResolver;

    @InjectMocks
    private ActiveWorkflowConfigService service;

    @Test
    void findActive_delegatesToResolver() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .intakeSegment(IntakeSegment.ANCHOR)
                .build();
        WorkflowConfig cfg = WorkflowConfig.builder().id(UUID.randomUUID()).build();
        when(applicationWorkflowResolver.requireConfig(app)).thenReturn(cfg);
        assertEquals(cfg, service.findActiveForApplication(app).orElseThrow());
        verify(applicationWorkflowResolver).requireConfig(app);
    }

    @Test
    void findActive_emptyWhenNotResolved() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("  ")
                .build();
        when(applicationWorkflowResolver.requireConfig(app)).thenThrow(new BusinessRuleException(
                "No Workflow could be resolved",
                "WORKFLOW_NOT_RESOLVED",
                "RESOLVE_WORKFLOW",
                MapLike.empty()));
        assertTrue(service.findActiveForApplication(app).isEmpty());
    }

    @Test
    void findActive_brokenReferenceFailsClosed() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .workflowId(UUID.randomUUID())
                .build();
        when(applicationWorkflowResolver.requireConfig(app)).thenThrow(new BusinessRuleException(
                "Persisted Workflow Version not found",
                "WORKFLOW_VERSION_NOT_FOUND",
                "RESOLVE_WORKFLOW",
                MapLike.empty()));
        assertThrows(BusinessRuleException.class, () -> service.findActiveForApplication(app));
    }

    /** Avoid importing Map in every call site for empty context. */
    private static final class MapLike {
        static java.util.Map<String, Object> empty() {
            return java.util.Map.of();
        }
    }
}
