package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.dto.PlpAnchorBorrowerResponse;
import com.los.plp.model.dto.PlpLinkedSubProgramSummaryResponse;
import com.los.plp.model.dto.PlpProgramDetailResponse;
import com.los.plp.model.dto.PlpProgramSummaryResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlpProgramQueryService {

    private final ProgramMasterRepository programMasterRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    @Transactional(readOnly = true)
    public List<PlpProgramSummaryResponse> listPrograms() {
        List<ProgramMaster> programs = programMasterRepository.findAll();
        List<PlpProgramSummaryResponse> result = new ArrayList<>();
        for (ProgramMaster program : programs) {
            List<SubProgramMaster> subPrograms = subProgramMasterRepository.findByProgramId(program.getId());
            if (subPrograms.isEmpty()) {
                result.add(toSummary(program, null, null, 0));
            } else {
                for (SubProgramMaster sp : subPrograms) {
                    AnchorMaster anchor = anchorMasterRepository.findById(sp.getAnchorId()).orElse(null);
                    long count = loanApplicationRepository.countBySubProgramId(sp.getId());
                    result.add(toSummary(program, sp, anchor, count));
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PlpLinkedSubProgramSummaryResponse getLinkedSubProgramSummary(UUID subProgramId) {
        SubProgramMaster sub = subProgramMasterRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + subProgramId));
        ProgramMaster program = programMasterRepository.findById(sub.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + sub.getProgramId()));
        AnchorMaster anchor = anchorMasterRepository.findById(sub.getAnchorId())
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + sub.getAnchorId()));
        return PlpLinkedSubProgramSummaryResponse.builder()
                .subProgramId(sub.getId())
                .programId(program.getId())
                .anchorId(anchor.getId())
                .subProgramName(sub.getName())
                .programName(program.getProgramName())
                .programType(program.getProductType())
                .anchorName(anchor.getName())
                .anchorCode(anchor.getCode())
                .programLimit(program.getProgramLimit())
                .programSyncStatus(program.getPlpProgramSyncStatus())
                .subProgramSyncStatus(sub.getPlpSubProgramSyncStatus())
                .plpProgramId(program.getPlpProgramId())
                .plpSubProgramId(sub.getPlpSubProgramId())
                .build();
    }

    public PlpProgramDetailResponse getProgramDetail(UUID programId) {
        ProgramMaster program = programMasterRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        List<SubProgramMaster> subPrograms = subProgramMasterRepository.findByProgramId(programId);

        List<PlpProgramDetailResponse.SubProgramView> subViews = new ArrayList<>();
        List<PlpProgramDetailResponse.PlpBorrowerApplicationView> allBorrowers = new ArrayList<>();

        for (SubProgramMaster sp : subPrograms) {
            AnchorMaster anchor = anchorMasterRepository.findById(sp.getAnchorId()).orElse(null);
            long count = loanApplicationRepository.countBySubProgramId(sp.getId());
            subViews.add(PlpProgramDetailResponse.SubProgramView.builder()
                    .subProgramId(sp.getId())
                    .subProgramCode(sp.getSubProgramCode())
                    .name(sp.getName())
                    .anchorId(sp.getAnchorId())
                    .anchorName(anchor != null ? anchor.getName() : null)
                    .anchorSyncStatus(anchor != null ? anchor.getPlpAnchorSyncStatus() : null)
                    .subProgramSyncStatus(sp.getPlpSubProgramSyncStatus())
                    .subProgramSyncError(sp.getPlpSubProgramSyncError())
                    .subProgramSyncedAt(sp.getPlpSubProgramSyncedAt())
                    .plpSubProgramId(sp.getPlpSubProgramId())
                    .borrowerCount(count)
                    .build());

            for (LoanApplication app : loanApplicationRepository.findBySubProgramId(sp.getId())) {
                allBorrowers.add(toBorrowerView(app, sp));
            }
        }

        return PlpProgramDetailResponse.builder()
                .programId(program.getId())
                .programCode(program.getProgramCode())
                .programName(program.getProgramName())
                .programType(program.getProductType())
                .creditLimit(program.getProgramLimit())
                .maxBorrowerLimit(program.getMaxBorrowerLimit())
                .interestRate(program.getInterestRate())
                .tenureDays(program.getTenureDays())
                .currency(program.getCurrency())
                .validityStartDate(program.getValidityStartDate())
                .validityEndDate(program.getValidityEndDate())
                .programSyncStatus(program.getPlpProgramSyncStatus())
                .programSyncError(program.getPlpProgramSyncError())
                .programSyncedAt(program.getPlpProgramSyncedAt())
                .plpProgramId(program.getPlpProgramId())
                .subPrograms(subViews)
                .borrowers(allBorrowers)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlpProgramSummaryResponse> listProgramsForAnchor(UUID anchorId, boolean syncedOnly) {
        List<SubProgramMaster> subPrograms = syncedOnly
                ? subProgramMasterRepository.findByAnchorIdAndPlpSubProgramSyncStatus(anchorId, PlpSyncStatus.SYNC_SUCCESS)
                : subProgramMasterRepository.findByAnchorId(anchorId);
        AnchorMaster anchor = anchorMasterRepository.findById(anchorId)
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + anchorId));

        if (syncedOnly && anchor.getPlpAnchorSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            return List.of();
        }

        Map<UUID, ProgramMaster> programCache = programMasterRepository.findAll().stream()
                .collect(Collectors.toMap(ProgramMaster::getId, p -> p));

        List<PlpProgramSummaryResponse> result = new ArrayList<>();
        for (SubProgramMaster sp : subPrograms) {
            ProgramMaster program = programCache.get(sp.getProgramId());
            if (program == null) {
                continue;
            }
            if (syncedOnly && program.getPlpProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
                continue;
            }
            long count = loanApplicationRepository.countBySubProgramId(sp.getId());
            result.add(toSummary(program, sp, anchor, count));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PlpAnchorBorrowerResponse> listBorrowersForAnchor(UUID anchorId) {
        anchorMasterRepository.findById(anchorId)
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + anchorId));
        List<SubProgramMaster> subPrograms = subProgramMasterRepository.findByAnchorId(anchorId);
        if (subPrograms.isEmpty()) {
            return List.of();
        }
        List<UUID> subProgramIds = subPrograms.stream().map(SubProgramMaster::getId).toList();
        Map<UUID, SubProgramMaster> spById = subPrograms.stream().collect(Collectors.toMap(SubProgramMaster::getId, s -> s));
        Map<UUID, ProgramMaster> programCache = programMasterRepository.findAll().stream()
                .collect(Collectors.toMap(ProgramMaster::getId, p -> p));

        return loanApplicationRepository.findBySubProgramIdIn(subProgramIds).stream()
                .filter(app -> StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct()))
                .map(app -> {
                    SubProgramMaster sp = spById.get(app.getSubProgramId());
                    ProgramMaster program = sp != null ? programCache.get(sp.getProgramId()) : null;
                    return PlpAnchorBorrowerResponse.builder()
                            .applicationId(app.getId())
                            .applicationNumber(app.getApplicationNumber())
                            .borrowerName(extractBorrowerName(app))
                            .status(app.getStatus() != null ? app.getStatus().name() : null)
                            .subProgramId(app.getSubProgramId())
                            .programName(program != null ? program.getProgramName() : null)
                            .borrowerSyncStatus(app.getPlpBorrowerSyncStatus())
                            .linkSyncStatus(app.getPlpLinkSyncStatus())
                            .mappingSyncStatus(app.getPlpMappingSyncStatus())
                            .overallSyncStatus(app.getPlpProgramSyncStatus())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlpProgramSummaryResponse> listSelectablePrograms() {
        return anchorMasterRepository.findByPlpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS).stream()
                .flatMap(anchor -> listProgramsForAnchor(anchor.getId(), true).stream())
                .toList();
    }

    private static PlpProgramSummaryResponse toSummary(
            ProgramMaster program,
            SubProgramMaster sp,
            AnchorMaster anchor,
            long borrowerCount) {
        return PlpProgramSummaryResponse.builder()
                .programId(program.getId())
                .subProgramId(sp != null ? sp.getId() : null)
                .programName(program.getProgramName())
                .programCode(program.getProgramCode())
                .programType(program.getProductType())
                .creditLimit(program.getProgramLimit())
                .interestRate(program.getInterestRate())
                .tenureDays(program.getTenureDays())
                .currency(program.getCurrency())
                .validityStartDate(program.getValidityStartDate())
                .validityEndDate(program.getValidityEndDate())
                .anchorId(anchor != null ? anchor.getId() : (sp != null ? sp.getAnchorId() : null))
                .anchorName(anchor != null ? anchor.getName() : null)
                .anchorCode(anchor != null ? anchor.getCode() : null)
                .programSyncStatus(program.getPlpProgramSyncStatus())
                .subProgramSyncStatus(sp != null ? sp.getPlpSubProgramSyncStatus() : null)
                .borrowerCount(borrowerCount)
                .flowType(sp != null ? sp.getFlowType() : null)
                .lmsEntryIn(program.getLmsEntryIn())
                .encoreProductCode(program.getEncoreProductCode())
                .build();
    }

    private static PlpProgramDetailResponse.PlpBorrowerApplicationView toBorrowerView(
            LoanApplication app,
            SubProgramMaster sp) {
        return PlpProgramDetailResponse.PlpBorrowerApplicationView.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .borrowerName(extractBorrowerName(app))
                .status(app.getStatus() != null ? app.getStatus().name() : null)
                .subProgramId(sp.getId())
                .borrowerSyncStatus(app.getPlpBorrowerSyncStatus())
                .linkSyncStatus(app.getPlpLinkSyncStatus())
                .mappingSyncStatus(app.getPlpMappingSyncStatus())
                .overallSyncStatus(app.getPlpProgramSyncStatus())
                .build();
    }

    private static String extractBorrowerName(LoanApplication app) {
        if (app.getBusinessInfo() != null && app.getBusinessInfo().containsKey("legalName")) {
            return String.valueOf(app.getBusinessInfo().get("legalName"));
        }
        if (app.getPersonalInfo() != null && app.getPersonalInfo().containsKey("fullName")) {
            return String.valueOf(app.getPersonalInfo().get("fullName"));
        }
        return app.getApplicationNumber();
    }
}
