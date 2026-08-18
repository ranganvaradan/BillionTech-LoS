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
import com.los.core.service.credit.LimitSizingService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.loan.WorkflowRoleGuard;
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
    @Mock
    private LimitSizingService limitSizingService;
    @Mock
    private WorkflowRoleGuard workflowRoleGuard;

    @InjectMocks
    private CreditAppraisalService service;

    private static CreditAppraisalMemo camWithBasis(UUID id, String status) {
        return CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus(status)
                .camJson(Map.of())
                .recommendedAmount(new BigDecimal("125000"))
                .recommendedTenureMonths(12)
                .recommendedRate(new BigDecimal("14.5"))
                .build();
    }

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
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.empty());
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        when(underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(id))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(id)).thenReturn(Map.of("outcome", "PASS"));
        when(creditControlService.buildReadView(app)).thenReturn(Map.of());
        when(limitSizingService.capRecommendedIfConfigured(any(), any())).thenAnswer(i -> i.getArgument(1));

        CreditAppraisalMemo cam = service.ensureCamForApplication(app);
        assertThat(cam.getCamJson()).containsKey("sectionExtended");
        assertThat(cam.getRecommendedAmount()).isEqualByComparingTo("500000");
        assertThat(cam.getRecommendedTenureMonths()).isEqualTo(36);
    }

    @Test
    void ensureCam_populatesSanctioningDefaultsFromApplicationRequest() {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("T-2")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .requestedAmount(new BigDecimal("100000"))
                .tenureMonths(24)
                .interestRate(new BigDecimal("12"))
                .status(ApplicationStatus.CAM_READY)
                .personalInfo(Map.of("fullName", "A"))
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.empty());
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        when(underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(id))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(id)).thenReturn(Map.of("outcome", "PASS"));
        when(creditControlService.buildReadView(app)).thenReturn(Map.of());
        when(limitSizingService.capRecommendedIfConfigured(any(), any())).thenAnswer(i -> i.getArgument(1));

        CreditAppraisalMemo cam = service.ensureCamForApplication(app);
        assertThat(cam.getRecommendedRate()).isEqualByComparingTo("12");
    }

    @Test
    void ensureCam_retryIsIdempotentOnSameApplication() {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("T-RETRY")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .requestedAmount(new BigDecimal("100000"))
                .tenureMonths(12)
                .status(ApplicationStatus.CAM_READY)
                .personalInfo(Map.of("fullName", "Retry"))
                .build();
        CreditAppraisalMemo existing = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camVersion(1)
                .camJson(Map.of("prior", true))
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(existing));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        when(underwritingEvaluationRepository.findTopByApplicationIdOrderByEvaluatedAtDesc(id))
                .thenReturn(Optional.empty());
        when(kycOrchestrationService.computeKycOutcome(id)).thenReturn(Map.of("outcome", "PASS"));
        when(creditControlService.buildReadView(app)).thenReturn(Map.of());
        when(limitSizingService.capRecommendedIfConfigured(any(), any())).thenAnswer(i -> i.getArgument(1));

        CreditAppraisalMemo first = service.ensureCamForApplication(app);
        CreditAppraisalMemo second = service.ensureCamForApplication(app);
        assertThat(first.getApplicationId()).isEqualTo(id);
        assertThat(second.getApplicationId()).isEqualTo(id);
        assertThat(first.getCamVersion()).isEqualTo(1);
        assertThat(second.getCamVersion()).isEqualTo(1);
        ArgumentCaptor<CreditAppraisalMemo> cap = ArgumentCaptor.forClass(CreditAppraisalMemo.class);
        verify(camRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).allMatch(c -> id.equals(c.getApplicationId()));
    }

    @Test
    void ensureCam_sourceDoesNotWriteUnderwritingFacts() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/los/core/service/cam/CreditAppraisalService.java"));
        assertThat(src).doesNotContain("CiUnderwritingFact");
        assertThat(src).doesNotContain("factRepository");
        assertThat(src).contains("CreditAppraisalMemo");
    }

    @Test
    void getCam_backfillsMissingSanctioningDefaultsOnRead() {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .applicationNumber("T-3")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .requestedAmount(new BigDecimal("200000"))
                .tenureMonths(18)
                .status(ApplicationStatus.CAM_READY)
                .personalInfo(Map.of("fullName", "B"))
                .build();
        CreditAppraisalMemo existing = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(existing));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        when(limitSizingService.capRecommendedIfConfigured(any(), any())).thenAnswer(i -> i.getArgument(1));

        CamResponse res = service.getCam(id);
        assertThat(res.getRecommendedAmount()).isEqualByComparingTo("200000");
        assertThat(res.getRecommendedTenureMonths()).isEqualTo(18);
    }

    @Test
    void updateCam_persistsEditableSections() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder()
                .id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        CamUpdateRequest req = new CamUpdateRequest();
        req.setEditableSectionsPatch(Map.of("executiveSummaryNarrative", "Note"));
        CamResponse res = service.updateCam(id, req, null);
        assertThat(res.getEditableSections()).containsEntry("executiveSummaryNarrative", "Note");
    }

    @Test
    void updateCam_persistsInterestTypeAndRate() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder()
                .id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        CamUpdateRequest req = new CamUpdateRequest();
        req.setRecommendedRate(new BigDecimal("13.25"));
        req.setInterestType("REDUCING");
        CamResponse res = service.updateCam(id, req, null);
        assertThat(res.getRecommendedRate()).isEqualByComparingTo("13.25");
        assertThat(res.getInterestType()).isEqualTo("REDUCING");
        assertThat(cam.getInterestType()).isEqualTo("REDUCING");
        assertThat(cam.getRecommendedRate()).isEqualByComparingTo("13.25");
    }

    @Test
    void updateCam_rejectsWhenApproved() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("APPROVED")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));

        CamUpdateRequest req = new CamUpdateRequest();
        req.setRecommendedRate(new BigDecimal("10"));
        assertThatThrownBy(() -> service.updateCam(id, req, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void submitCam_requiresProposedRate() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .recommendedAmount(new BigDecimal("125000"))
                .recommendedTenureMonths(12)
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));

        assertThatThrownBy(() -> service.submitCam(id, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getReason())
                .isEqualTo("CAM_SANCTION_BASIS_INCOMPLETE");
    }

    @Test
    void submitCam_transitionsToSubmitted() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = camWithBasis(id, "DRAFT");
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder().id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        CamResponse res = service.submitCam(id, null);
        assertThat(res.getCamStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void submitCam_fromCamSentBack_restoresCamReadyApplicationStatus() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = camWithBasis(id, "SENT_BACK");
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder().id(id).applicationNumber("X").status(ApplicationStatus.CAM_SENT_BACK).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        CamResponse res = service.submitCam(id, null);
        assertThat(res.getCamStatus()).isEqualTo("SUBMITTED");
        assertThat(res.getApplicationStatus()).isEqualTo("CAM_READY");

        ArgumentCaptor<LoanApplication> cap = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ApplicationStatus.CAM_READY);
    }

    @Test
    void sendBackCam_movesApplicationToCamSentBack() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("SUBMITTED")
                .camJson(Map.of())
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder().id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        CamResponse res = service.sendBackCam(id, null, "Please revise");
        assertThat(res.getCamStatus()).isEqualTo("SENT_BACK");
        assertThat(res.getApplicationStatus()).isEqualTo("CAM_SENT_BACK");

        ArgumentCaptor<LoanApplication> cap = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ApplicationStatus.CAM_SENT_BACK);
    }

    @Test
    void sendBackCam_fromApproved_opensRevisionWithoutSilentEdit() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("APPROVED")
                .camReviewed(true)
                .camVersion(1)
                .camJson(Map.of())
                .recommendedAmount(new BigDecimal("125000"))
                .recommendedTenureMonths(12)
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder()
                .id(id).applicationNumber("X").status(ApplicationStatus.SANCTION_PENDING).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        CamResponse res = service.sendBackCam(id, null, "Enter proposed rate");
        assertThat(res.getCamStatus()).isEqualTo("SENT_BACK");
        assertThat(res.isCamReviewed()).isFalse();
        assertThat(res.getCamVersion()).isEqualTo(2);
        assertThat(res.getApplicationStatus()).isEqualTo("CAM_SENT_BACK");
        assertThat(cam.getRecommendedRate()).isNull();
    }

    @Test
    void markReviewed_requiresProposedRate() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = CreditAppraisalMemo.builder()
                .applicationId(id)
                .camStatus("DRAFT")
                .camJson(Map.of())
                .recommendedAmount(new BigDecimal("125000"))
                .recommendedTenureMonths(12)
                .build();
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));

        assertThatThrownBy(() -> service.markReviewed(id, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getReason())
                .isEqualTo("CAM_SANCTION_BASIS_INCOMPLETE");
    }

    @Test
    void markReviewed_preservesRateAndApproves() {
        UUID id = UUID.randomUUID();
        CreditAppraisalMemo cam = camWithBasis(id, "SUBMITTED");
        cam.setInterestType("UPFRONT");
        when(camRepository.findByApplicationId(id)).thenReturn(Optional.of(cam));
        when(camRepository.save(any(CreditAppraisalMemo.class))).thenAnswer(i -> i.getArgument(0));
        LoanApplication app = LoanApplication.builder()
                .id(id).applicationNumber("X").status(ApplicationStatus.CAM_READY).build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));

        service.markReviewed(id, null);
        assertThat(cam.getCamStatus()).isEqualTo("APPROVED");
        assertThat(cam.getRecommendedRate()).isEqualByComparingTo("14.5");
        assertThat(cam.getInterestType()).isEqualTo("UPFRONT");
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
