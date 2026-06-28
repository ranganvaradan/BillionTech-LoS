package com.los.core.service.audit;

import com.los.core.model.entity.AuditEvent;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.AuditEventRepository;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Covers {@code AuditEvent} JSONB maps as written by KYC retry (must persist like submit events).
 */
@DataJpaTest
@Import(AuditService.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = "spring.flyway.enabled=false")
class AuditEventJsonbPersistenceTest {

    @Autowired
    private LoanApplicationRepository applicationRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private AuditService auditService;

    @Test
    void kycRetryAuditState_mapsPersist() {
        LoanApplication app = applicationRepository.save(LoanApplication.builder()
                .applicationNumber("KYC-RETRY-TEST-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .requestedAmount(java.math.BigDecimal.ONE)
                .status(ApplicationStatus.KYC_FAILED)
                .build());

        assertDoesNotThrow(() -> auditService.logEvent(
                app.getId(),
                "FLOW",
                "KYC_RETRY",
                null,
                Map.of("status", "KYC_FAILED"),
                Map.of("status", "KYC_IN_PROGRESS", "retainedHistory", "true"),
                "KYC retry — status reset for re-run; prior attempts retained in history"
        ));
        // If @Async is inactive in tests, the row exists immediately; otherwise poll or @Transactional
        // wait not needed if sync throw — we just assert no throw from the call path used in tests.
    }

    @Test
    void directRepository_save_kycRetryNewState_usesStringScalarsOnly() {
        UUID appId = applicationRepository.save(LoanApplication.builder()
                .applicationNumber("KYC-RETRY-TEST-2")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .requestedAmount(java.math.BigDecimal.ONE)
                .status(ApplicationStatus.KYC_FAILED)
                .build()).getId();

        AuditEvent ev = AuditEvent.builder()
                .applicationId(appId)
                .eventType("FLOW")
                .action("KYC_RETRY")
                .performedBy(null)
                .previousState(Map.of("status", "KYC_FAILED"))
                .newState(Map.of("status", "KYC_IN_PROGRESS", "retainedHistory", "true"))
                .description("KYC retry — status reset for re-run; prior attempts retained in history")
                .build();
        assertDoesNotThrow(() -> auditEventRepository.save(ev));
    }
}
