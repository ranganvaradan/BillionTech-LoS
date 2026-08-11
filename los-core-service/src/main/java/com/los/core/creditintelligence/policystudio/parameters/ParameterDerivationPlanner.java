package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-PARAMETER-RESOLVER-1 — thin derivation proposal over existing registry primitives.
 * Produces a PROPOSAL only — never silently accepts or invents production authority.
 * Does not execute user text. Does not create a second metric engine.
 */
public final class ParameterDerivationPlanner {

    private static final Pattern PLUS = Pattern.compile("\\+|\\bplus\\b|\\band\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUS = Pattern.compile("\\-|\\bminus\\b|\\bless\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIV = Pattern.compile("/|\\bdivided by\\b|\\bratio\\b", Pattern.CASE_INSENSITIVE);

    private final CanonicalParameterRegistry registry;

    public ParameterDerivationPlanner(CanonicalParameterRegistry registry) {
        this.registry = registry == null ? new CanonicalParameterRegistry() : registry;
    }

    /**
     * Interpret CM plain-English description into a reviewable proposal.
     * Never auto-activates.
     */
    public Map<String, Object> propose(String term, String description) {
        String desc = description == null ? "" : description.trim();
        String businessName = term == null || term.isBlank() ? "Proposed parameter" : term.trim();

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("kind", "PROPOSAL");
        proposal.put("status", "AWAITING_CM_CONFIRMATION");
        proposal.put("businessName", businessName);
        proposal.put("originalDescription", desc);
        proposal.put("silentlyAccepted", false);
        proposal.put("allowCanonicalAuthority", false);
        proposal.put("executableCodeGenerated", false);
        proposal.put("persistence", "SESSION_DRAFT_ONLY");

        if (desc.isBlank()) {
            proposal.put("result", "INCOMPLETE");
            proposal.put("resultAvailability", ParameterResolutionSupport.AVAIL_NEEDS_INPUT);
            proposal.put("message", "Enter a business description before a proposal can be formed.");
            proposal.put("knownPrimitives", List.of());
            proposal.put("missingPrimitives", List.of());
            return proposal;
        }

        // Prefer exact existing parameter if description/name resolves unambiguously
        var existing = registry.resolve(businessName);
        if (existing.isEmpty()) {
            existing = registry.resolve(desc);
        }
        if (existing.isPresent() && !isNeedsConfigOnly(existing.get())) {
            CanonicalParameterDefinition p = existing.get();
            proposal.put("existingEquivalent", p.toBusinessView());
            proposal.put("reuseExisting", true);
            proposal.put("proposedCalculation", p.calculationSummary() != null
                    ? p.calculationSummary()
                    : "Reuse existing parameter: " + p.businessName());
            proposal.put("evaluatedFrom", p.evaluatedFrom());
            proposal.put("result", "REUSES_EXISTING");
            proposal.put("resultAvailability", p.availability());
            proposal.put("knownPrimitives", List.of(p.toBusinessView()));
            proposal.put("missingPrimitives", List.of());
            proposal.put("message",
                    "An existing parameter appears equivalent — confirm before use. Nothing was auto-applied.");
            return proposal;
        }

        List<Map<String, Object>> known = new ArrayList<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        List<CanonicalParameterDefinition> hits = extractKnownPrimitives(desc);

        for (CanonicalParameterDefinition p : hits) {
            Map<String, Object> row = new LinkedHashMap<>(p.toBusinessView());
            row.put("primitiveStatus", availabilityLabel(p));
            known.add(row);
        }

        // Detect simple composition operators in business language
        String op = detectOperator(desc);
        String calc;
        if (hits.size() >= 2 && op != null) {
            calc = hits.get(0).businessName() + " " + op + " " + hits.get(1).businessName();
            if (hits.size() > 2) {
                for (int i = 2; i < hits.size(); i++) {
                    calc = calc + " " + op + " " + hits.get(i).businessName();
                }
            }
            proposal.put("expressionKind", switch (op) {
                case "+" -> "SUM";
                case "-" -> "DIFFERENCE";
                case "/" -> "RATIO";
                default -> "COMPOSITION";
            });
        } else if (hits.size() == 1) {
            CanonicalParameterDefinition only = hits.get(0);
            calc = only.calculationSummary() != null
                    ? only.calculationSummary()
                    : "Mapped to " + only.businessName();
            proposal.put("expressionKind", "REFERENCE");
        } else {
            calc = "Description captured — primitives not fully matched to known registry entries.";
            proposal.put("expressionKind", "NARRATIVE");
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("businessName", businessName);
            gap.put("status", "UNRESOLVED");
            gap.put("note", "No existing registry primitive confidently matched every part of the description.");
            missing.add(gap);
        }

        proposal.put("proposedCalculation", calc);
        proposal.put("knownPrimitives", known);
        proposal.put("missingPrimitives", missing);
        proposal.put("evaluatedFrom", dominantSource(hits));

        boolean allKnown = !hits.isEmpty() && missing.isEmpty()
                && hits.stream().noneMatch(this::isUnavailable);
        if (allKnown && hits.size() >= 2 && op != null) {
            proposal.put("result", "CAN_BE_CALCULATED_AUTOMATICALLY");
            proposal.put("resultAvailability", ParameterResolutionSupport.AVAIL_DERIVABLE);
            proposal.put("message",
                    "Proposed calculation reuses existing primitives. Confirm with Use this definition — nothing was auto-applied.");
        } else if (!hits.isEmpty() && missing.isEmpty()) {
            proposal.put("result", "PARTIAL_MATCH");
            proposal.put("resultAvailability", ParameterResolutionSupport.AVAIL_NEEDS_CONFIG);
            proposal.put("message",
                    "Known primitives identified. Review the proposal before confirming.");
        } else {
            proposal.put("result", "NEEDS_MORE_DEFINITION");
            proposal.put("resultAvailability", ParameterResolutionSupport.AVAIL_NEEDS_INPUT);
            proposal.put("message",
                    "Proposal recorded for Credit Manager review. Not accepted until confirmed.");
        }

        // Explicit: never invent CLEAN = DPD 0
        String lower = desc.toLowerCase(Locale.ROOT);
        if (lower.contains("clean") && (lower.contains("dpd") || lower.contains("zero"))) {
            proposal.put("doNotInventCleanAsDpd0", true);
            proposal.put("message",
                    "CLEAN is not silently defined as DPD=0. Confirm the proposed understanding explicitly.");
        }
        return proposal;
    }

    private boolean isNeedsConfigOnly(CanonicalParameterDefinition p) {
        return ParameterResolutionSupport.AVAIL_NEEDS_CONFIG.equals(p.availability())
                || ParameterResolutionSupport.AVAIL_NEEDS_INPUT.equals(p.availability());
    }

    private boolean isUnavailable(CanonicalParameterDefinition p) {
        return ParameterResolutionSupport.AVAIL_UNAVAILABLE.equals(p.availability());
    }

    private List<CanonicalParameterDefinition> extractKnownPrimitives(String desc) {
        String d = desc.toLowerCase(Locale.ROOT);
        List<CanonicalParameterDefinition> hits = new ArrayList<>();
        // Prefer longer / more specific names first
        List<CanonicalParameterDefinition> ordered = new ArrayList<>(registry.all());
        ordered.sort((a, b) -> Integer.compare(
                b.businessName() == null ? 0 : b.businessName().length(),
                a.businessName() == null ? 0 : a.businessName().length()));
        for (CanonicalParameterDefinition p : ordered) {
            if (matchesPhrase(d, p)) {
                if (hits.stream().noneMatch(h -> h.id().equals(p.id()))) {
                    hits.add(p);
                }
            }
        }
        // Alias-only soft hits for EMI / bureau obligations common in EDI descriptions
        if (d.contains("emi") || d.contains("obligation")) {
            registry.findById("obligation.ratio").ifPresent(p -> {
                if (hits.stream().noneMatch(h -> h.id().equals(p.id()))) {
                    // keep as related, not forced into calc unless named
                }
            });
            registry.resolve("bureau score"); // no-op — ensure search path warm
            for (CanonicalParameterDefinition p : registry.all()) {
                if (p.aliases() == null) continue;
                for (String a : p.aliases()) {
                    if (a != null && d.contains(a.toLowerCase(Locale.ROOT))
                            && hits.stream().noneMatch(h -> h.id().equals(p.id()))) {
                        hits.add(p);
                    }
                }
            }
        }
        // Cap to keep proposal readable
        if (hits.size() > 6) {
            return hits.subList(0, 6);
        }
        return hits;
    }

    private boolean matchesPhrase(String descLower, CanonicalParameterDefinition p) {
        if (p.businessName() != null && descLower.contains(p.businessName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (p.aliases() != null) {
            for (String a : p.aliases()) {
                if (a != null && a.length() >= 3 && descLower.contains(a.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        // Soft tokens for common CM language
        if (descLower.contains("proposed loan") && p.id() != null && p.id().contains("application")) {
            return p.id().contains("edi") || p.businessName().toLowerCase(Locale.ROOT).contains("edi");
        }
        if ((descLower.contains("existing") && descLower.contains("emi"))
                || descLower.contains("monthly emi obligations")) {
            return p.id() != null && (p.id().contains("bureau") || p.id().contains("obligation"));
        }
        return false;
    }

    private String detectOperator(String desc) {
        Matcher plus = PLUS.matcher(desc);
        Matcher minus = MINUS.matcher(desc);
        Matcher div = DIV.matcher(desc);
        if (plus.find() && !div.find()) return "+";
        if (minus.find()) return "-";
        if (div.find()) return "/";
        return null;
    }

    private String dominantSource(List<CanonicalParameterDefinition> hits) {
        if (hits == null || hits.isEmpty()) return "Needs confirmation";
        if (hits.size() == 1) return hits.get(0).evaluatedFrom();
        return "Computed / Derived";
    }

    private String availabilityLabel(CanonicalParameterDefinition p) {
        return switch (p.availability() == null ? "" : p.availability()) {
            case ParameterResolutionSupport.AVAIL_AUTOMATIC -> "Available / Derived";
            case ParameterResolutionSupport.AVAIL_DERIVABLE -> "Derivable from available data";
            case ParameterResolutionSupport.AVAIL_MANUAL -> "Manual input available";
            case ParameterResolutionSupport.AVAIL_UNAVAILABLE -> "Unavailable";
            case ParameterResolutionSupport.AVAIL_NEEDS_CONFIG -> "Needs configuration";
            default -> p.availability() == null ? "Unknown" : p.availability();
        };
    }
}
