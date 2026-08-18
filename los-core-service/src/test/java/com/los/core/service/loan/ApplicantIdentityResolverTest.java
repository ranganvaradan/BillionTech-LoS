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

    @Test
    void resolvePanFromPanAlias() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("pan", "abcde1234f"))
                .build();
        assertEquals("ABCDE1234F", ApplicantIdentityResolver.resolvePanNumber(app));
    }

    @Test
    void canonicalisePersonalInfoWritesPanNumber() {
        Map<String, Object> out = ApplicantIdentityResolver.canonicalisePersonalInfo(Map.of("pan", "abcde1234f"));
        assertEquals("ABCDE1234F", out.get("panNumber"));
        assertEquals("abcde1234f", out.get("pan"));
    }

    @Test
    void enrichMapsAadhaarAliasToAadhaarNumber() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("aadhaar", "123412341234"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("123412341234", merged.get("aadhaarNumber"));
    }

    @Test
    void enrichDoesNotPromoteAadhaarLast4ToAadhaarNumber() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("aadhaarLast4", "1234"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals(null, merged.get("aadhaarNumber"));
        assertEquals(false, ApplicantIdentityResolver.hasCanonicalExecutionValue("AADHAAR_OTP", merged));
    }

    @Test
    void enrichMapsIfscCodeAndBankAccountAliases() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("bankAccountNumber", "501000123456", "ifscCode", "hdfc0000001"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("501000123456", merged.get("accountNumber"));
        assertEquals("HDFC0000001", merged.get("ifsc"));
        assertEquals(true, ApplicantIdentityResolver.hasCanonicalExecutionValue("BANK_PENNY_DROP", merged));
    }

    @Test
    void enrichMapsFinancialInfoBankFields() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("fullName", "Test User"))
                .financialInfo(Map.of("accountNumber", "111122223333", "ifsc", "SBIN0001234"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("111122223333", merged.get("accountNumber"));
        assertEquals("SBIN0001234", merged.get("ifsc"));
        assertEquals("Test User", merged.get("name"));
    }

    @Test
    void enrichMapsUdyamCinAndGstinAliases() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .businessInfo(Map.of(
                        "udyamNumber", "udyam-aa-01-0000001",
                        "CIN_MCA21", "u12345mh2010ptc123456",
                        "gstin", "27aaaaa0000a1z5"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(app, Map.of());
        assertEquals("UDYAM-AA-01-0000001", merged.get("udyam"));
        assertEquals("UDYAM-AA-01-0000001", merged.get("udyamRegistrationNo"));
        assertEquals("U12345MH2010PTC123456", merged.get("cin"));
        assertEquals("27AAAAA0000A1Z5", merged.get("gstin"));
    }

    @Test
    void requestPayloadOverridesStoredAadhaar() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("aadhaar", "111111111111"))
                .build();
        Map<String, Object> merged = ApplicantIdentityResolver.enrichKycPayload(
                app, Map.of("aadhaarNumber", "123412341234"));
        assertEquals("123412341234", merged.get("aadhaarNumber"));
    }

    @Test
    void resolveIntakeFieldBankAccountUsesCanonicalAccount() {
        String v = ApplicantIdentityResolver.resolveIntakeField(
                "bankAccountNumber",
                Map.of("accountNumber", "501000123456"),
                Map.of(),
                null);
        assertEquals("501000123456", v);
    }

    @Test
    void canonicalisePersonalInfoWritesAadhaarAndIfsc() {
        Map<String, Object> out = ApplicantIdentityResolver.canonicalisePersonalInfo(Map.of(
                "aadhaar", "123412341234",
                "ifscCode", "hdfc0000001",
                "pan", "abcde1234f"));
        assertEquals("123412341234", out.get("aadhaarNumber"));
        assertEquals("HDFC0000001", out.get("ifsc"));
        assertEquals("ABCDE1234F", out.get("panNumber"));
    }

    @Test
    void bureauBorrowerInfoIsCanonical() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("pan", "abcde1234f", "fullName", "Test User"))
                .build();
        Map<String, Object> info = ApplicantIdentityResolver.buildBureauBorrowerInfo(app);
        assertEquals("ABCDE1234F", info.get("panNumber"));
        assertEquals("Test User", info.get("name"));
    }
}
