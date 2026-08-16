package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Advisory derivation research over GACAT metadata.
 * Never creates an executable definition; never calls an LLM at runtime evaluation.
 */
@Service
@RequiredArgsConstructor
public class DerivedCalculationResearchService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_READY_FOR_REVIEW = "READY_FOR_REVIEW";
    public static final String STATUS_NEEDS_INPUT = "NEEDS_INPUT";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final CiGacatDerivedCalculationProposalRepository proposalRepository;
    private final DerivedCalculationDefinitionService definitionService;

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    @Transactional
    public Map<String, Object> suggest(String targetParameterId, UUID tenantId, String actor) {
        if (targetParameterId == null || targetParameterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetParameterId required");
        }
        CanonicalParameterDefinition target = registry().findById(targetParameterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown GACAT parameter: " + targetParameterId));

        // Supersede prior open proposals for this target
        for (CiGacatDerivedCalculationProposal prior :
                proposalRepository.findByTargetParameterIdOrderByCreatedAtDesc(targetParameterId)) {
            if (List.of(STATUS_DRAFT, STATUS_READY_FOR_REVIEW, STATUS_NEEDS_INPUT)
                    .contains(prior.getProposalStatus())) {
                prior.setProposalStatus(STATUS_SUPERSEDED);
                prior.setUpdatedAt(Instant.now());
                proposalRepository.save(prior);
            }
        }

        List<Map<String, Object>> options = buildOptions(target, tenantId);
        if (options.isEmpty()) {
            CiGacatDerivedCalculationProposal empty = CiGacatDerivedCalculationProposal.builder()
                    .tenantId(tenantId)
                    .targetParameterId(target.id())
                    .targetParameterName(target.businessName())
                    .scope(DerivedCalculationDefinitionService.SCOPE_PLATFORM)
                    .proposalStatus(STATUS_NEEDS_INPUT)
                    .confidence("LOW")
                    .humanExplanation(
                            "Unable to recommend a calculation from the currently available canonical parameters.")
                    .missingDependencies(List.of(
                            "No exact GACAT dependencies with sufficient metadata to form a safe expression"))
                    .evidence(List.of("catalogue_scan", "requiredPrimitives", "source_family"))
                    .createdBy(actor)
                    .recommended(false)
                    .optionIndex(1)
                    .build();
            empty = proposalRepository.save(empty);
            Map<String, Object> out = toView(empty);
            out.put("options", List.of(toView(empty)));
            out.put("unableToRecommend", true);
            return out;
        }

        List<Map<String, Object>> savedViews = new ArrayList<>();
        UUID primaryId = null;
        int idx = 1;
        for (Map<String, Object> opt : options) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expr = opt.get("proposedExpression") instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m) : null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deps = opt.get("candidateDependencies") instanceof List<?> l
                    ? castDepList(l) : List.of();
            @SuppressWarnings("unchecked")
            List<String> assumptions = opt.get("assumptions") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();
            @SuppressWarnings("unchecked")
            List<String> limitations = opt.get("limitations") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();
            @SuppressWarnings("unchecked")
            List<String> missing = opt.get("missingDependencies") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();
            @SuppressWarnings("unchecked")
            List<String> evidence = opt.get("evidence") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();

            CiGacatDerivedCalculationProposal row = CiGacatDerivedCalculationProposal.builder()
                    .tenantId(tenantId)
                    .targetParameterId(target.id())
                    .targetParameterName(target.businessName())
                    .scope(DerivedCalculationDefinitionService.SCOPE_PLATFORM)
                    .proposalStatus(String.valueOf(opt.getOrDefault("proposalStatus", STATUS_READY_FOR_REVIEW)))
                    .confidence(String.valueOf(opt.getOrDefault("confidence", "MEDIUM")))
                    .humanExplanation(String.valueOf(opt.getOrDefault("humanExplanation", "")))
                    .proposedExpression(expr)
                    .candidateDependencies(deps)
                    .assumptions(assumptions)
                    .limitations(limitations)
                    .missingDependencies(missing)
                    .evidence(evidence)
                    .optionIndex(idx)
                    .recommended(Boolean.TRUE.equals(opt.get("recommended")) || idx == 1)
                    .createdBy(actor)
                    .metadata(Map.of(
                            "advisoryOnly", true,
                            "arbitraryCodeAllowed", false,
                            "llmRuntimeForbidden", true))
                    .build();
            row = proposalRepository.save(row);
            if (primaryId == null) primaryId = row.getId();
            savedViews.add(toView(row));
            idx++;
        }
        // Copy primary out of the options list to avoid a circular Map graph
        // (Jackson would otherwise emit nested ]}]}]} until the response fails).
        Map<String, Object> primary = new LinkedHashMap<>(savedViews.get(0));
        primary.put("options", savedViews);
        primary.put("unableToRecommend", false);
        primary.put("primaryProposalId", primaryId);
        return primary;
    }

    /**
     * Generic catalogue-driven options. No parameter-id hardcoding.
     * Semantic similarity only discovers candidates; every dependency is an exact GACAT id.
     * READY_FOR_REVIEW REF requires semantic/dimensional compatibility — overlap alone is insufficient.
     */
    private List<Map<String, Object>> buildOptions(CanonicalParameterDefinition target, UUID tenantId) {
        List<Map<String, Object>> options = new ArrayList<>();
        List<String> primitives = target.requiredPrimitives() == null
                ? List.of() : target.requiredPrimitives();
        List<Map<String, Object>> primitiveDeps = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String pid : primitives) {
            Optional<CanonicalParameterDefinition> dep = registry().findById(pid);
            if (dep.isEmpty()) {
                missing.add(pid);
                continue;
            }
            primitiveDeps.add(depView(dep.get(), "Listed as required primitive on target catalogue entry"));
        }

        String missingTreatment = target.capability() == null
                ? "" : String.valueOf(target.capability().missingDataTreatment());
        boolean needsConfig = missingTreatment.toUpperCase(Locale.ROOT).contains("NEEDS_CONFIGURATION")
                || missingTreatment.toUpperCase(Locale.ROOT).contains("CALCULATION_NOT_IMPLEMENTED")
                || missingTreatment.toUpperCase(Locale.ROOT).contains("VOCABULARY")
                || "CUSTOMER_DEFINED".equalsIgnoreCase(
                        target.period() == null ? "" : target.period().trim());

        // Option A: single exact primitive REF only when strong semantic equivalence holds
        if (primitiveDeps.size() == 1 && missing.isEmpty() && !needsConfig) {
            String onlyId = String.valueOf(primitiveDeps.get(0).get("parameterId"));
            CanonicalParameterDefinition only = registry().findById(onlyId).orElseThrow();
            DerivedCalculationSemanticCompatibility.Result compat =
                    DerivedCalculationSemanticCompatibility.assess(target, only, true);
            if (compat.compatible() && compat.strongEquivalence()) {
                Map<String, Object> opt = baseOption(
                        STATUS_READY_FOR_REVIEW,
                        "HIGH",
                        "Calculate " + target.businessName() + " as a direct reference to "
                                + only.businessName() + " (" + only.id() + ").",
                        Map.of("op", "REF", "id", only.id()),
                        primitiveDeps,
                        List.of("Target declares exactly one required primitive with strong semantic equivalence"),
                        List.of("Confirm business equivalence before production certification"),
                        List.of(),
                        List.of("requiredPrimitives", "semantic_compatibility", "unit_dimension", "temporal"),
                        true);
                opt.put("compatibility", compat.toMap());
                options.add(opt);
            }
        }

        // Option B: related DERIVED — discovery may list candidates, but REF only if strong equivalence
        List<CanonicalParameterDefinition> relatedDerived = findRelatedDerived(target);
        List<String> rejectedProxies = new ArrayList<>();
        for (CanonicalParameterDefinition rel : relatedDerived) {
            if (rel.id().equals(target.id())) continue;
            boolean relImplemented = rel.capability() != null && rel.capability().implemented();
            Optional<CiGacatDerivedCalculationDefinition> authored =
                    definitionService.latestFor(rel.id(), tenantId);
            boolean hasAuthored = authored.isPresent()
                    && !DerivedCalculationDefinitionService.STATUS_RETIRED.equals(authored.get().getStatus());
            if (!relImplemented && !hasAuthored) {
                continue;
            }
            DerivedCalculationSemanticCompatibility.Result compat =
                    DerivedCalculationSemanticCompatibility.assess(target, rel, true);
            if (!compat.compatible() || !compat.strongEquivalence()) {
                rejectedProxies.add(rel.id() + " rejected as REF: "
                        + String.join("; ", compat.failures()));
                continue;
            }
            List<Map<String, Object>> deps = List.of(
                    depView(rel, "Related derived parameter with validated semantic equivalence"));
            Map<String, Object> opt = baseOption(
                    STATUS_READY_FOR_REVIEW,
                    "HIGH",
                    "Derive " + target.businessName() + " by referencing equivalent derived parameter "
                            + rel.businessName() + ".",
                    Map.of("op", "REF", "id", rel.id()),
                    deps,
                    List.of("Semantic/dimensional equivalence validated (not mere keyword overlap)"),
                    List.of("Confirm lender intent before production certification"),
                    List.of(),
                    List.of("semantic_compatibility", "unit_dimension", "temporal", "aggregation"),
                    options.isEmpty());
            opt.put("compatibility", compat.toMap());
            options.add(opt);
            if (options.size() >= 2) break;
        }

        // Option C: no defensible executable expression → NEEDS_INPUT (never invent a proxy formula)
        if (options.isEmpty()) {
            List<String> limitations = new ArrayList<>();
            limitations.add("No formula was invented to force Policy executability");
            limitations.add("Overlapping aliases/primitives alone do not authorise READY_FOR_REVIEW");
            if (!rejectedProxies.isEmpty()) {
                limitations.addAll(rejectedProxies.stream().limit(5).toList());
            }
            List<String> missingOut = new ArrayList<>(missing);
            if (needsConfig) {
                missingOut.add("Customer-defined / vocabulary configuration for executable derivation");
            }
            if (primitiveDeps.isEmpty() && missingOut.isEmpty()) {
                missingOut.add("No exact GACAT dependencies with sufficient metadata to form a safe expression");
            }
            String explanation;
            if (needsConfig) {
                explanation = "Candidate inputs were identified from catalogue metadata, but no semantically "
                        + "compatible typed expression can be completed until configuration / vocabulary is "
                        + "confirmed. A direct REF to a related metric (for example max DPD) is not a valid "
                        + "substitute for " + target.businessName() + ".";
            } else if (primitiveDeps.isEmpty()) {
                explanation = "Unable to recommend a calculation from the currently available canonical parameters.";
            } else {
                explanation = "Unable to form a complete safe, semantically compatible expression from catalogue "
                        + "metadata alone. Edit only if you can supply a supported typed expression whose "
                        + "dependencies match the target's unit, temporal window, aggregation, and business meaning.";
            }
            Map<String, Object> opt = baseOption(
                    STATUS_NEEDS_INPUT,
                    primitiveDeps.isEmpty() ? "LOW" : "MEDIUM",
                    explanation,
                    null,
                    primitiveDeps,
                    List.of("Dependencies come from exact requiredPrimitives / catalogue metadata only",
                            "Semantic validation requires datatype, unit/dimension, temporal, aggregation, "
                                    + "and business-meaning compatibility"),
                    limitations,
                    missingOut,
                    List.of("requiredPrimitives", "missingDataTreatment", "semantic_compatibility",
                            "unit_dimension", "temporal", "aggregation"),
                    true);
            options.add(opt);
        }

        return options;
    }

    private List<CanonicalParameterDefinition> findRelatedDerived(CanonicalParameterDefinition target) {
        Set<String> targetPrims = new LinkedHashSet<>(
                target.requiredPrimitives() == null ? List.of() : target.requiredPrimitives());
        Set<String> targetAliases = new LinkedHashSet<>();
        if (target.aliases() != null) {
            for (String a : target.aliases()) {
                if (a != null) targetAliases.add(a.toLowerCase(Locale.ROOT));
            }
        }
        String family = target.evaluatedFrom() == null ? "" : target.evaluatedFrom();
        List<CanonicalParameterDefinition> scored = new ArrayList<>();
        for (CanonicalParameterDefinition d : registry().all()) {
            if (d == null || d.id() == null || d.id().equals(target.id())) continue;
            if (!CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(d.type())) continue;
            if (family.isBlank() || d.evaluatedFrom() == null
                    || !family.equalsIgnoreCase(d.evaluatedFrom())) {
                continue;
            }
            boolean primOverlap = false;
            if (d.requiredPrimitives() != null) {
                for (String p : d.requiredPrimitives()) {
                    if (targetPrims.contains(p)) {
                        primOverlap = true;
                        break;
                    }
                }
            }
            boolean aliasOverlap = false;
            if (d.aliases() != null) {
                for (String a : d.aliases()) {
                    if (a != null && targetAliases.contains(a.toLowerCase(Locale.ROOT))) {
                        aliasOverlap = true;
                        break;
                    }
                }
            }
            String name = (d.businessName() == null ? "" : d.businessName()).toLowerCase(Locale.ROOT);
            String tname = (target.businessName() == null ? "" : target.businessName()).toLowerCase(Locale.ROOT);
            boolean nameHint = !tname.isBlank() && name.contains(
                    tname.split("\\s+")[0]);
            if (primOverlap || aliasOverlap || nameHint) {
                scored.add(d);
            }
        }
        return scored;
    }

    private static boolean typesCompatible(CanonicalParameterDefinition target, CanonicalParameterDefinition dep) {
        return DerivedCalculationSemanticCompatibility.assess(target, dep, false).compatible();
    }

    private void assertExpressionSemanticallyCompatible(
            String targetParameterId, Map<String, Object> expression) {
        CanonicalParameterDefinition target = registry().findById(targetParameterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown GACAT parameter: " + targetParameterId));
        DerivedCalculationSemanticCompatibility.Result compat =
                DerivedCalculationSemanticCompatibility.assessExpression(
                        target,
                        expression,
                        id -> registry().findById(id).orElse(null));
        if (!compat.compatible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Semantic/dimensional incompatibility — proposal cannot be approved: "
                            + String.join("; ", compat.failures()));
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> depView(CanonicalParameterDefinition d, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameterId", d.id());
        m.put("displayName", d.businessName());
        m.put("source", d.evaluatedFrom());
        m.put("parameterKind", d.type());
        m.put("dataType", d.unit());
        boolean impl = d.capability() != null && d.capability().implemented();
        boolean prod = d.capability() != null && d.capability().productionReady();
        m.put("readiness", prod ? "PRODUCTION_READY" : (impl ? "IMPLEMENTED" : "CATALOGUE_ONLY"));
        m.put("reasonSelected", reason);
        return m;
    }

    private static Map<String, Object> baseOption(
            String status,
            String confidence,
            String explanation,
            Map<String, Object> expression,
            List<Map<String, Object>> deps,
            List<String> assumptions,
            List<String> limitations,
            List<String> missing,
            List<String> evidence,
            boolean recommended) {
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("proposalStatus", status);
        opt.put("confidence", confidence);
        opt.put("humanExplanation", explanation);
        opt.put("proposedExpression", expression);
        opt.put("candidateDependencies", deps);
        opt.put("assumptions", assumptions);
        opt.put("limitations", limitations);
        opt.put("missingDependencies", missing);
        opt.put("evidence", evidence);
        opt.put("recommended", recommended);
        return opt;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castDepList(List<?> l) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> updateProposalExpression(
            UUID proposalId, Map<String, Object> expression, String actor) {
        CiGacatDerivedCalculationProposal row = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found"));
        if (STATUS_APPROVED.equals(row.getProposalStatus()) || STATUS_REJECTED.equals(row.getProposalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal is closed");
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (CanonicalParameterDefinition d : registry().all()) {
            allowed.add(d.id());
        }
        List<String> errors = SafeDerivedExpressionEvaluator.validate(expression, allowed);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
        assertExpressionSemanticallyCompatible(row.getTargetParameterId(), expression);
        row.setProposedExpression(expression);
        row.setProposalStatus(STATUS_READY_FOR_REVIEW);
        row.setUpdatedAt(Instant.now());
        Map<String, Object> meta = row.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(row.getMetadata());
        meta.put("editedBy", actor);
        meta.put("editedAt", Instant.now().toString());
        row.setMetadata(meta);
        return toView(proposalRepository.save(row));
    }

    @Transactional
    public Map<String, Object> accept(UUID proposalId, String actor) {
        CiGacatDerivedCalculationProposal row = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found"));
        if (STATUS_APPROVED.equals(row.getProposalStatus())) {
            Map<String, Object> v = toView(row);
            v.put("alreadyApproved", true);
            return v;
        }
        if (STATUS_REJECTED.equals(row.getProposalStatus()) || STATUS_SUPERSEDED.equals(row.getProposalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal cannot be accepted: " + row.getProposalStatus());
        }
        Map<String, Object> expr = row.getProposedExpression();
        if (expr == null || expr.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal has no proposedExpression — Edit calculation before Accept");
        }
        assertExpressionSemanticallyCompatible(row.getTargetParameterId(), expr);
        // Persist via existing V139 authoring path (exact GACAT target, safe validate, cycle check)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("canonicalParameterId", row.getTargetParameterId());
        body.put("scope", row.getScope());
        body.put("expression", expr);
        body.put("resultType", inferResultType(row.getTargetParameterId()));
        body.put("description", row.getHumanExplanation());
        Map<String, Object> def = definitionService.saveDraft(body, row.getTenantId(), actor);

        row.setProposalStatus(STATUS_APPROVED);
        row.setApprovedBy(actor);
        row.setApprovedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        Object idObj = def.get("id");
        if (idObj instanceof UUID u) {
            row.setResultingDefinitionId(u);
        } else if (idObj != null) {
            row.setResultingDefinitionId(UUID.fromString(String.valueOf(idObj)));
        }
        proposalRepository.save(row);

        Map<String, Object> out = toView(row);
        out.put("definition", def);
        out.put("duplicateParameterCreated", false);
        out.put("targetCanonicalParameterReused", true);
        return out;
    }

    @Transactional
    public Map<String, Object> reject(UUID proposalId, String actor) {
        CiGacatDerivedCalculationProposal row = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found"));
        if (STATUS_APPROVED.equals(row.getProposalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot reject an approved proposal");
        }
        row.setProposalStatus(STATUS_REJECTED);
        row.setRejectedBy(actor);
        row.setRejectedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        return toView(proposalRepository.save(row));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProposal(UUID id) {
        return toView(proposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForTarget(String targetParameterId) {
        return proposalRepository.findByTargetParameterIdOrderByCreatedAtDesc(targetParameterId).stream()
                .map(this::toView)
                .toList();
    }

    private String inferResultType(String canonicalId) {
        return registry().findById(canonicalId)
                .map(d -> d.unit() == null || d.unit().isBlank() ? "NUMBER" : d.unit())
                .orElse("NUMBER");
    }

    public Map<String, Object> toView(CiGacatDerivedCalculationProposal row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("targetParameterId", row.getTargetParameterId());
        m.put("targetParameterName", row.getTargetParameterName());
        m.put("scope", row.getScope());
        m.put("proposalStatus", row.getProposalStatus());
        m.put("confidence", row.getConfidence());
        m.put("humanExplanation", row.getHumanExplanation());
        m.put("proposedExpression", row.getProposedExpression());
        m.put("proposedCalculationSpec", row.getProposedExpression());
        m.put("candidateDependencies", row.getCandidateDependencies());
        m.put("assumptions", row.getAssumptions());
        m.put("limitations", row.getLimitations());
        m.put("missingDependencies", row.getMissingDependencies());
        m.put("evidence", row.getEvidence());
        m.put("optionIndex", row.getOptionIndex());
        m.put("recommended", row.getRecommended());
        m.put("resultingDefinitionId", row.getResultingDefinitionId());
        m.put("arbitraryCodeAllowed", false);
        m.put("advisoryOnly", true);
        return m;
    }
}
