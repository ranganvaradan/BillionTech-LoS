package com.los.plp.mapper;

import com.los.core.model.entity.LoanApplication;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.plp.dto.request.PlpBorrowerSyncRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;

public final class PlpBorrowerPayloadMapper {

    private PlpBorrowerPayloadMapper() {
    }

    private static String resolveGstin(LoanApplication app) {
        if (app == null || app.getBusinessInfo() == null) {
            return null;
        }
        Object v = app.getBusinessInfo().get("gstin");
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim().toUpperCase();
    }

    public static PlpBorrowerSyncRequest toRequest(
            LoanApplication app,
            ProgramMaster program,
            AnchorMaster anchor) {
        return PlpBorrowerSyncRequest.builder()
                .losBorrowerId(app.getCustomerId().toString())
                .plpProgramId(program.getPlpProgramId().toString())
                .plpAnchorId(anchor.getPlpAnchorId().toString())
                .borrower(PlpBorrowerSyncRequest.BorrowerPayload.builder()
                        .name(ApplicationPartyResolver.resolveDisplayName(app))
                        .email(ApplicationPartyResolver.resolveEmail(app))
                        .phone(ApplicationPartyResolver.resolveMobile(app))
                        .pan(ApplicantIdentityResolver.resolvePanNumber(app))
                        .gstin(resolveGstin(app))
                        .build())
                .build();
    }
}
