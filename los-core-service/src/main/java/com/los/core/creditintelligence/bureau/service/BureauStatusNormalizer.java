package com.los.core.creditintelligence.bureau.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Maps Equifax / SurePass / CIBIL status strings → canonical bureau statuses.
 */
@Component
public class BureauStatusNormalizer {

    public enum CanonicalStatus {
        NTC,
        DBT,
        PWOS,
        LSS,
        ACCOUNT_SOLD,
        SETTLED,
        RESTRUCTURED,
        LEGAL_SUIT,
        UNKNOWN
    }

    public CanonicalStatus normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return CanonicalStatus.UNKNOWN;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (s.contains("NTC") || s.contains("NO_HIT") || s.contains("NEW_TO_CREDIT") || s.contains("THIN_FILE")) {
            return CanonicalStatus.NTC;
        }
        if (s.contains("DBT") || s.contains("DOUBTFUL")) {
            return CanonicalStatus.DBT;
        }
        if (s.contains("PWOS") || s.contains("POST_WO") || s.contains("POST_WRITE") || s.contains("WRITEOFF_SETTLED")) {
            return CanonicalStatus.PWOS;
        }
        if (s.contains("LSS") || s.contains("LOSS")) {
            return CanonicalStatus.LSS;
        }
        if (s.contains("ACCOUNT_SOLD") || s.contains("SOLD") || s.contains("ASSIGNED")) {
            return CanonicalStatus.ACCOUNT_SOLD;
        }
        if (s.contains("SETTLED") || s.contains("SETTLEMENT")) {
            return CanonicalStatus.SETTLED;
        }
        if (s.contains("RESTRUCTUR") || s.contains("RESCHEDUL")) {
            return CanonicalStatus.RESTRUCTURED;
        }
        if (s.contains("LEGAL") || s.contains("SUIT") || s.contains("LITIGATION")) {
            return CanonicalStatus.LEGAL_SUIT;
        }
        if (s.contains("WRITE") || s.contains("W_OFF") || s.contains("WOFF")) {
            // write-off without PWOS nuance → treat as LSS-ish loss bucket for counting callers
            return CanonicalStatus.LSS;
        }
        return CanonicalStatus.UNKNOWN;
    }

    public boolean matches(String raw, CanonicalStatus expected) {
        return normalize(raw) == expected;
    }
}
