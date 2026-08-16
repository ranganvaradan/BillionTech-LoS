package com.los.core.creditintelligence.policystudio.parameters.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wave-3 single compatibility registry for snapshot/runtime path ↔ exact GACAT ID.
 * Not a second alias mechanism — consolidates {@code ParameterExecutabilitySupport.runtimeFactAliases}.
 *
 * <p>DANGEROUS aliases (semantically different concepts) are recorded but never used for
 * automatic reverse materialization onto exact IDs.
 */
public final class CanonicalCompatibilityRegistry {

    public enum AliasClass {
        TRUE_COMPAT_ALIAS,
        SOURCE_FIELD_MAPPING,
        LEGACY_REMAP_PENDING_WAVE6,
        DANGEROUS_ALIAS_REJECTED
    }

    public record AliasEntry(String canonicalId, String aliasOrRemap, AliasClass classification, String note) {}

    private CanonicalCompatibilityRegistry() {}

    /** Exact GACAT ID → controlled remaps/aliases (same semantics). */
    public static List<String> trueCompatAliases(String canonicalId) {
        if (canonicalId == null) return List.of();
        return switch (canonicalId) {
            case "bureau.score" -> List.of("bureau.consumer.score", "compat.BUREAU_SCORE", "BUREAU_SCORE");
            case "bureau.live_unsecured_loan_count" -> List.of(
                    "compat.LIVE_UNSECURED_LOAN_COUNT", "LIVE_UNSECURED_LOAN_COUNT");
            case "bureau.max_dpd_6m" -> List.of("bureau.dpd.max_6m", "compat.MAX_DPD_6M", "MAX_DPD_6M");
            case "bureau.max_dpd_12m" -> List.of("bureau.dpd.max_12m", "compat.MAX_DPD_12M", "MAX_DPD_12M");
            case "bureau.max_dpd_24m" -> List.of("bureau.dpd.max_24m");
            case "bureau.total_monthly_obligation" -> List.of(
                    "MONTHLY_OBLIGATION", "EMI_OBLIGATION", "compat.MONTHLY_OBLIGATION");
            case "bureau.status_ntc" -> List.of("NTC_FLAG", "bureau.thin_file_indicator");
            case "bureau.written_off_account_count" -> List.of("bureau.accounts.written_off_count");
            case "bureau.settled_account_count" -> List.of("bureau.accounts.settled_count");
            case "bureau.secured_live_exposure" -> List.of("bureau.exposure.secured_live");
            case "bureau.unsecured_live_exposure" -> List.of("bureau.exposure.unsecured_live");
            case "bureau.recent_inquiries_90d" -> List.of("bureau.inquiries.count_90d");
            case "bureau.accounts.writeoff_non_cc" -> List.of("WRITEOFF_NON_CC", "compat.WRITEOFF_NON_CC");
            case "banking.avg_daily_balance_3m" -> List.of(
                    "banking.balance.average_3m", "banking.average_balance");
            case "banking.emi_bounce_count_3m" -> List.of("banking.bounce.emi_count_3m");
            case "kyc.pan.verified" -> List.of("kyc.pan_verified");
            case "application.declared_income" -> List.of("applicant.declared_annual_income");
            default -> List.of();
        };
    }

    /** Aliases that must NOT auto-promote to exact ID (different semantics). */
    public static List<String> dangerousAliases(String canonicalId) {
        if ("bureau.recent_inquiries_90d".equals(canonicalId)) {
            return List.of("compat.BUREAU_ENQUIRIES_3M", "BUREAU_ENQUIRIES_3M");
        }
        return List.of();
    }

    /**
     * Reverse: snapshot/runtime path → exact GACAT ID when TRUE_COMPAT only.
     * Never reverses dangerous 90d↔3m aliases.
     */
    public static String exactCanonicalIdForPath(String pathOrAlias) {
        if (pathOrAlias == null || pathOrAlias.isBlank()) {
            return null;
        }
        String p = pathOrAlias.trim();
        // Already exact for known dual-write targets
        for (AliasEntry e : allEntries()) {
            if (e.classification() == AliasClass.DANGEROUS_ALIAS_REJECTED) {
                continue;
            }
            if (p.equals(e.canonicalId())) {
                return e.canonicalId();
            }
            if (p.equals(e.aliasOrRemap())) {
                return e.canonicalId();
            }
        }
        return null;
    }

    public static List<AliasEntry> allEntries() {
        List<AliasEntry> out = new ArrayList<>();
        addAll(out, "bureau.score", AliasClass.TRUE_COMPAT_ALIAS, "Score identity");
        addAll(out, "bureau.live_unsecured_loan_count", AliasClass.TRUE_COMPAT_ALIAS, null);
        addAll(out, "bureau.max_dpd_6m", AliasClass.LEGACY_REMAP_PENDING_WAVE6, "Snapshot may use bureau.dpd.max_6m");
        addAll(out, "bureau.max_dpd_12m", AliasClass.LEGACY_REMAP_PENDING_WAVE6, "Snapshot remapped to bureau.dpd.max_12m");
        addAll(out, "bureau.max_dpd_24m", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "bureau.total_monthly_obligation", AliasClass.TRUE_COMPAT_ALIAS, null);
        addAll(out, "bureau.status_ntc", AliasClass.TRUE_COMPAT_ALIAS, null);
        addAll(out, "bureau.written_off_account_count", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "bureau.settled_account_count", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "bureau.secured_live_exposure", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "bureau.unsecured_live_exposure", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "bureau.recent_inquiries_90d", AliasClass.LEGACY_REMAP_PENDING_WAVE6,
                "count_90d remap is TRUE_COMPAT; ENQUIRIES_3M is dangerous");
        for (String d : dangerousAliases("bureau.recent_inquiries_90d")) {
            out.add(new AliasEntry("bureau.recent_inquiries_90d", d, AliasClass.DANGEROUS_ALIAS_REJECTED,
                    "90d != 3m enquiry window"));
        }
        addAll(out, "bureau.accounts.writeoff_non_cc", AliasClass.TRUE_COMPAT_ALIAS, null);
        addAll(out, "banking.avg_daily_balance_3m", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "banking.emi_bounce_count_3m", AliasClass.LEGACY_REMAP_PENDING_WAVE6, null);
        addAll(out, "kyc.pan.verified", AliasClass.SOURCE_FIELD_MAPPING, "pan_verified snake vs dotted");
        addAll(out, "application.declared_income", AliasClass.LEGACY_REMAP_PENDING_WAVE6,
                "applicant.declared_annual_income");
        return out;
    }

    private static void addAll(List<AliasEntry> out, String canonical, AliasClass cls, String note) {
        for (String a : trueCompatAliases(canonical)) {
            out.add(new AliasEntry(canonical, a, cls, note));
        }
    }

    /**
     * Project remapped snapshot facts onto exact GACAT IDs (putIfAbsent).
     * Keeps legacy paths for non-spine consumers. Does not apply dangerous aliases.
     */
    public static Map<String, Object> projectExactCanonicalFacts(Map<String, Object> remappedOrMixed) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (remappedOrMixed == null) {
            return out;
        }
        // First pass: copy all
        out.putAll(remappedOrMixed);
        // Second: ensure exact IDs present when a true-compat alias has a value
        for (Map.Entry<String, Object> e : remappedOrMixed.entrySet()) {
            if (e.getValue() == null) continue;
            String exact = exactCanonicalIdForPath(e.getKey());
            if (exact != null && !exact.equals(e.getKey())) {
                // Reject dangerous reverse for enquiry 3m keys
                String keyUpper = e.getKey().toUpperCase(Locale.ROOT);
                if (keyUpper.contains("ENQUIRIES_3M") || keyUpper.contains("ENQUIRIES.3M")) {
                    continue;
                }
                out.putIfAbsent(exact, e.getValue());
            }
        }
        return out;
    }
}
