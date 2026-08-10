package com.los.plp.service;

import com.los.core.model.entity.LoanApplication;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.request.PlpApplicationCleanupRequest;
import com.los.plp.dto.response.PlpApplicationCleanupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpApplicationCleanupService {

    private final PlpIntegrationClient plpIntegrationClient;

    public String cleanupForApplication(LoanApplication app) {
        if (!plpIntegrationClient.isEnabled()) {
            return "PLP integration disabled — skipped remote cleanup";
        }
        if (app.getPlpBorrowerId() == null && app.getSubProgramId() == null) {
            return "No PLP borrower or sub-program link — skipped remote cleanup";
        }
        try {
            PlpApplicationCleanupRequest request = PlpApplicationCleanupRequest.builder()
                    .losApplicationId(app.getId().toString())
                    .plpBorrowerId(app.getPlpBorrowerId())
                    .plpSubProgramBorrowerId(app.getPlpSubProgramBorrowerId())
                    .plpBorrowerProgramMappingId(app.getPlpBorrowerProgramMappingId())
                    .deleteBorrowerRecord(app.getPlpBorrowerId() != null)
                    .build();
            PlpApplicationCleanupResponse response = plpIntegrationClient.cleanupApplication(request);
            return response != null ? response.getSummary() : "PLP cleanup completed";
        } catch (PlpIntegrationException e) {
            log.error("PLP cleanup failed for application {}: {}", app.getApplicationNumber(), e.getMessage());
            throw new PlpIntegrationException("PLP cleanup failed: " + e.getMessage());
        }
    }
}
