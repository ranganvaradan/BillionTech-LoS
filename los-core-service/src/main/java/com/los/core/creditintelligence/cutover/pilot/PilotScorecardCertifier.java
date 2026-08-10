package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scorecard certification — score/grade comparison (§21).
 */
@Service
public class PilotScorecardCertifier {

    public record ScorecardCase(
            String applicationLabel,
            BigDecimal legacyScore,
            BigDecimal canonicalScore,
            String legacyGrade,
            String canonicalGrade,
            boolean materialDifferenceExplained
    ) {
    }

    public record ScorecardCertResult(
            boolean componentsMapped,
            boolean bandsFrozen,
            boolean weightsFrozen,
            boolean missingSemanticsExplicit,
            boolean pass,
            List<Map<String, Object>> materialDifferences,
            List<String> blockers
    ) {
    }

    public ScorecardCertResult certify(
            boolean componentsMapped,
            boolean bandsFrozen,
            boolean weightsFrozen,
            boolean missingSemanticsExplicit,
            List<ScorecardCase> cases) {
        List<ScorecardCase> list = cases == null ? List.of() : cases;
        List<Map<String, Object>> material = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        for (ScorecardCase c : list) {
            boolean scoreDiff = c.legacyScore() != null && c.canonicalScore() != null
                    && c.legacyScore().subtract(c.canonicalScore()).abs().compareTo(new BigDecimal("5")) > 0;
            boolean gradeDiff = !Objects.equals(c.legacyGrade(), c.canonicalGrade());
            if (scoreDiff || gradeDiff) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("application", c.applicationLabel());
                row.put("legacyScore", c.legacyScore());
                row.put("canonicalScore", c.canonicalScore());
                row.put("legacyGrade", c.legacyGrade());
                row.put("canonicalGrade", c.canonicalGrade());
                row.put("explained", c.materialDifferenceExplained());
                material.add(row);
                if (!c.materialDifferenceExplained()) {
                    blockers.add("Unexplained scorecard difference for " + c.applicationLabel());
                }
            }
        }
        if (!componentsMapped) blockers.add("Scorecard components not fully mapped");
        if (!bandsFrozen) blockers.add("Scorecard bands not frozen");
        if (!weightsFrozen) blockers.add("Scorecard weights not frozen");
        if (!missingSemanticsExplicit) blockers.add("Missing scorecard semantics not explicit");

        boolean pass = blockers.isEmpty() && componentsMapped && bandsFrozen
                && weightsFrozen && missingSemanticsExplicit;
        return new ScorecardCertResult(componentsMapped, bandsFrozen, weightsFrozen,
                missingSemanticsExplicit, pass, material, blockers);
    }
}
