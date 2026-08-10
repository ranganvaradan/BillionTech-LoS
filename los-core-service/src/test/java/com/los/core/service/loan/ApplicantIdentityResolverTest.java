package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicantIdentityResolverTest {

    @Test
    void resolvePanFromPersonalInfoForBorrower() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("panNumber", "abcde1234f"))
                .build();
        assertEquals("ABCDE1234F", ApplicantIdentityResolver.resolvePanNumber(app));
    }

    @Test
    void resolvePanFromEntityPanForAnchor() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "aaacb1234d"))
                .build();
        assertEquals("AAACB1234D", ApplicantIdentityResolver.resolvePanNumber(app));
    }

    @Test
    void buildBureauBorrowerInfoIncludesCanonicalPanForAnchor() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "AAACB1234D", "corporateName", "Acme Corp"))
                .build();
        Map<String, Object> info = ApplicantIdentityResolver.buildBureauBorrowerInfo(app);
        assertEquals("AAACB1234D", info.get("panNumber"));
        assertEquals("Acme Corp", info.get("corporateName"));
    }

    @Test
    void enrichKycPayloadPrefersRequestPanWhenProvided() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("entityPan", "AAACB1234D"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of("panNumber", "BBBBB9999B"));
        assertEquals("BBBBB9999B", merged.get("panNumber"));
    }

    @Test
    void enrichKycPayloadMapsMobileAliasesToMobileNumber() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("mobile", "+91 98765 43210", "fullName", "Test User"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("9876543210", merged.get("mobileNumber"));
        assertEquals("9876543210", merged.get("mobile"));
    }

    @Test
    void normalizeIndianMobileDigitsStripsCountryCodeAndSpaces() {
        assertEquals("9876543210", ApplicantIdentityResolver.normalizeIndianMobileDigits("+91-98765-43210"));
        assertEquals("9876543210", ApplicantIdentityResolver.normalizeIndianMobileDigits("09876543210"));
        assertEquals("", ApplicantIdentityResolver.normalizeIndianMobileDigits(""));
        assertEquals("", ApplicantIdentityResolver.normalizeIndianMobileDigits(null));
    }
}
