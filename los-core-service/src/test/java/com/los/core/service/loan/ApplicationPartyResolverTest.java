package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationPartyResolverTest {

    @Test
    void anchorLmsPartyMapIncludesCorporateNameAndPincode() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .applicationNumber("BL07477")
                .businessInfo(Map.of(
                        "corporateName", "Acme Anchor Pvt Ltd",
                        "email", "anchor@acme.example",
                        "pincode", "560001",
                        "entityPan", "AAACB1234D"))
                .build();

        assertEquals("Acme Anchor Pvt Ltd", ApplicationPartyResolver.resolveDisplayName(app));
        assertEquals("560001", ApplicationPartyResolver.resolvePincode(app));
        assertEquals("anchor@acme.example", ApplicationPartyResolver.resolveEmail(app));

        Map<String, Object> party = ApplicationPartyResolver.buildIntegrationPartyMap(app);
        assertEquals("Acme Anchor Pvt Ltd", party.get("fullName"));
        assertEquals("560001", party.get("pinCode"));
        assertEquals("AAACB1234D", party.get("panNumber"));
    }

    @Test
    void enrichEsignSignerFromAnchorBusinessEmail() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of(
                        "corporateName", "Acme Corp",
                        "email", "signatory@acme.example"))
                .personalInfo(Map.of())
                .build();

        Map<String, Object> signer = ApplicationPartyResolver.enrichEsignSignerInfo(app, Map.of());
        assertEquals("signatory@acme.example", signer.get("borrowerEmail"));
        assertEquals("Acme Corp", signer.get("name"));
    }

    @Test
    void borrowerDisplayNameUnchanged() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.BORROWER)
                .personalInfo(Map.of("fullName", "Ravi Kumar", "email", "ravi@example.com", "pincode", "110001"))
                .build();

        assertEquals("Ravi Kumar", ApplicationPartyResolver.resolveDisplayName(app));
        assertEquals("ravi@example.com", ApplicationPartyResolver.resolveEmail(app));
        assertEquals("110001", ApplicationPartyResolver.resolvePincode(app));
    }

    @Test
    void enrichEsignDoesNotOverrideProvidedEmail() {
        LoanApplication app = LoanApplication.builder()
                .intakeSegment(IntakeSegment.ANCHOR)
                .businessInfo(Map.of("email", "stored@acme.example"))
                .build();

        Map<String, Object> signer = ApplicationPartyResolver.enrichEsignSignerInfo(
                app, Map.of("borrowerEmail", "override@example.com"));
        assertEquals("override@example.com", signer.get("borrowerEmail"));
    }
}
