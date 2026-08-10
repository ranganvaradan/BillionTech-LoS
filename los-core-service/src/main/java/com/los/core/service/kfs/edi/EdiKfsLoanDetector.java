package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.LoanApplication;
import com.los.lms.service.LmsApplicationConfigResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EdiKfsLoanDetector {

    private final EdiKfsProperties ediKfsProperties;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;

    public boolean isEdiLoan(LoanApplication app) {
        if (app == null || !ediKfsProperties.isEnabled()) {
            return false;
        }
        return "day".equalsIgnoreCase(lmsApplicationConfigResolver.resolveTenureUnit(app));
    }
}
