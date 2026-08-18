package com.los.core.service.workflow.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowIntakeValidatorTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private WorkflowIntakeValidator validator;

    @Test
    void legacyPolicySkipsValidation() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .personalInfo(Map.of())
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(KycStepIntakeCatalog.legacyIntakeConfig())
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true)))
                .build();
        assertThatCode(() -> validator.validateAtSubmit(app, wf)).doesNotThrowAnyException();
    }

    @Test
    void workflowDrivenRequiresPanWhenStepConfigured() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(12)
                .personalInfo(Map.of("dateOfBirth", "1990-01-01"))
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig())
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "collectAtIntake", true)))
                .build();

        assertThatThrownBy(() -> validator.validateAtSubmit(app, wf))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PAN_VERIFY");
    }

    @Test
    void anyGroupSatisfiedWithVoterId() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> intakeConfig = new java.util.LinkedHashMap<>(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig());
        intakeConfig.put("mandatoryFieldGroups", List.of(Map.of(
                "id", "govt",
                "label", "Government ID",
                "logic", "ANY",
                "steps", List.of("AADHAAR_OTP", "VOTER_ID_VERIFY", "DL_VERIFY"))));

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(6)
                .personalInfo(Map.of(
                        "dateOfBirth", "1990-01-01",
                        "voterId", "ABC1234567",
                        "occupation", "SALARIED_PRIVATE",
                        "loanPurpose", "EDUCATION"))
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(intakeConfig)
                .steps(List.of(
                        Map.of("step", "AADHAAR_OTP", "mandatory", true),
                        Map.of("step", "VOTER_ID_VERIFY", "mandatory", true),
                        Map.of("step", "DL_VERIFY", "mandatory", true)))
                .build();
        when(documentRepository.findByApplicationIdAndIsLatestTrueOrderByCreatedAtDesc(appId))
                .thenReturn(List.of());

        assertThatCode(() -> validator.validateAtSubmit(app, wf)).doesNotThrowAnyException();
    }

    @Test
    void workflowDrivenValidatesRequiredCustomFields() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> intakeConfig = new java.util.LinkedHashMap<>(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig());
        intakeConfig.put("customFields", List.of(Map.of(
                "key", "gstTurnover",
                "label", "GST Turnover",
                "type", "NUMBER",
                "required", true)));

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(6)
                .personalInfo(Map.of(
                        "dateOfBirth", "1990-01-01",
                        "occupation", "SALARIED_PRIVATE",
                        "loanPurpose", "EDUCATION",
                        "customFields", Map.of()))
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(intakeConfig)
                .steps(List.of())
                .build();

        assertThatThrownBy(() -> validator.validateAtSubmit(app, wf))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("GST Turnover");
    }

    @Test
    void allowedStatesMatchIsoCodeAgainstConfiguredName() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .personalInfo(Map.of("state", "TN"))
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(Map.of("policy", "LEGACY", "allowedStates", List.of("Tamil Nadu")))
                .steps(List.of())
                .build();
        assertThatCode(() -> validator.validateAtSubmit(app, wf)).doesNotThrowAnyException();
    }

    @Test
    void panAliasSatisfiesWorkflowDrivenPanVerify() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(12)
                .personalInfo(Map.of("pan", "ABCDE1234F", "dateOfBirth", "1990-01-01",
                        "occupation", "SALARIED_PRIVATE", "loanPurpose", "EDUCATION"))
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .intakeConfig(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig())
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "collectAtIntake", true)))
                .build();
        when(documentRepository.findByApplicationIdAndIsLatestTrueOrderByCreatedAtDesc(appId))
                .thenReturn(List.of());
        assertThatCode(() -> validator.validateAtSubmit(app, wf)).doesNotThrowAnyException();
    }

    @Test
    void twoWorkflowVersionsCanRequireDifferentIntakeFields() {
        UUID appId = UUID.randomUUID();
        LoanApplication panApp = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(12)
                .personalInfo(Map.of("gstin", "22AAAAA0000A1Z5", "dateOfBirth", "1990-01-01",
                        "occupation", "SALARIED_PRIVATE", "loanPurpose", "EDUCATION"))
                .build();
        WorkflowConfig panWorkflow = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .intakeConfig(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig())
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "collectAtIntake", true)))
                .build();
        WorkflowConfig gstWorkflow = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .intakeConfig(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig())
                .steps(List.of(Map.of("step", "GSTIN_VERIFY", "mandatory", true, "collectAtIntake", true)))
                .build();
        when(documentRepository.findByApplicationIdAndIsLatestTrueOrderByCreatedAtDesc(appId))
                .thenReturn(List.of());
        assertThatThrownBy(() -> validator.validateAtSubmit(panApp, panWorkflow))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PAN_VERIFY");
        assertThatCode(() -> validator.validateAtSubmit(panApp, gstWorkflow)).doesNotThrowAnyException();
    }

    @Test
    void requiredPanDoesNotDisappearWhenALaterGstWorkflowExists() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .tenureMonths(12)
                .personalInfo(Map.of("dateOfBirth", "1990-01-01",
                        "occupation", "SALARIED_PRIVATE", "loanPurpose", "EDUCATION"))
                .build();
        WorkflowConfig pinnedPan = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .intakeConfig(KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig())
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "collectAtIntake", true)))
                .build();
        assertThatThrownBy(() -> validator.validateAtSubmit(app, pinnedPan))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PAN_VERIFY");
    }
}
