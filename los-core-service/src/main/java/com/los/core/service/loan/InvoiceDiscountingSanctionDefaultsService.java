package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Guard the program's Max. dealer (per-borrower) limit at application submission — before sanction and
     * the PLP borrower link. When the application is linked to a PLP sub-program, the requested amount must
     * not exceed the parent program's {@code maxBorrowerLimit}; otherwise the PLP link would later fail while
     * the LOS sanction had already completed. No-op when no sub-program is linked yet or the limit is unset.
     */
    public void validateRequestedAmountWithinProgramLimit(LoanApplication app) {
        UUID subProgramId = app.getSubProgramId();
        if (subProgramId == null) {
            return;
        }
        BigDecimal requested = app.getRequestedAmount();
        if (requested == null) {
            return;
        }
        subProgramMasterRepository.findById(subProgramId)
                .map(SubProgramMaster::getProgramId)
                .flatMap(programMasterRepository::findById)
                .ifPresent(program -> {
                    BigDecimal max = program.getMaxBorrowerLimit();
                    if (max != null && requested.compareTo(max) > 0) {
                        throw new BusinessRuleException(
                                "Requested amount (₹" + requested.toPlainString()
                                        + ") exceeds the program's Max. dealer limit (₹" + max.toPlainString()
                                        + "). Reduce the requested amount to continue.",
                                "REQUESTED_AMOUNT_EXCEEDS_PROGRAM_LIMIT",
                                "SUBMIT_APPLICATION",
                                Map.of(
                                        "requestedAmount", requested.toPlainString(),
                                        "maxBorrowerLimit", max.toPlainString(),
                                        "programCode",
                                        program.getProgramCode() != null ? program.getProgramCode() : ""));
                    }
                });
    }

    public Optional<ProgramMaster> resolveAnchorProgram(LoanApplication app) {
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
