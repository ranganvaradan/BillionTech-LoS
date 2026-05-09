package com.los.core.service.cam;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.CamUpdateRequest;
import com.los.core.model.dto.response.CamResponse;
import com.los.core.model.entity.CreditAppraisalMemo;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingEvaluationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.kyc.IKycOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditAppraisalServiceTest {

    @Mock
    private CreditAppraisalMemoRepository camRepository;
    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private UnderwritingEvaluationRepository underwritingEvaluationRepository;
    @Mock
    private UnderwritingScorecardRepository scorecardRepository;
    @Mock
    private IKycOrchestrationService kycOrchestrationService;
    @Mock
    private CreditControlService creditControlService;

    @InjectMocks
    private CreditAppraisalService service;

    @Test
    void ensureCam_populatesSectionExtended() {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("T-1001")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("500000"))
                .tenureMonths(36)
                .status(ApplicationStatus.CAM_READY)
                .personalInfo(Map.of("fullName", "Test User", "monthlyNetIncome", "45000"))
                .build();
        when(underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(id))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(id))
                .thenReturn(Map.of("outcome", "PASS", "exceptions", List.of()));
        when(creditControlService.buildReadView(any(LoanApplication.class))).thenReturn(Map.of("effective", Map.of()));
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.empty());
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));

        CreditAppraisalMemo saved = service.ensureCamForApplication(app);

        assertThat(saved.getCamJson()).containsKey("sectionExtended");
        @SuppressWarnings("unchecked")
        Map<String, Object> ext = (Map<String, Object>) saved.getCamJson().get("sectionExtended");
        assertThat(ext).containsKeys("executiveSummary", "borrowerProfile", "riskFlags", "completenessPercent");
    }

    @Test
    void updateCam_persistsEditableSections() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camVersion(1)
                .camStatus("DRAFT")
                .camJson(Map.of("sectionExtended", Map.of("a", 1)))
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder().id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        CamUpdateRequest req = new CamUpdateRequest();
        req.setEditableSectionsPatch(Map.of("executiveSummaryNarrative", "Edited summary for committee"));
        CamResponse res = service.updateCam(id, req);

        ArgumentCaptor<CreditAppraisalMemo> cap = ArgumentCaptor.forClass(CreditAppraisalMemo.class);
        verify(camRepository).save(cap.capture());
        assertThat(cap.getValue().getEditableSectionsJson()).containsEntry("executiveSummaryNarrative", "Edited summary for committee");
        assertThat(res.getEditableSections()).containsKey("executiveSummaryNarrative");
    }

    @Test
    void submitCam_transitionsToSubmitted() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder().id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        CamResponse res = service.submitCam(id);
        assertThat(res.getCamStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void markReviewed_failsIfAlreadyApproved() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("APPROVED")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));

        assertThatThrownBy(() -> service.markReviewed(id, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void renderCamPdf_doesNotDumpRawJsonBraces() {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("P-9")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .status(ApplicationStatus.CAM_REVIEWED)
                .build();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("APPROVED")
                .camVersion(1)
                .camJson(Map.of("sectionExtended", Map.of("executiveSummary", Map.of("A", "B"))))
                .recommendedDecision("APPROVE")
                .build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(id))
                .thenReturn(Optional.empty());
        byte[] pdf = service.renderCamPdf(id);
        assertThat(pdf.length).isGreaterThan(200);
        assertThat(new String(pdf, 0, Math.min(8, pdf.length), StandardCharsets.US_ASCII)).startsWith("%PDF");
    }
}
