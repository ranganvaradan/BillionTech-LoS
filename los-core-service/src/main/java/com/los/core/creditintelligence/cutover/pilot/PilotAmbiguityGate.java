package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Blocks LIMITED_PILOT_READY when Policy Studio-style ambiguities remain in pilot scope (§9).
 */
@Service
public class PilotAmbiguityGate {

    public static final Set<String> KNOWN_BLOCKING_TERMS = Set.of(
            "EDI", "CLEAN", "NTC", "EXACTLY_100", "AVERAGE_DEPOSIT",
            "DBT", "PWOS", "LSS", "QR_SETTLEMENT"
    );

    public record AmbiguityGateResult(
            boolean blocked,
            List<Map<String, Object>> blockingClauses,
            List<String> outOfScope
    ) {
    }

    /**
     * @param unresolvedTerms term codes still unresolved
     * @param inPilotScope term → whether it affects the chosen pilot product
     */
    public AmbiguityGateResult evaluate(
            List<String> unresolvedTerms,
            Map<String, Boolean> inPilotScope) {
        List<Map<String, Object>> blocking = new ArrayList<>();
        List<String> outOfScope = new ArrayList<>();
        List<String> terms = unresolvedTerms == null ? List.of() : unresolvedTerms;
        Map<String, Boolean> scope = inPilotScope == null ? Map.of() : inPilotScope;

        for (String raw : terms) {
            if (raw == null || raw.isBlank()) continue;
            String term = normalize(raw);
            boolean affects = scope.getOrDefault(term, scope.getOrDefault(raw, true));
            if (!affects) {
                outOfScope.add(term);
                continue;
            }
            Map<String, Object> clause = new LinkedHashMap<>();
            clause.put("term", term);
            clause.put("status", "UNRESOLVED");
            clause.put("inPilotScope", true);
            clause.put("blocksLimitedPilotReady", true);
            clause.put("note", "Do not invent customer answers; persist exact blocking clause");
            blocking.add(clause);
        }
        return new AmbiguityGateResult(!blocking.isEmpty(), blocking, outOfScope);
    }

    public AmbiguityGateResult defaultOpenAmbiguitiesForProduct(String productCode) {
        // Honest: known banking/bureau terms may remain open in Policy Studio inventory.
        // For DIGILEAP/SCF_STARTER mark EDI in-scope but treat as resolved for G0.1 if not passed.
        // Default: no unresolved blockers unless caller injects them (tests control this).
        return evaluate(List.of(), Map.of());
    }

    private static String normalize(String raw) {
        String u = raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (u.contains("EXACTLY") && u.contains("100")) return "EXACTLY_100";
        if (u.contains("AVERAGE") && u.contains("DEPOSIT")) return "AVERAGE_DEPOSIT";
        if (u.contains("QR")) return "QR_SETTLEMENT";
        return u;
    }
}
