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
        Map<String, Object> primary = savedViews.get(0);
        primary.put("options", savedViews);
        primary.put("unableToRecommend", false);
        primary.put("primaryProposalId", primaryId);
        return primary;
    }

    /**
     * Generic catalogue-driven options. No parameter-id hardcoding.
     * Semantic similarity only discovers candidates; every dependency is an exact GACAT id.
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
                || missingTreatment.toUpperCase(Locale.ROOT).contains("VOCABULARY");

        // Option A: single exact primitive REF when types align and no config gate
        if (primitiveDeps.size() == 1 && missing.isEmpty() && !needsConfig) {
            String onlyId = String.valueOf(primitiveDeps.get(0).get("parameterId"));
            CanonicalParameterDefinition only = registry().findById(onlyId).orElseThrow();
            if (typesCompatible(target, only)) {
                Map<String, Object> opt = baseOption(
                        STATUS_READY_FOR_REVIEW,
                        "HIGH",
                        "Calculate " + target.businessName() + " as a direct reference to "
                                + only.businessName() + " (" + only.id() + ").",
                        Map.of("op", "REF", "id", only.id()),
                        primitiveDeps,
                        List.of("Target declares exactly one required primitive of compatible type"),
                        List.of("Confirm business equivalence before production certification"),
                        List.of(),
                        List.of("requiredPrimitives", "type_compatibility"),
                        true);
                options.add(opt);
            }
        }

        // Option B: related DERIVED already executable / defined that shares primitives or aliases
        List<CanonicalParameterDefinition> relatedDerived = findRelatedDerived(target);
        for (CanonicalParameterDefinition rel : relatedDerived) {
            if (rel.id().equals(target.id())) continue;
            boolean relImplemented = rel.capability() != null && rel.capability().implemented();
            Optional<CiGacatDerivedCalculationDefinition> authored =
                    definitionService.latestFor(rel.id(), tenantId);
            boolean hasAuthored = authored.isPresent()
                    && !DerivedCalculationDefinitionService.STATUS_RETIRED.equals(authored.get().getStatus());
            if (!relImplemented && !hasAuthored) {
                // Still list as discovery candidate dependency only when overlapping primitives
                continue;
            }
            List<Map<String, Object>> deps = List.of(
                    depView(rel, "Related derived parameter with overlapping catalogue semantics"));
            String conf = relImplemented ? "MEDIUM" : "LOW";
            List<String> limitations = new ArrayList<>();
            limitations.add("Proxy / related derived — confirm it matches the lender's intended meaning");
            if (!rel.id().equals(target.id())) {
                limitations.add("Does not rename or replace the target canonical id; attaches a REF expression only");
            }
            Map<String, Object> opt = baseOption(
                    STATUS_READY_FOR_REVIEW,
                    conf,
                    "Derive " + target.businessName() + " by referencing related derived parameter "
                            + rel.businessName() + ".",
                    Map.of("op", "REF", "id", rel.id()),
                    deps,
                    List.of("Catalogue synonym / primitive overlap discovery"),
                    limitations,
                    List.of(),
                    List.of("source_family", "alias_overlap", "requiredPrimitives_overlap"),
                    options.isEmpty());
            options.add(opt);
            if (options.size() >= 2) break;
        }

        // Option C: primitives known but vocabulary/config incomplete → NEEDS_INPUT (no fabricated formula)
        if (options.isEmpty() && (!primitiveDeps.isEmpty() || !missing.isEmpty() || needsConfig)) {
            Map<String, Object> opt = baseOption(
                    STATUS_NEEDS_INPUT,
                    primitiveDeps.isEmpty() ? "LOW" : "MEDIUM",
                    needsConfig
                            ? "Candidate inputs were identified from catalogue metadata, but an executable "
                            + "typed expression cannot be completed until configuration / vocabulary is confirmed. "
                            + "Edit the proposal to supply a safe expression over the listed dependencies, "
                            + "or reject if the business concept is not yet definable."
                            : "Unable to form a complete safe expression from catalogue metadata alone. "
                            + "Edit to supply a supported typed expression over exact GACAT dependencies.",
                    null,
                    primitiveDeps,
                    List.of("Dependencies come from exact requiredPrimitives / catalogue metadata only"),
                    List.of("No formula was invented to force Policy executability"),
                    missing,
                    List.of("requiredPrimitives", "missingDataTreatment", "calculationSummary"),
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
        String tu = norm(target.unit());
        String du = norm(dep.unit());
        if (!tu.isBlank() && !du.isBlank() && tu.equals(du)) return true;
        // MONTHS / NUMBER soft compatibility when units missing on one side
        if (tu.isBlank() || du.isBlank()) return true;
        return tu.equals(du);
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
