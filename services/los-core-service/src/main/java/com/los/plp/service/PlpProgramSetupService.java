package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.model.dto.CreatePlpProgramRequest;
import com.los.plp.model.dto.PlpProgramSetupResponse;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpProgramSetupService {

    private final AnchorMasterRepository anchorMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final PlpProgramSyncService plpProgramSyncService;
    private final PlpSubProgramSyncService plpSubProgramSyncService;
    private final PlpProperties plpProperties;

    @Transactional
    public PlpProgramSetupResponse createProgramForAnchor(CreatePlpProgramRequest request) {
        AnchorMaster anchor = anchorMasterRepository.findById(request.getAnchorId())
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + request.getAnchorId()));

        // Idempotency: same anchor + program name implies retry of an earlier submission. Reuse the existing
        // program/sub-program records (and retry only the sync legs that haven't succeeded yet) so a duplicate
        // submission cannot violate the program_masters.program_code unique constraint.
        List<SubProgramMaster> existingSubs = subProgramMasterRepository
                .findByAnchorIdAndProgramName(anchor.getId(), request.getProgramName());
        if (!existingSubs.isEmpty()) {
            return reuseExistingProgram(existingSubs.get(0), anchor);
        }

        String programCode = "PRG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subProgramCode = request.getSubProgramCode() != null && !request.getSubProgramCode().isBlank()
                ? request.getSubProgramCode()
                : programCode + "-SP";

        String lmsEntry = normalizeLmsEntry(request.getLmsEntryIn());
        if ("YES".equals(lmsEntry)
                && "INVOICE_DISCOUNTING".equalsIgnoreCase(request.getProgramType())
                && (request.getEncoreProductCode() == null || request.getEncoreProductCode().isBlank())) {
            throw new IllegalArgumentException("encoreProductCode is required when LMS entry is YES for invoice discounting");
        }
        ProgramMaster program = ProgramMaster.builder()
                .programCode(programCode)
                .programName(request.getProgramName())
                .productType(request.getProgramType())
                .programLimit(request.getCreditLimit())
                .maxBorrowerLimit(request.getCreditLimit())
                .interestRate(request.getInterestRate())
                .tenureDays(request.getTenureDays())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .validityStartDate(request.getValidityStartDate())
                .validityEndDate(request.getValidityEndDate())
                .lmsEntryIn(lmsEntry)
                .encoreProductCode("YES".equals(lmsEntry) ? trimToNull(request.getEncoreProductCode()) : null)
                .plpLenderId(parseLenderId())
                .plpProgramSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
        program = programMasterRepository.save(program);

        String subName = request.getSubProgramName() != null && !request.getSubProgramName().isBlank()
                ? request.getSubProgramName()
                : request.getProgramName() + " — " + anchor.getName();

        // PLP's LosSubProgramUpsertRequest requires non-blank flowType + anchorRole + borrowerRole, and only
        // accepts a fixed set of (flow_type, anchor_role, borrower_role) tuples per product type. Hardcoding
        // flowType to the product type previously caused HTTP 400 ("Validation failed", count: 2) because the
        // values were either invalid (INVOICE_DISCOUNTING is not a flow type on PLP) or missing.
        SubProgramRoles roles = resolveSubProgramRoles(request.getProgramType(), request.getFlowType());

        SubProgramMaster subProgram = SubProgramMaster.builder()
                .subProgramCode(subProgramCode)
                .name(subName)
                .programId(program.getId())
                .anchorId(anchor.getId())
                .flowType(roles.flowType())
                .anchorRole(roles.anchorRole())
                .borrowerRole(roles.borrowerRole())
                .subProgramLimit(request.getSubProgramLimit() != null ? request.getSubProgramLimit() : request.getCreditLimit())
                .plpSubProgramSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
        subProgram = subProgramMasterRepository.save(subProgram);

        if (plpProperties.isEnabled()) {
            try {
                program = plpProgramSyncService.sync(program);
            } catch (Exception e) {
                log.error("PLP program sync failed after create for {}: {}", program.getId(), e.getMessage(), e);
            }
            try {
                // Pass already-loaded program + anchor entities so the sync joins this @Transactional and
                // sees the still-uncommitted parent rows; a REQUIRES_NEW would not be able to read or FK-link
                // to the program until the outer transaction commits.
                subProgram = plpSubProgramSyncService.sync(subProgram, program, anchor);
            } catch (Exception e) {
                log.error("PLP sub-program sync failed after create for {}: {}", subProgram.getId(), e.getMessage(), e);
            }
            program = programMasterRepository.findById(program.getId()).orElse(program);
            subProgram = subProgramMasterRepository.findById(subProgram.getId()).orElse(subProgram);
        }

        return buildResponse(program, subProgram, anchor);
    }

    private PlpProgramSetupResponse reuseExistingProgram(SubProgramMaster existingSub, AnchorMaster anchor) {
        final UUID existingProgramId = existingSub.getProgramId();
        ProgramMaster existingProgram = programMasterRepository.findById(existingProgramId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inconsistent state: sub-program references missing program " + existingProgramId));

        if (plpProperties.isEnabled()) {
            if (existingProgram.getPlpProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
                try {
                    existingProgram = plpProgramSyncService.sync(existingProgram);
                } catch (Exception e) {
                    log.error("PLP program retry-sync failed for {}: {}",
                            existingProgram.getId(), e.getMessage(), e);
                }
            }
            if (existingSub.getPlpSubProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
                try {
                    existingSub = plpSubProgramSyncService.sync(existingSub, existingProgram, anchor);
                } catch (Exception e) {
                    log.error("PLP sub-program retry-sync failed for {}: {}",
                            existingSub.getId(), e.getMessage(), e);
                }
            }
            existingProgram = programMasterRepository.findById(existingProgram.getId()).orElse(existingProgram);
            existingSub = subProgramMasterRepository.findById(existingSub.getId()).orElse(existingSub);
        }

        return buildResponse(existingProgram, existingSub, anchor);
    }

    private PlpProgramSetupResponse buildResponse(ProgramMaster program, SubProgramMaster subProgram, AnchorMaster anchor) {
        return PlpProgramSetupResponse.builder()
                .programId(program.getId())
                .subProgramId(subProgram.getId())
                .anchorId(anchor.getId())
                .programName(program.getProgramName())
                .programType(program.getProductType())
                .creditLimit(program.getProgramLimit())
                .interestRate(program.getInterestRate())
                .tenureDays(program.getTenureDays())
                .currency(program.getCurrency())
                .validityStartDate(program.getValidityStartDate())
                .validityEndDate(program.getValidityEndDate())
                .programSyncStatus(program.getPlpProgramSyncStatus())
                .programSyncError(program.getPlpProgramSyncError())
                .subProgramSyncStatus(subProgram.getPlpSubProgramSyncStatus())
                .subProgramSyncError(subProgram.getPlpSubProgramSyncError())
                .plpProgramId(program.getPlpProgramId())
                .plpSubProgramId(subProgram.getPlpSubProgramId())
                .programSyncedAt(program.getPlpProgramSyncedAt())
                .subProgramSyncedAt(subProgram.getPlpSubProgramSyncedAt())
                .build();
    }

    private UUID parseLenderId() {
        String raw = plpProperties.getLenderId();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("los.plp.lender-id (PLP_LENDER_ID) is required for program setup");
        }
        return UUID.fromString(raw.trim());
    }

    /**
     * PLP defaults for the (flow_type, anchor_role, borrower_role) triple. PLP rejects sub-program upserts
     * whose tuple is not one of the values listed in {@code SubProgramService.validateSubProgramForProgram}
     * on the PLP side, so we must populate values that match a supported configuration. Today the LOS UI does
     * not expose a flow-type selector, so we apply the default for each product:
     * <ul>
     *   <li>{@code INVOICE_DISCOUNTING} → PURCHASE_BILL_DISCOUNTING (anchor=SELLER, borrower=BUYER) — matches
     *       PLP's own fallback when an invoice's flow_type is blank.</li>
     *   <li>{@code PAY_DAY_LOAN} → PAY_LOAN (anchor=EMPLOYER, borrower=EMPLOYEE).</li>
     * </ul>
     */
    private static SubProgramRoles resolveSubProgramRoles(String programType, String flowType) {
        if (programType == null) {
            throw new IllegalArgumentException("programType is required for PLP sub-program setup");
        }
        switch (programType.trim().toUpperCase()) {
            case "INVOICE_DISCOUNTING":
                String flow = flowType != null && !flowType.isBlank()
                        ? flowType.trim().toUpperCase()
                        : "PURCHASE_BILL_DISCOUNTING";
                if ("SALES_BILL_DISCOUNTING".equals(flow)) {
                    return new SubProgramRoles(flow, "BUYER", "SELLER");
                }
                if ("PURCHASE_ORDER_DISCOUNTING".equals(flow)) {
                    return new SubProgramRoles(flow, "BUYER", "SELLER");
                }
                if ("PURCHASE_BILL_DISCOUNTING".equals(flow)) {
                    return new SubProgramRoles(flow, "SELLER", "BUYER");
                }
                throw new IllegalArgumentException(
                        "Unsupported flowType for invoice discounting: " + flowType
                                + ". Use PURCHASE_BILL_DISCOUNTING, SALES_BILL_DISCOUNTING, or "
                                + "PURCHASE_ORDER_DISCOUNTING");
            case "PAY_DAY_LOAN":
                return new SubProgramRoles("PAY_LOAN", "EMPLOYER", "EMPLOYEE");
            default:
                throw new IllegalArgumentException(
                        "Unsupported programType for PLP sub-program setup: " + programType
                                + ". Supported values: INVOICE_DISCOUNTING, PAY_DAY_LOAN");
        }
    }

    private static String normalizeLmsEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NO";
        }
        String v = raw.trim().toUpperCase();
        if ("YES".equals(v) || "NO".equals(v)) {
            return v;
        }
        throw new IllegalArgumentException("lmsEntryIn must be YES or NO");
    }

    private static String trimToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private record SubProgramRoles(String flowType, String anchorRole, String borrowerRole) {
    }
}
