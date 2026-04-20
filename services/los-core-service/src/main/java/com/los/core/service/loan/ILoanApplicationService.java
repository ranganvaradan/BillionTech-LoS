package com.los.core.service.loan;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface ILoanApplicationService {

    ApplicationResponse createApplication(CreateApplicationRequest request, UUID customerId);

    ApplicationResponse getApplication(UUID applicationId);

    Page<ApplicationResponse> listApplications(ApplicationStatus status, String borrowerType, Pageable pageable);

    ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request);

    ApplicationResponse transitionStatus(UUID applicationId, ApplicationStatus newStatus, String remarks);

    Map<String, Object> getDashboardSummary();
}
