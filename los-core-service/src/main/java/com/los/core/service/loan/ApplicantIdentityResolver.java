package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sole KYC/identity alias → canonical field boundary.
 * <p>
 * UI and persisted JSON may retain aliases. After this class runs, workflow execution
 * and provider adapters consume canonical keys. Provider adapters may then rename
 * canonical fields into vendor-specific request keys.
 */
public final class ApplicantIdentityResolver {

    public static final String CANONICAL_PAN = "panNumber";
    public static final String CANONICAL_AADHAAR = "aadhaarNumber";
    public static final String CANONICAL_ACCOUNT = "accountNumber";
    public static final String CANONICAL_IFSC = "ifsc";
    public static final String CANONICAL_GSTIN = "gstin";
    public static final String CANONICAL_UDYAM = "udyam";
    public static final String CANONICAL_CIN = "cin";
    public static final String CANONICAL_NAME = "name";
    public static final String CANONICAL_MOBILE = "mobileNumber";
    public static final String CANONICAL_DOB = "dateOfBirth";
    public static final String CANONICAL_DL = "dlNumber";
    public static final String CANONICAL_VOTER = "voterId";
    public static final String CANONICAL_EMAIL = "email";
    public static final String BANK_ACCOUNT_CATALOG_KEY = "bankAccountNumber";

    private ApplicantIdentityResolver() {
    }

    public static String resolvePanNumber(LoanApplication app) {
        if (app == null) {
            return "";
        }
        String fromPersonal = firstNonBlank(
                app.getPersonalInfo(), "panNumber", "pan", "panNo");
        if (!fromPersonal.isBlank()) {
            return fromPersonal.trim().toUpperCase(Locale.ROOT);
        }
        if (isAnchor(app)) {
            String entityPan = stringValue(app.getBusinessInfo(), "entityPan");
            if (!entityPan.isBlank()) {
                return entityPan.trim().toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    /**
     * Persist-time fold: write canonical keys without dropping aliases.
     */
    public static Map<String, Object> canonicalisePersonalInfo(Map<String, Object> personal) {
        if (personal == null || personal.isEmpty()) {
            return personal;
        }
        Map<String, Object> out = new HashMap<>(personal);
        normalizeIdentityMap(out);
        return out;
    }

    public static Map<String, Object> canonicaliseBusinessInfo(Map<String, Object> business) {
        if (business == null || business.isEmpty()) {
            return business;
        }
        Map<String, Object> out = new HashMap<>(business);
        normalizeIdentityMap(out);
        return out;
    }

    /**
     * Context map for bureau providers: personal + business + financial with canonical keys.
     */
    public static Map<String, Object> buildBureauBorrowerInfo(LoanApplication app) {
        Map<String, Object> out = mergeApplicationIdentity(app, Map.of());
        if (app != null && app.getId() != null) {
            out.put("applicationId", app.getId().toString());
        }
        if (app != null && app.getApplicationNumber() != null) {
            out.put("applicationNumber", app.getApplicationNumber());
        }
        return out;
    }

    /**
     * Merges stored application identity into the KYC workflow payload without dropping request overrides.
     */
    public static Map<String, Object> enrichKycPayload(LoanApplication app, Map<String, Object> payload) {
        return mergeApplicationIdentity(app, payload);
    }

    /**
     * Intake required-field resolution. Delegates alias lists to this boundary so the validator
     * does not keep a second alias table.
     */
    public static String resolveIntakeField(
            String fieldKey,
            Map<String, Object> personal,
            Map<String, Object> business,
            LoanApplication app) {
        if (app != null && "panNumber".equals(fieldKey)) {
            String pan = resolvePanNumber(app);
            if (!pan.isBlank()) {
                return pan;
            }
        }
        Map<String, Object> merged = new HashMap<>();
        if (personal != null) {
            merged.putAll(personal);
        }
        if (business != null) {
            merged.putAll(business);
        }
        if (app != null) {
            copyFinancialAliases(merged, app.getFinancialInfo());
        }
        normalizeIdentityMap(merged);
        if (fieldKey == null || fieldKey.isBlank()) {
            return "";
        }
        return switch (fieldKey) {
            case "panNumber" -> stringValue(merged, CANONICAL_PAN);
            case "aadhaar" -> firstNonBlank(merged, CANONICAL_AADHAAR, "aadhaarLast4", "aadhaar");
            case "voterId" -> stringValue(merged, CANONICAL_VOTER);
            case "dlNumber" -> stringValue(merged, CANONICAL_DL);
            case "gstin" -> stringValue(merged, CANONICAL_GSTIN);
            case "cin" -> stringValue(merged, CANONICAL_CIN);
            case "udyam" -> stringValue(merged, CANONICAL_UDYAM);
            case "bankAccountNumber" -> firstNonBlank(merged, BANK_ACCOUNT_CATALOG_KEY, CANONICAL_ACCOUNT);
            default -> stringValue(merged, fieldKey);
        };
    }

    public static boolean hasCanonicalExecutionValue(String stepName, Map<String, Object> payload) {
        if (stepName == null || stepName.isBlank()) {
            return false;
        }
        String step = stepName.trim().toUpperCase(Locale.ROOT);
        return switch (step) {
            case "PAN_VERIFY" -> hasText(payload, CANONICAL_PAN);
            case "AADHAAR_OTP" -> digits(stringValue(payload, CANONICAL_AADHAAR)).length() == 12;
            case "VOTER_ID_VERIFY" -> hasText(payload, CANONICAL_VOTER);
            case "DL_VERIFY" -> hasText(payload, CANONICAL_DL);
            case "GSTIN_VERIFY" -> hasText(payload, CANONICAL_GSTIN);
            case "UDYAM_VERIFY" -> hasText(payload, CANONICAL_UDYAM);
            case "BANK_PENNY_DROP" -> hasText(payload, CANONICAL_ACCOUNT) && hasText(payload, CANONICAL_IFSC);
            case "CIN_MCA21" -> hasText(payload, CANONICAL_CIN);
            case "MNRL", "MOBILE_OTP" -> hasText(payload, CANONICAL_MOBILE);
            case "EMAIL_OTP" -> hasText(payload, CANONICAL_EMAIL);
            default -> true;
        };
    }

    /**
     * Strip non-digits; drop leading country code 91 when present; return last 10 digits when possible.
     * Does not log or return the original raw value.
     */
    public static String normalizeIndianMobileDigits(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digitChars = raw.replaceAll("\\D+", "");
        if (digitChars.isBlank()) {
            return "";
        }
        if (digitChars.length() >= 12 && digitChars.startsWith("91")) {
            digitChars = digitChars.substring(digitChars.length() - 10);
        } else if (digitChars.length() > 10) {
            digitChars = digitChars.substring(digitChars.length() - 10);
        }
        return digitChars;
    }

    static void normalizeIdentityMap(Map<String, Object> merged) {
        if (merged == null) {
            return;
        }
        String pan = firstNonBlank(merged, "panNumber", "pan", "panNo", "entityPan");
        if (!pan.isBlank()) {
            merged.put(CANONICAL_PAN, pan.toUpperCase(Locale.ROOT));
        }

        String aadhaar12 = "";
        String aadhaarRaw = firstNonBlank(merged, "aadhaarNumber", "aadhaar");
        String aadhaarDigits = digits(aadhaarRaw);
        if (aadhaarDigits.length() == 12) {
            aadhaar12 = aadhaarDigits;
        }
        String last4 = digits(stringValue(merged, "aadhaarLast4"));
        if (last4.length() == 4 && aadhaar12.isBlank()) {
            merged.put("aadhaarLast4", last4);
        }
        if (!aadhaar12.isBlank()) {
            merged.put(CANONICAL_AADHAAR, aadhaar12);
            merged.put("aadhaar", aadhaar12);
        }

        String acct = firstNonBlank(merged, "accountNumber", "bankAccountNumber");
        if (!acct.isBlank()) {
            merged.put(CANONICAL_ACCOUNT, acct);
            merged.put(BANK_ACCOUNT_CATALOG_KEY, acct);
        }

        String ifsc = firstNonBlank(merged, "ifsc", "ifscCode");
        if (!ifsc.isBlank()) {
            String upper = ifsc.toUpperCase(Locale.ROOT);
            merged.put(CANONICAL_IFSC, upper);
            merged.put("ifscCode", upper);
        }

        String gstin = firstNonBlank(merged, "gstin");
        if (!gstin.isBlank()) {
            merged.put(CANONICAL_GSTIN, gstin.toUpperCase(Locale.ROOT));
        }

        String udyam = firstNonBlank(merged, "udyam", "udyamNumber", "udyamRegistrationNo");
        if (!udyam.isBlank()) {
            String upper = udyam.toUpperCase(Locale.ROOT);
            merged.put(CANONICAL_UDYAM, upper);
            merged.put("udyamRegistrationNo", upper);
        }

        String cin = firstNonBlank(merged, "cin", "CIN_MCA21");
        if (!cin.isBlank()) {
            String upper = cin.toUpperCase(Locale.ROOT);
            merged.put(CANONICAL_CIN, upper);
        }

        String name = firstNonBlank(merged, "name", "fullName", "accountHolderName");
        if (!name.isBlank()) {
            merged.put(CANONICAL_NAME, name);
            merged.putIfAbsent("fullName", name);
        }

        String dl = firstNonBlank(merged, "dlNo", "drivingLicenseNumber", "dlNumber");
        if (!dl.isBlank()) {
            String upper = dl.toUpperCase(Locale.ROOT);
            merged.put("dlNo", upper);
            merged.put("drivingLicenseNumber", upper);
            merged.put(CANONICAL_DL, upper);
        }

        String epic = firstNonBlank(merged, "epicNo", "voterId");
        if (!epic.isBlank()) {
            String upper = epic.toUpperCase(Locale.ROOT);
            merged.put("epicNo", upper);
            merged.put(CANONICAL_VOTER, upper);
        }

        String dob = firstNonBlank(merged, "dob", "dateOfBirth", "drivingLicenseDob");
        if (!dob.isBlank()) {
            merged.put("dob", dob);
            merged.put("drivingLicenseDob", dob);
            merged.put(CANONICAL_DOB, dob);
        }

        String mobileRaw = firstNonBlank(merged, "mobileNumber", "mobile", "phone", "borrowerMobile");
        String mobileDigits = normalizeIndianMobileDigits(mobileRaw);
        if (!mobileDigits.isBlank()) {
            merged.put(CANONICAL_MOBILE, mobileDigits);
            merged.put("mobile", mobileDigits);
            merged.put("phone", mobileDigits);
        }

        String email = firstNonBlank(merged, "email", "borrowerEmail", "contactEmail");
        if (!email.isBlank()) {
            merged.put(CANONICAL_EMAIL, email);
        }
    }

    private static Map<String, Object> mergeApplicationIdentity(LoanApplication app, Map<String, Object> payload) {
        Map<String, Object> merged = new HashMap<>();
        if (app != null && app.getPersonalInfo() != null) {
            merged.putAll(app.getPersonalInfo());
        }
        if (app != null && app.getBusinessInfo() != null) {
            merged.putAll(app.getBusinessInfo());
            if (isAnchor(app)) {
                copyIfPresent(merged, app.getBusinessInfo(), "entityPan", "gstin", "cin");
                copyIfPresent(merged, app.getBusinessInfo(),
                        "bankAccountNumber", "ifscCode", "accountHolderName", "corporateName");
                String corporate = stringValue(app.getBusinessInfo(), "corporateName");
                if (!corporate.isBlank()) {
                    merged.putIfAbsent("businessName", corporate);
                }
            }
        }
        if (app != null) {
            copyFinancialAliases(merged, app.getFinancialInfo());
        }
        if (payload != null) {
            payload.forEach((k, v) -> {
                if (v != null && !String.valueOf(v).isBlank()) {
                    merged.put(k, v);
                }
            });
        }
        String pan = resolvePanNumber(app);
        if (!pan.isBlank()) {
            merged.put(CANONICAL_PAN, pan);
        }
        String requestPan = payload != null ? stringValue(payload, CANONICAL_PAN) : "";
        if (!requestPan.isBlank()) {
            merged.put(CANONICAL_PAN, requestPan.trim().toUpperCase(Locale.ROOT));
        }
        normalizeIdentityMap(merged);
        return merged;
    }

    private static void copyFinancialAliases(Map<String, Object> target, Map<String, Object> financial) {
        if (financial == null || financial.isEmpty()) {
            return;
        }
        copyIfAbsent(target, financial, "accountNumber", "bankAccountNumber", "ifsc", "ifscCode");
    }

    private static void copyIfAbsent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (!hasText(target, key) && hasText(source, key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private static boolean hasText(Map<String, Object> map, String key) {
        return !stringValue(map, key).isBlank();
    }

    private static String digits(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("\\D+", "");
    }

    private static String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String v = stringValue(map, key);
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static boolean isAnchor(LoanApplication app) {
        return app != null && app.getIntakeSegment() == IntakeSegment.ANCHOR;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }
}
