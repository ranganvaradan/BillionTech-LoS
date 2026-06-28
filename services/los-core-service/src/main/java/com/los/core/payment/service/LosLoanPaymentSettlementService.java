package com.los.core.payment.service;

import com.los.core.model.dto.response.LosLoanPaymentInProgressResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.payment.model.LosLoanPaymentInProgress;
import com.los.core.payment.model.LosPgSettlementBatch;
import com.los.core.payment.repository.LosLoanPaymentInProgressRepository;
import com.los.core.payment.repository.LosPgSettlementBatchRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.lms.service.LmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LosLoanPaymentSettlementService {

    private final LosLoanPaymentInProgressRepository pipRepository;
    private final LosPgSettlementBatchRepository batchRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LmsService lmsService;

    @Transactional(readOnly = true)
    public List<LosLoanPaymentInProgressResponse> listOpenPip() {
        return pipRepository.findByPipStatusOrderByCreatedAtDesc(LosLoanPayuPaymentService.PIP_OPEN).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LosPgSettlementBatch createAndApplyBatch(
            LocalDate settlementDate,
            String settlementUtr,
            List<UUID> pipIds,
            String createdBy,
            String remarks) {
        LosPgSettlementBatch batch = LosPgSettlementBatch.builder()
                .settlementDate(settlementDate)
                .settlementUtr(settlementUtr)
                .settlementMode("PG")
                .status("PENDING")
                .createdBy(createdBy)
                .remarks(remarks)
                .build();
        batch = batchRepository.save(batch);
        for (UUID pipId : pipIds) {
            LosLoanPaymentInProgress pip = pipRepository.findById(pipId)
                    .orElseThrow(() -> new IllegalArgumentException("PIP not found: " + pipId));
            if (!LosLoanPayuPaymentService.PIP_OPEN.equals(pip.getPipStatus())) {
                throw new IllegalArgumentException("PIP already settled: " + pipId);
            }
            LoanApplication app = applicationRepository.findById(pip.getApplicationId())
                    .orElseThrow(() -> new IllegalArgumentException("Application not found for PIP: " + pipId));
            lmsService.recordPgSettlementRepayment(
                    app.getApplicationNumber(), pip.getPrincipalAmount(), settlementUtr);
            pip.setPipStatus("SETTLED");
            pip.setSettlementBatchId(batch.getId());
            pip.setSettledAt(Instant.now());
            pipRepository.save(pip);
        }
        batch.setStatus("APPLIED");
        batch.setAppliedAt(Instant.now());
        return batchRepository.save(batch);
    }

    private LosLoanPaymentInProgressResponse toResponse(LosLoanPaymentInProgress pip) {
        return LosLoanPaymentInProgressResponse.builder()
                .id(pip.getId())
                .applicationId(pip.getApplicationId())
                .applicationNumber(pip.getApplicationNumber())
                .loanProduct(pip.getLoanProduct())
                .borrowerUserId(pip.getBorrowerUserId())
                .principalAmount(pip.getPrincipalAmount())
                .pipStatus(pip.getPipStatus())
                .createdAt(pip.getCreatedAt())
                .build();
    }
}
