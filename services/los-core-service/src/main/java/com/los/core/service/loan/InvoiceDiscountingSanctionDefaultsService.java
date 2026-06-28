package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves sanction defaults for invoice discounting flows — PLP program for anchor,
 * CAM terms for borrower.
 */
@Service
@RequiredArgsConstructor
public class InvoiceDiscountingSanctionDefaultsService {

    private final AnchorMasterRepository anchorMasterRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;

    public void applyAnchorProgramDefaults(LoanApplication app) {
        if (!InvoiceDiscountingApplicationRules.isAnchorFlow(app)) {
            return;
        }
        resolveAnchorProgram(app).ifPresent(program -> {
            if (app.getSanctionedAmount() == null && program.getProgramLimit() != null) {
                app.setSanctionedAmount(program.getProgramLimit());
            }
            if (app.getTenureMonths() == null && program.getTenureDays() != null) {
                app.setTenureMonths(tenureDaysToMonths(program.getTenureDays()));
            }
            if (app.getApprovedRate() == null && program.getInterestRate() != null) {
                app.setApprovedRate(program.getInterestRate());
            }
            if (app.getInterestRate() == null && program.getInterestRate() != null) {
                app.setInterestRate(program.getInterestRate());
            }
        });
    }

    public int defaultAnchorTenureMonths(LoanApplication app) {
        return resolveAnchorProgram(app)
                .map(ProgramMaster::getTenureDays)
                .map(InvoiceDiscountingSanctionDefaultsService::tenureDaysToMonths)
                .orElse(12);
    }

    Optional<ProgramMaster> resolveAnchorProgram(LoanApplication app) {
        return anchorMasterRepository.findBySourceAnchorApplicationId(app.getId())
                .flatMap(anchor -> subProgramMasterRepository.findByAnchorId(anchor.getId()).stream()
                        .sorted(Comparator.comparing(
                                SubProgramMaster::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .findFirst()
                        .flatMap(sp -> programMasterRepository.findById(sp.getProgramId())));
    }

    static int tenureDaysToMonths(int tenureDays) {
        return Math.max(1, (tenureDays + 29) / 30);
    }
}
