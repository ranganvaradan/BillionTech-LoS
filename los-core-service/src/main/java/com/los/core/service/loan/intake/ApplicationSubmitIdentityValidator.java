package com.los.core.service.loan.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.core.service.loan.ApplicationPartyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks submission when the application's email / mobile / PAN / GSTN already belongs to a <em>different</em>
 * borrower (different {@code customerId}). Because the customer is resolved from email/mobile before
 * this runs, the same borrower re-applying shares a {@code customerId} and is allowed; a reused PAN or
 * GSTN tied to a different identity is rejected.
 *
 * <p>Read-only and side-effect free; it only reads other applications and throws on conflict.</p>
 */
@Component
@RequiredArgsConstructor
public class ApplicationSubmitIdentityValidator {

    static final UUID NO_APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final LoanApplicationRepository applicationRepository;

    public void validateNoDuplicateIdentity(LoanApplication app) {
        validateFields(
                app.getId(),
                app.getCustomerId(),
                ApplicationPartyResolver.resolveEmail(app),
                ApplicationPartyResolver.resolveMobile(app),
                ApplicantIdentityResolver.resolvePanNumber(app),
                resolveGstin(app));
    }

    /**
     * Check provisional identity fields during intake (before final submit).
     * Blank fields are skipped.
     */
    public void validateFields(
            UUID selfId,
            UUID customerId,
            String email,
            String mobile,
            String pan,
            String gstin) {
        UUID excludeId = selfId != null ? selfId : NO_APPLICATION_ID;

        if (email != null && !email.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByEmail(excludeId, email.trim()), customerId,
                    "email", email.trim(),
                    "This email is already used by another borrower's application.");
        }

        String mobileDigits = normalizeMobileDigits(mobile);
        if (!mobileDigits.isBlank() && mobileDigits.length() >= 10) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByMobile(excludeId, mobileDigits), customerId,
                    "mobile", mobile,
                    "This mobile number is already used by another borrower's application.");
        }

        if (pan != null && !pan.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByPan(excludeId, pan.trim()), customerId,
                    "panNumber", pan.trim(),
                    "This PAN is already used by another borrower's application.");
        }

        if (gstin != null && !gstin.isBlank()) {
            blockIfDifferentCustomer(
                    applicationRepository.findOthersByGstin(excludeId, gstin.trim()), customerId,
                    "gstin", gstin.trim(),
                    "This GSTIN is already used by another borrower's application.");
        }
    }

    private void blockIfDifferentCustomer(
            List<LoanApplication> others, UUID customerId, String field, String value, String message) {
        for (LoanApplication other : others) {
            if (customerId == null || !customerId.equals(other.getCustomerId())) {
                throw new BusinessRuleException(
                        message,
                        "DUPLICATE_" + field.toUpperCase(),
                        "VALIDATE_IDENTITY",
                        Map.of("field", field, "value", value,
                                "conflictingApplicationNumber",
                                other.getApplicationNumber() != null ? other.getApplicationNumber() : ""));
            }
        }
    }

    private static String normalizeMobileDigits(String mobile) {
        if (mobile == null) {
            return "";
        }
        return mobile.replaceAll("\\D", "");
    }

    private static String resolveGstin(LoanApplication app) {
        String fromPersonal = stringValue(app.getPersonalInfo());
        if (!fromPersonal.isBlank()) {
            return fromPersonal;
        }
        return stringValueBusiness(app);
    }

    private static String stringValue(Map<String, Object> personalInfo) {
        if (personalInfo == null) {
            return "";
        }
        Object v = personalInfo.get("gstin");
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }

    private static String stringValueBusiness(LoanApplication app) {
        Map<String, Object> bi = app.getBusinessInfo();
        if (bi == null) {
            return "";
        }
        Object v = bi.get("gstin");
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }
}
