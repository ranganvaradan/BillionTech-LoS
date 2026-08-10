package com.los.core.controller;

import com.los.core.config.WebSecurityConfig;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.borrower.BorrowerApplicationStatusService;
import com.los.core.service.borrower.BorrowerPortalService;
import com.los.core.service.document.IDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BorrowerApplicationController.class)
@Import({BorrowerApplicationStatusService.class, WebSecurityConfig.class})
class BorrowerApplicationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanApplicationRepository applicationRepository;

    @MockBean
    private IDocumentService documentService;

    @MockBean
    private BorrowerPortalService borrowerPortalService;

    @Test
    void getBorrowerStatus_returnsOnlyBorrowerFields() throws Exception {
        UUID id = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("LOS-IND-20260425-00001")
                .customerId(UUID.fromString("7ba7b810-9dad-11d1-80b4-00c04fd430c8"))
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .requestedAmount(BigDecimal.valueOf(1_00_000))
                .status(ApplicationStatus.KYC_IN_PROGRESS)
                .personalInfo(Map.of("fullName", "Test User"))
                .esignTransactionId(null)
                .build();
        when(applicationRepository.findById(eq(id))).thenReturn(Optional.of(app));
        when(documentService.getDocuments(eq(id))).thenReturn(List.of());
        when(documentService.isDocumentChecklistComplete(eq(id))).thenReturn(false);

        mockMvc.perform(get("/api/v1/borrower/applications/{applicationId}/status", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(id.toString()))
                .andExpect(jsonPath("$.customerName").value("Test User"))
                .andExpect(jsonPath("$.status").value("KYC_IN_PROGRESS"))
                .andExpect(jsonPath("$.kycStatus").isString())
                // Borrower DTO must not include internal credit fields
                .andExpect(jsonPath("$.bureauScore").doesNotExist());
        verify(applicationRepository).findById(id);
    }

    @Test
    void deleteDraft_callsBorrowerPortalService() throws Exception {
        UUID appId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        UUID uid = UUID.fromString("7ba7b810-9dad-11d1-80b4-00c04fd430c8");
        mockMvc.perform(delete("/api/v1/borrower/applications/{applicationId}/draft", appId)
                        .header("X-User-Id", uid.toString())
                        .header("X-User-Role", "BORROWER"))
                .andExpect(status().isNoContent());
        verify(borrowerPortalService).deleteUnsubmittedApplication(uid, appId);
    }
}
