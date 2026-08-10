package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.dto.VintageEligibilityResponse;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceDiscountingVintageService {

    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;

    @Transactional(readOnly = true)
    public Optional<VintageEligibilityResponse> evaluate(LoanApplication app) {
        if (!StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            return Optional.empty();
        }
        UUID subProgramId = app.getSubProgramId();
        if (subProgramId == null) {
            return Optional.empty();
        }
        SubProgramMaster sub = subProgramMasterRepository.findById(subProgramId).orElse(null);
        if (sub == null) {
            return Optional.empty();
        }
        ProgramMaster program = programMasterRepository.findById(sub.getProgramId()).orElse(null);
        if (program == null) {
            return Optional.empty();
        }

        Map<String, Object> bi = app.getBusinessInfo() != null ? app.getBusinessInfo() : Map.of();
        BigDecimal borrowerDep = toBigDecimal(bi.get("dependencyVintagePercent"));
        Integer borrowerAnchorMonths = toInteger(bi.get("anchorRelationshipVintageMonths"));

        BigDecimal programDep = program.getDependencyVintagePercent();
        Integer programAnchorMonths = program.getAnchorRelationshipVintageMonths();

        VintageFieldEval depEval = evaluateMinimum(borrowerDep, programDep, true);
        VintageFieldEval anchorEval = evaluateMinimum(
                borrowerAnchorMonths != null ? BigDecimal.valueOf(borrowerAnchorMonths) : null,
                programAnchorMonths != null ? BigDecimal.valueOf(programAnchorMonths) : null,
                false);

        return Optional.of(VintageEligibilityResponse.builder()
                .borrowerDependencyVintagePercent(borrowerDep)
                .borrowerAnchorRelationshipVintageMonths(borrowerAnchorMonths)
                .programDependencyVintagePercent(programDep)
                .programAnchorRelationshipVintageMonths(programAnchorMonths)
                .dependencyVintageStatus(depEval.status())
                .dependencyVintageMessage(depEval.message())
                .anchorVintageStatus(anchorEval.status())
                .anchorVintageMessage(anchorEval.message())
                .build());
    }

    private record VintageFieldEval(String status, String message) {}

    private static VintageFieldEval evaluateMinimum(BigDecimal borrower, BigDecimal programReq, boolean percentField) {
        if (programReq == null) {
            return new VintageFieldEval("NOT_CONFIGURED", percentField
                    ? "Program dependency vintage not configured"
                    : "Program anchor relationship vintage not configured");
        }
        if (borrower == null) {
            return new VintageFieldEval("LOWER", percentField
                    ? "Dependency vintage not provided by borrower"
                    : "Anchor relationship vintage not provided by borrower");
        }
        if (borrower.compareTo(programReq) >= 0) {
            return new VintageFieldEval("ELIGIBLE", percentField
                    ? "Dependency Vintage Eligible"
                    : "Anchor relationship vintage eligible");
        }
        return new VintageFieldEval("LOWER", percentField
                ? "Dependency vintage lower than requirement"
                : "Anchor relationship lower than requirement");
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
