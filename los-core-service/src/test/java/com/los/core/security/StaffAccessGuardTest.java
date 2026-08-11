package com.los.core.security;

import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAccessGuardTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Test
    void unauthenticated_denied() {
        StaffAccessGuard guard = new StaffAccessGuard(loanApplicationRepository);
        assertThatThrownBy(() -> guard.requireStaff(null, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void borrowerRole_forbidden() {
        StaffAccessGuard guard = new StaffAccessGuard(loanApplicationRepository);
        UUID uid = UUID.randomUUID();
        assertThatThrownBy(() -> guard.requireStaff(uid.toString(), "BORROWER"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void creditManager_canAccessDocument() {
        StaffAccessGuard guard = new StaffAccessGuard(loanApplicationRepository);
        UUID appId = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        Document doc = Document.builder().id(UUID.randomUUID()).applicationId(appId).fileName("x.pdf").build();
        when(loanApplicationRepository.existsById(appId)).thenReturn(true);
        assertThatCode(() -> guard.assertCanAccessDocument(uid.toString(), "CREDIT_MANAGER", doc))
                .doesNotThrowAnyException();
    }

    @Test
    void unauthorizedStaff_sameNotFoundMessage() {
        StaffAccessGuard guard = new StaffAccessGuard(loanApplicationRepository);
        UUID appId = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        Document doc = Document.builder().id(UUID.randomUUID()).applicationId(appId).fileName("x.pdf").build();
        LoanApplication app = LoanApplication.builder().id(appId).assignedTo(UUID.randomUUID()).build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        // ACCOUNTS is staff but not privileged / not assignee → 404
        assertThatThrownBy(() -> guard.assertCanAccessDocument(uid.toString(), "ACCOUNTS", doc))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found");
    }
}
