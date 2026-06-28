package com.los.core.service.loan;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.request.ValidateIdentityRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface ILoanApplicationService {

    /**
     * @param actingUserId   Logged-in user from {@code X-User-Id} (or null for unauthenticated / tests only on staff paths).
     * @param actingUserRole from {@code X-User-Role}
     */
    ApplicationResponse createApplication(CreateApplicationRequest request, UUID actingUserId, String actingUserRole);

    ApplicationResponse getApplication(UUID applicationId);

    Page<ApplicationResponse> listApplications(
            ApplicationStatus status, String borrowerType, String intakeSegment, Pageable pageable);

    ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request);

    /** Throws {@link com.los.core.exception.BusinessRuleException} when email/mobile/PAN/GSTIN is already in use. */
    void validateIdentity(ValidateIdentityRequest request);

    ApplicationResponse transitionStatus(UUID applicationId, ApplicationStatus newStatus, String remarks);

    ApplicationResponse setManualBureau(UUID applicationId, Integer manualBureauScore, String manualBureauRemarks, UUID manualBureauDocumentId, UUID performedBy);

    ApplicationResponse applyManualCreditInputs(UUID applicationId, ManualCreditInputsRequest request, UUID performedBy);

    Map<String, Object> getDashboardSummary();
}
