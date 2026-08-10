package com.los.core.service.loan;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves canonical identity fields (especially PAN) for KYC, bureau, and underwriting flows.
 * Anchor intake stores entity PAN on {@code businessInfo.entityPan}; borrower intake uses
 * {@code personalInfo.panNumber}.
 */
public final class ApplicantIdentityResolver {

    private ApplicantIdentityResolver() {
    }

    public static String resolvePanNumber(LoanApplication app) {
        if (app == null) {
            return "";
        }
        String fromPersonal = stringValue(app.getPersonalInfo(), "panNumber");
        if (!fromPersonal.isBlank()) {
            return fromPersonal.trim().toUpperCase();
        }
        if (isAnchor(app)) {
            String entityPan = stringValue(app.getBusinessInfo(), "entityPan");
            if (!entityPan.isBlank()) {
                return entityPan.trim().toUpperCase();
            }
        }
        return "";
    }

    /**
     * Context map for bureau providers: personal + business fields with canonical {@code panNumber}.
     */
    public static Map<String, Object> buildBureauBorrowerInfo(LoanApplication app) {
        Map<String, Object> out = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            out.putAll(app.getPersonalInfo());
        }
        if (app.getBusinessInfo() != null) {
            out.putAll(app.getBusinessInfo());
        }
        String pan = resolvePanNumber(app);
        if (!pan.isBlank()) {
            out.put("panNumber", pan);
        }
        if (app.getId() != null) {
            out.put("applicationId", app.getId().toString());
        }
        if (app.getApplicationNumber() != null) {
            out.put("applicationNumber", app.getApplicationNumber());
        }
        return out;
    }

    /**
     * Merges stored application identity into the KYC workflow payload without dropping request overrides.
     */
    public static Map<String, Object> enrichKycPayload(LoanApplication app, Map<String, Object> payload) {
        Map<String, Object> merged = new HashMap<>();
        if (app.getPersonalInfo() != null) {
            merged.putAll(app.getPersonalInfo());
        }
        if (app.getBusinessInfo() != null) {
            merged.putAll(app.getBusinessInfo());
            if (isAnchor(app)) {
                copyIfPresent(merged, app.getBusinessInfo(), "entityPan", "gstin", "cin");
                copyIfPresent(merged, app.getBusinessInfo(), "bankAccountNumber", "ifscCode", "accountHolderName");
                String acct = stringValue(app.getBusinessInfo(), "bankAccountNumber");
                if (!acct.isBlank()) {
                    merged.putIfAbsent("accountNumber", acct);
                }
                String ifsc = stringValue(app.getBusinessInfo(), "ifscCode");
                if (!ifsc.isBlank()) {
                    merged.putIfAbsent("ifsc", ifsc.toUpperCase());
                }
                String corporate = stringValue(app.getBusinessInfo(), "corporateName");
                if (!corporate.isBlank()) {
                    merged.putIfAbsent("businessName", corporate);
                    merged.putIfAbsent("name", stringValue(app.getBusinessInfo(), "accountHolderName"));
                }
                String mobile = stringValue(app.getBusinessInfo(), "mobile");
                if (!mobile.isBlank()) {
                    merged.putIfAbsent("mobile", mobile);
                    merged.putIfAbsent("phone", mobile);
                }
            }
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
            merged.put("panNumber", pan);
        }
        String requestPan = payload != null ? stringValue(payload, "panNumber") : "";
        if (!requestPan.isBlank()) {
            merged.put("panNumber", requestPan.trim().toUpperCase());
        }
        normalizeKycFieldAliases(merged);
        return merged;
    }

    /** Map intake field keys to the names expected by KYC providers. */
    private static void normalizeKycFieldAliases(Map<String, Object> merged) {
        String dl = firstNonBlank(merged, "dlNo", "drivingLicenseNumber", "dlNumber");
        if (!dl.isBlank()) {
            String upper = dl.toUpperCase();
            merged.put("dlNo", upper);
            merged.put("drivingLicenseNumber", upper);
            merged.put("dlNumber", upper);
        }
        String epic = firstNonBlank(merged, "epicNo", "voterId");
        if (!epic.isBlank()) {
            String upper = epic.toUpperCase();
            merged.put("epicNo", upper);
            merged.put("voterId", upper);
        }
        String dob = firstNonBlank(merged, "dob", "dateOfBirth", "drivingLicenseDob");
        if (!dob.isBlank()) {
            merged.putIfAbsent("dob", dob);
            merged.putIfAbsent("drivingLicenseDob", dob);
            merged.putIfAbsent("dateOfBirth", dob);
        }
        String acct = firstNonBlank(merged, "accountNumber", "bankAccountNumber");
        if (!acct.isBlank()) {
            merged.putIfAbsent("accountNumber", acct);
            merged.putIfAbsent("bankAccountNumber", acct);
        }
        // Intake / UI store mobile|phone|borrowerMobile; Authbridge MOBILE_OTP reads mobileNumber.
        String mobileRaw = firstNonBlank(merged, "mobileNumber", "mobile", "phone", "borrowerMobile");
        String mobileDigits = normalizeIndianMobileDigits(mobileRaw);
        if (!mobileDigits.isBlank()) {
            merged.put("mobileNumber", mobileDigits);
            merged.put("mobile", mobileDigits);
            merged.put("phone", mobileDigits);
        }
    }

    /**
     * Strip non-digits; drop leading country code 91 when present; return last 10 digits when possible.
     * Does not log or return the original raw value.
     */
    public static String normalizeIndianMobileDigits(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return "";
        }
        if (digits.length() >= 12 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        } else if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
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
        return app.getIntakeSegment() == IntakeSegment.ANCHOR;
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
