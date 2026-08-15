package com.los.core.requirement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves preferred vs alternative source keys from RequirementItem.sourceHints (W4).
 * Preferred only executes initially; alternatives are never parallel-mandatory.
 */
public final class AcquisitionSourceResolver {

    public static final String BUREAU = "BUREAU";
    public static final String KYC = "KYC";
    public static final String ACCOUNT_AGGREGATOR = "ACCOUNT_AGGREGATOR";
    public static final String BANK_STATEMENT_UPLOAD = "BANK_STATEMENT_UPLOAD";
    public static final String GST = "GST";
    public static final String ITR = "ITR";
    public static final String DOCUMENT_EXTRACTION = "DOCUMENT_EXTRACTION";
    public static final String DERIVATION = "DERIVATION";
    public static final String DIRECT_INPUT = "DIRECT_INPUT";

    private AcquisitionSourceResolver() {}

    public static String preferredSourceKey(RequirementItemEntity item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> hints = item.getSourceHints() != null ? item.getSourceHints() : Map.of();
        if (hints.get("activeSourceKey") != null) {
            return normalize(String.valueOf(hints.get("activeSourceKey")));
        }
        if (hints.get("preferredSourceKey") != null) {
            return normalize(String.valueOf(hints.get("preferredSourceKey")));
        }
        if (hints.get("sourceProvider") != null) {
            return normalize(String.valueOf(hints.get("sourceProvider")));
        }
        Object explanation = hints.get("explanation");
        if (explanation instanceof Map<?, ?> exp && exp.get("sourceProvider") != null) {
            return normalize(String.valueOf(exp.get("sourceProvider")));
        }
        FulfilmentMode mode = effectiveMode(item);
        if (mode == FulfilmentMode.DERIVATION) {
            return DERIVATION;
        }
        if (mode == FulfilmentMode.DOCUMENT_UPLOAD
                && item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED) {
            return DOCUMENT_EXTRACTION;
        }
        if (mode == FulfilmentMode.AUTOMATIC_SOURCE) {
            return inferAutomaticFamily(item);
        }
        return null;
    }

    public static List<Map<String, Object>> alternativeSources(RequirementItemEntity item) {
        if (item == null || item.getSourceHints() == null) {
            return List.of();
        }
        Object raw = item.getSourceHints().get("alternativeSources");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                out.add(copy);
            }
        }
        return out;
    }

    /**
     * Explicit fallback candidate after preferred terminal failure.
     * Example: ACCOUNT_AGGREGATOR → BANK_STATEMENT_UPLOAD / DOCUMENT_UPLOAD.
     */
    public static Optional<String> explicitFallbackSourceKey(RequirementItemEntity item) {
        for (Map<String, Object> alt : alternativeSources(item)) {
            String mode = alt.get("mode") != null ? String.valueOf(alt.get("mode")) : "";
            String key = alt.get("sourceKey") != null ? String.valueOf(alt.get("sourceKey")) : null;
            if (key != null && !key.isBlank()) {
                return Optional.of(normalize(key));
            }
            if (FulfilmentMode.DOCUMENT_UPLOAD.name().equals(mode)
                    || "BANK_STATEMENT".equalsIgnoreCase(String.valueOf(alt.get("documentGroup")))
                    || "BANK_STATEMENT_UPLOAD".equalsIgnoreCase(String.valueOf(alt.get("documentGroup")))) {
                return Optional.of(BANK_STATEMENT_UPLOAD);
            }
            if (FulfilmentMode.DOCUMENT_UPLOAD.name().equals(mode)) {
                Object dg = alt.get("documentGroup");
                if (dg != null) {
                    return Optional.of(BANK_STATEMENT_UPLOAD);
                }
            }
        }
        Object altDoc = item.getSourceHints() != null
                ? item.getSourceHints().get("alternativeDocumentGroup") : null;
        if (altDoc != null) {
            return Optional.of(BANK_STATEMENT_UPLOAD);
        }
        // Allowed modes include DOCUMENT_UPLOAD as configured alternative to AA
        if (item.allows(FulfilmentMode.DOCUMENT_UPLOAD)
                && item.allows(FulfilmentMode.AUTOMATIC_SOURCE)
                && ACCOUNT_AGGREGATOR.equals(preferredSourceKey(item))) {
            return Optional.of(BANK_STATEMENT_UPLOAD);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static List<String> dependencyParameterIds(RequirementItemEntity item) {
        List<String> deps = new ArrayList<>();
        if (item == null || item.getSourceHints() == null) {
            return deps;
        }
        Object raw = item.getSourceHints().get("dependsOn");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    deps.add(String.valueOf(o));
                }
            }
        }
        Object derivationDeps = item.getSourceHints().get("derivationDependsOn");
        if (derivationDeps instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    deps.add(String.valueOf(o));
                }
            }
        }
        return deps;
    }

    public static FulfilmentMode effectiveMode(RequirementItemEntity item) {
        if (item.getFulfilmentModeUsed() != null) {
            return item.getFulfilmentModeUsed();
        }
        Map<String, Object> hints = item.getSourceHints() != null ? item.getSourceHints() : Map.of();
        if (hints.get("chosenMode") != null) {
            try {
                return FulfilmentMode.valueOf(String.valueOf(hints.get("chosenMode")));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (hints.get("preferredMode") != null) {
            try {
                return FulfilmentMode.valueOf(String.valueOf(hints.get("preferredMode")));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (item.allowsOnly(FulfilmentMode.DERIVATION)) {
            return FulfilmentMode.DERIVATION;
        }
        if (item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)) {
            return FulfilmentMode.AUTOMATIC_SOURCE;
        }
        if (item.allows(FulfilmentMode.AUTOMATIC_SOURCE)) {
            return FulfilmentMode.AUTOMATIC_SOURCE;
        }
        if (item.allows(FulfilmentMode.DERIVATION)) {
            return FulfilmentMode.DERIVATION;
        }
        if (item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
            return FulfilmentMode.DOCUMENT_UPLOAD;
        }
        if (item.allows(FulfilmentMode.DIRECT_INPUT)) {
            return FulfilmentMode.DIRECT_INPUT;
        }
        if (item.allows(FulfilmentMode.MANUAL_REVIEW)) {
            return FulfilmentMode.MANUAL_REVIEW;
        }
        return null;
    }

    public static boolean isPlatformExecutable(RequirementItemEntity item) {
        if (item == null) {
            return false;
        }
        if (item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY) {
            return false;
        }
        if ("NO_FULFILMENT_PATH".equals(String.valueOf(
                item.getSourceHints() != null ? item.getSourceHints().get("blockingReason") : null))) {
            return false;
        }
        FulfilmentMode mode = effectiveMode(item);
        if (mode == FulfilmentMode.MANUAL_REVIEW) {
            return false;
        }
        if (mode == FulfilmentMode.DIRECT_INPUT) {
            return false;
        }
        if (mode == FulfilmentMode.DOCUMENT_UPLOAD) {
            // Only process after customer PROVIDED
            return item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                    && item.getDataReadinessState() != DataReadinessState.READY_FOR_POLICY;
        }
        if (mode == FulfilmentMode.AUTOMATIC_SOURCE || mode == FulfilmentMode.DERIVATION) {
            SourceAcquisitionState s = item.getSourceAcquisitionState();
            if (s != null && (s.isTerminalSuccess() || s.requiresCustomerAction() || s.requiresManualReview())) {
                // Still allow readiness reconciliation if SUCCEEDED but not ready
                return s == SourceAcquisitionState.SUCCEEDED
                        && item.getDataReadinessState() != DataReadinessState.READY_FOR_POLICY;
            }
            return true;
        }
        return false;
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (s.contains("BUREAU") || s.contains("EQUIFAX") || s.contains("CREDIT_BUREAU")) {
            return BUREAU;
        }
        if (s.contains("ACCOUNT_AGGREGATOR") || s.equals("AA") || s.contains("SETU")) {
            return ACCOUNT_AGGREGATOR;
        }
        if (s.contains("BANK_STATEMENT") || s.contains("BSA") || s.contains("OCR_BANK")) {
            return BANK_STATEMENT_UPLOAD;
        }
        if (s.contains("GST")) {
            return GST;
        }
        if (s.contains("ITR") || s.contains("TAX")) {
            return ITR;
        }
        if (s.contains("KYC") || s.contains("VKYC") || s.contains("CKYC")) {
            return KYC;
        }
        if (s.contains("EXTRACT") || s.contains("OCR") || s.contains("DOCUMENT")) {
            return DOCUMENT_EXTRACTION;
        }
        if (s.contains("DERIV")) {
            return DERIVATION;
        }
        return s;
    }

    private static String inferAutomaticFamily(RequirementItemEntity item) {
        String id = item.getCanonicalParameterId() != null
                ? item.getCanonicalParameterId().toUpperCase(Locale.ROOT) : "";
        String key = item.getItemKey() != null ? item.getItemKey().toUpperCase(Locale.ROOT) : "";
        String combined = id + " " + key;
        if (combined.contains("BUREAU") || combined.contains("CREDIT_SCORE") || combined.contains("CIBIL")) {
            return BUREAU;
        }
        if (combined.contains("GST")) {
            return GST;
        }
        if (combined.contains("ITR") || combined.contains("TAX")) {
            return ITR;
        }
        if (combined.contains("KYC")) {
            return KYC;
        }
        if (combined.contains("BANK") || combined.contains("ADB") || combined.contains("AA")
                || combined.contains("TURNOVER_BANK")) {
            return ACCOUNT_AGGREGATOR;
        }
        return ACCOUNT_AGGREGATOR;
    }
}
