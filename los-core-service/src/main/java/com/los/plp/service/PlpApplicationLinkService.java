package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.dto.LinkProgramRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlpApplicationLinkService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;

    @Transactional
    public LoanApplication linkProgram(UUID applicationId, LinkProgramRequest request) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        if (!StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            throw new IllegalArgumentException(
                    "Program link is only supported for " + StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING);
        }

        SubProgramMaster subProgram = subProgramMasterRepository.findById(request.getSubProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + request.getSubProgramId()));

        ProgramMaster program = programMasterRepository.findById(subProgram.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + subProgram.getProgramId()));

        AnchorMaster anchor = anchorMasterRepository.findById(subProgram.getAnchorId())
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + subProgram.getAnchorId()));

        if (anchor.getPlpAnchorSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            throw new IllegalStateException("Anchor is not synced to PLP");
        }
        if (program.getPlpProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            throw new IllegalStateException("Program is not synced to PLP");
        }
        if (subProgram.getPlpSubProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            throw new IllegalStateException("Sub-program is not synced to PLP");
        }

        app.setSubProgramId(subProgram.getId());
        return loanApplicationRepository.save(app);
    }
}
