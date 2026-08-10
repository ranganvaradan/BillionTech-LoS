package com.los.lms.service;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LmsProgramResolver {

    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;

    public Optional<ProgramMaster> resolveForApplication(LoanApplication app) {
        if (app == null || app.getSubProgramId() == null) {
            return Optional.empty();
        }
        return subProgramMasterRepository.findById(app.getSubProgramId())
                .map(SubProgramMaster::getProgramId)
                .flatMap(programMasterRepository::findById);
    }

    public Optional<ProgramMaster> resolveBySubProgramId(UUID subProgramId) {
        if (subProgramId == null) {
            return Optional.empty();
        }
        return subProgramMasterRepository.findById(subProgramId)
                .map(SubProgramMaster::getProgramId)
                .flatMap(programMasterRepository::findById);
    }

    public Optional<ProgramMaster> resolveByApplicationNumber(String applicationNumber) {
        if (applicationNumber == null || applicationNumber.isBlank()) {
            return Optional.empty();
        }
        return loanApplicationRepository.findByApplicationNumber(applicationNumber)
                .flatMap(this::resolveForApplication);
    }

    public boolean isLmsEntryEnabled(ProgramMaster program) {
        return program != null && "YES".equalsIgnoreCase(program.getLmsEntryIn());
    }

    public String resolveEncoreProductCode(LoanApplication app, String loanProduct) {
        return lmsApplicationConfigResolver.resolveEncoreProductCode(app);
    }
}
