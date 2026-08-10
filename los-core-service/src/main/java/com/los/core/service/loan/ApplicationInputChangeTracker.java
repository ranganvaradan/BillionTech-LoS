package com.los.core.service.loan;

import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks KYC and intake field changes after verification or staff send-back, without altering lifecycle rules.
 * State is stored under {@code financialInfo.changeTracking}.
 */
@Slf4j
@Service
public class ApplicationInputChangeTracker {

    static final String TRACKING_KEY = "changeTracking";

    private static final List<FieldSpec> KYC_FIELDS = List.of(
            new FieldSpec("PAN", "personalInfo", "panNumber"),
            new FieldSpec("Full name", "personalInfo", "fullName"),
            new FieldSpec("Full name", "personalInfo", "name"),
            new FieldSpec("Aadhaar", "personalInfo", "aadhaarNumber"),
            new FieldSpec("Aadhaar (last 4)", "personalInfo", "aadhaarLast4"),
            new FieldSpec("Mobile", "personalInfo", "mobile"),
            new FieldSpec("Mobile", "personalInfo", "phone"),
            new FieldSpec("GSTIN", "businessInfo", "gstin"),
            new FieldSpec("Business name", "businessInfo", "businessName"),
            new FieldSpec("Udyam registration", "businessInfo", "udyam"),
            new FieldSpec("Udyam registration", "businessInfo", "udyamRegistrationNo"),
            new FieldSpec("Driving licence", "personalInfo", "dlNo"),
            new FieldSpec("Driving licence", "personalInfo", "dlNumber"),
            new FieldSpec("Driving licence", "personalInfo", "drivingLicenseNumber"),
            new FieldSpec("Date of birth", "personalInfo", "dob"),
            new FieldSpec("Date of birth", "personalInfo", "dateOfBirth"),
            new FieldSpec("Voter ID", "personalInfo", "epicNo"),
            new FieldSpec("Voter ID", "personalInfo", "voterId"),
            new FieldSpec("Bank account", "personalInfo", "bankAccountNumber"),
            new FieldSpec("Bank account", "financialInfo", "accountNumber"),
            new FieldSpec("IFSC", "personalInfo", "ifsc"),
            new FieldSpec("IFSC", "financialInfo", "ifsc"),
            new FieldSpec("Bank name", "personalInfo", "bankName"),
            new FieldSpec("CIN", "businessInfo", "cin")
    );

    private static final List<FieldSpec> INTAKE_FIELDS = List.of(
            new FieldSpec("Requested amount", "application", "requestedAmount"),
            new FieldSpec("Tenure (months)", "application", "tenureMonths"),
            new FieldSpec("Email", "personalInfo", "email"),
            new FieldSpec("Address", "personalInfo", "addressLine"),
            new FieldSpec("City", "personalInfo", "city"),
            new FieldSpec("State", "personalInfo", "state"),
            new FieldSpec("PIN code", "personalInfo", "pincode"),
            new FieldSpec("GSTIN", "businessInfo", "gstin"),
            new FieldSpec("Business name", "businessInfo", "businessName"),
            new FieldSpec("CIN", "businessInfo", "cin"),
            new FieldSpec("Dependency vintage %", "businessInfo", "dependencyVintagePercent"),
            new FieldSpec("Anchor relationship vintage (months)", "businessInfo", "anchorRelationshipVintageMonths")
    );

    public Map<String, Object> fullFingerprint(LoanApplication app) {
        Map<String, Object> out = new LinkedHashMap<>(fingerprintIntake(app));
        return out;
    }

    public List<String> diffSnapshots(Map<String, Object> before, Map<String, Object> after) {
        List<FieldSpec> all = new ArrayList<>();
        all.addAll(KYC_FIELDS);
        all.addAll(INTAKE_FIELDS);
        return diffFingerprints(before == null ? Map.of() : before, after == null ? Map.of() : after, all);
    }

    public void recordKycVerifiedSnapshot(LoanApplication app) {
        Map<String, Object> tracking = tracking(app);
        tracking.put("kycVerifiedInputs", fingerprintKyc(app));
        tracking.put("kycInputsModified", false);
        tracking.put("kycInputChanges", List.of());
        writeTracking(app, tracking);
    }

    public void refreshKycChangeFlags(LoanApplication app) {
        Map<String, Object> tracking = tracking(app);
        @SuppressWarnings("unchecked")
        Map<String, Object> verified = (Map<String, Object>) tracking.get("kycVerifiedInputs");
        if (verified == null || verified.isEmpty()) {
            return;
        }
        Map<String, Object> current = fingerprintKyc(app);
        List<String> changes = diffFingerprints(verified, current, KYC_FIELDS);
        boolean modified = !changes.isEmpty();
        tracking.put("kycInputsModified", modified);
        tracking.put("kycInputChanges", changes);
        writeTracking(app, tracking);
        if (modified) {
            log.info("Application {} — KYC inputs modified since last verification: {}", app.getApplicationNumber(), changes);
        }
    }

    public void snapshotIntakeAtSendBack(LoanApplication app) {
        Map<String, Object> tracking = tracking(app);
        tracking.put("intakeSnapshotAtSendBack", fingerprintIntake(app));
        tracking.put("intakeModifiedSinceSendBack", false);
        tracking.put("intakeChangeSummary", List.of());
        if (hasKycVerifiedSnapshot(tracking)) {
            tracking.put("kycSnapshotAtSendBack", fingerprintKyc(app));
        }
        writeTracking(app, tracking);
    }

    public void refreshIntakeChangeSinceSendBack(LoanApplication app) {
        Map<String, Object> tracking = tracking(app);
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) tracking.get("intakeSnapshotAtSendBack");
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Map<String, Object> current = fingerprintIntake(app);
        List<String> changes = diffFingerprints(snapshot, current, INTAKE_FIELDS);
        boolean modified = !changes.isEmpty();
        tracking.put("intakeModifiedSinceSendBack", modified);
        tracking.put("intakeChangeSummary", changes);
        if (hasKycVerifiedSnapshot(tracking)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> kycSnapshot = (Map<String, Object>) tracking.get("kycSnapshotAtSendBack");
            if (kycSnapshot != null && !kycSnapshot.isEmpty()) {
                List<String> kycChanges = diffFingerprints(kycSnapshot, fingerprintKyc(app), KYC_FIELDS);
                if (!kycChanges.isEmpty()) {
                    tracking.put("kycInputsModified", true);
                    tracking.put("kycInputChanges", kycChanges);
                }
            }
        }
        writeTracking(app, tracking);
    }

    public boolean isKycInputsModified(LoanApplication app) {
        Object v = tracking(app).get("kycInputsModified");
        return Boolean.TRUE.equals(v);
    }

    public boolean isIntakeModifiedSinceSendBack(LoanApplication app) {
        Object v = tracking(app).get("intakeModifiedSinceSendBack");
        return Boolean.TRUE.equals(v);
    }

    @SuppressWarnings("unchecked")
    public List<String> kycInputChanges(LoanApplication app) {
        Object raw = tracking(app).get("kycInputChanges");
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> intakeChangeSummary(LoanApplication app) {
        Object raw = tracking(app).get("intakeChangeSummary");
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    public void applyToResponse(ApplicationResponse response, LoanApplication app) {
        Map<String, Object> tracking = tracking(app);
        response.setKycInputsModifiedSinceVerify(Boolean.TRUE.equals(tracking.get("kycInputsModified")));
        response.setKycInputChangeSummary(stringList(tracking.get("kycInputChanges")));
        response.setIntakeModifiedSinceSendBack(Boolean.TRUE.equals(tracking.get("intakeModifiedSinceSendBack")));
        response.setIntakeChangeSummary(stringList(tracking.get("intakeChangeSummary")));
    }

    private static boolean hasKycVerifiedSnapshot(Map<String, Object> tracking) {
        Object v = tracking.get("kycVerifiedInputs");
        return v instanceof Map<?, ?> m && !m.isEmpty();
    }

    private static Map<String, Object> fingerprintKyc(LoanApplication app) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldSpec spec : KYC_FIELDS) {
            String value = readField(app, spec);
            if (value != null && !value.isBlank()) {
                out.put(spec.key(), normalize(value));
            }
        }
        return out;
    }

    private static Map<String, Object> fingerprintIntake(LoanApplication app) {
        Map<String, Object> out = new LinkedHashMap<>(fingerprintKyc(app));
        for (FieldSpec spec : INTAKE_FIELDS) {
            String value = readField(app, spec);
            if (value != null && !value.isBlank()) {
                out.put(spec.key(), normalize(value));
            }
        }
        return out;
    }

    private static List<String> diffFingerprints(
            Map<String, Object> before, Map<String, Object> after, List<FieldSpec> specs) {
        List<String> changes = new ArrayList<>();
        for (FieldSpec spec : specs) {
            String key = spec.key();
            String oldVal = stringVal(before.get(key));
            String newVal = stringVal(after.get(key));
            if (oldVal == null && newVal == null) {
                continue;
            }
            if (!Objects.equals(oldVal, newVal)) {
                changes.add(spec.label() + (oldVal == null || oldVal.isBlank()
                        ? " (added)"
                        : newVal == null || newVal.isBlank()
                                ? " (removed)"
                                : " (updated)"));
            }
        }
        return changes.stream().distinct().toList();
    }

    private static String readField(LoanApplication app, FieldSpec spec) {
        return switch (spec.section()) {
            case "application" -> switch (spec.field()) {
                case "requestedAmount" -> app.getRequestedAmount() != null ? app.getRequestedAmount().toPlainString() : null;
                case "tenureMonths" -> app.getTenureMonths() != null ? String.valueOf(app.getTenureMonths()) : null;
                default -> null;
            };
            case "personalInfo" -> stringVal(mapVal(app.getPersonalInfo(), spec.field()));
            case "businessInfo" -> stringVal(mapVal(app.getBusinessInfo(), spec.field()));
            case "financialInfo" -> stringVal(mapVal(app.getFinancialInfo(), spec.field()));
            default -> null;
        };
    }

    private static Map<String, Object> tracking(LoanApplication app) {
        Map<String, Object> fi = app.getFinancialInfo();
        if (fi == null) {
            return new LinkedHashMap<>();
        }
        Object raw = fi.get(TRACKING_KEY);
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    private static void writeTracking(LoanApplication app, Map<String, Object> tracking) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        fi.put(TRACKING_KEY, tracking);
        app.setFinancialInfo(fi);
    }

    private static Object mapVal(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    private static String stringVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private record FieldSpec(String label, String section, String field) {
        String key() {
            return section + "." + field;
        }
    }
}
