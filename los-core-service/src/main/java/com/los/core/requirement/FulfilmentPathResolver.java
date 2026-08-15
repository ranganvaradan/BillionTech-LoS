package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.service.readiness.GacatParameterReadinessProjection;
import com.los.core.service.readiness.WorkflowParameterProvidesCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic fulfilment-path planner for one Policy canonical parameter.
 * Plans only — never executes providers, OCR, KYC, Policy, or Scorecard.
 */
@Component
public class FulfilmentPathResolver {

    public record Resolution(
            RequirementClass requirementClass,
            FulfilmentMode preferredMode,
            List<FulfilmentMode> allowedModes,
            List<FulfilmentMode> otherAvailableModes,
            CustomerFulfilmentState customerFulfilmentState,
            DataReadinessState dataReadinessState,
            SourceAcquisitionState sourceAcquisitionState,
            String documentGroup,
            String whyRequired,
            String whyChosen,
            String blockingReason,
            String productionReadiness,
            List<Map<String, Object>> alternativeSources,
            Map<String, Object> sourceHints,
            Map<String, Object> provenance,
            Map<String, Object> explanation
    ) {}

    public Resolution resolve(
            PolicyParameterRequirement req,
            Optional<ExistingCanonicalFact> existingFact,
            Map<String, Object> inventoryHints) {

        Map<String, Object> hints = inventoryHints != null ? inventoryHints : Map.of();
        Map<String, Object> sourceHints = new LinkedHashMap<>();
        Map<String, Object> provenance = new LinkedHashMap<>();
        Map<String, Object> explanation = new LinkedHashMap<>();
        List<Map<String, Object>> alternatives = new ArrayList<>();

        explanation.put("canonicalParameterId", req.canonicalParameterId());
        explanation.put("requiredByPolicyRules", req.ruleReferences());
        explanation.put("whyRequired", "Referenced by Policy rule graph operands");
        explanation.put("usageTypes", req.usageTypes());

        if (req.unresolved()) {
            String reason = "UNRESOLVED_CANONICAL_PARAMETER";
            explanation.put("blockingReason", reason);
            explanation.put("whyChosen", "Fail-closed: DP-3 unresolved operand cannot be fuzzy-mapped");
            sourceHints.put("blockingReason", reason);
            sourceHints.put("originalToken", req.originalToken());
            provenance.put("resolutionStatus", req.resolutionStatus());
            return new Resolution(
                    RequirementClass.UNAVAILABLE_BLOCKER,
                    FulfilmentMode.MANUAL_REVIEW,
                    List.of(FulfilmentMode.MANUAL_REVIEW),
                    List.of(),
                    CustomerFulfilmentState.REQUIRED,
                    DataReadinessState.FAILED,
                    SourceAcquisitionState.UNAVAILABLE,
                    null,
                    "Unresolved Policy operand — exact GACAT ID required",
                    "Fail-closed on unresolved operand",
                    reason,
                    req.productionReadiness(),
                    List.of(),
                    sourceHints,
                    provenance,
                    explanation);
        }

        String id = req.canonicalParameterId();
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        Optional<CanonicalParameterDefinition> defOpt = registry.findById(id);
        Map<String, Object> projection = defOpt
                .map(GacatParameterReadinessProjection::project)
                .orElseGet(LinkedHashMap::new);

        String productionReadiness = projection.get("overallReadiness") != null
                ? String.valueOf(projection.get("overallReadiness"))
                : req.productionReadiness();
        explanation.put("productionReadiness", productionReadiness);
        sourceHints.put("productionReadiness", productionReadiness);
        if (projection.get("sourceType") != null) {
            sourceHints.put("sourceType", projection.get("sourceType"));
        }
        if (projection.get("provider") != null) {
            sourceHints.put("provider", projection.get("provider"));
        }
        if (projection.get("workflow") != null) {
            sourceHints.put("workflowProvides", projection.get("workflow"));
        }

        // A. EXISTING CANONICAL FACT
        if (existingFact.isPresent() && existingFact.get().readyForPolicy()) {
            ExistingCanonicalFact fact = existingFact.get();
            provenance.putAll(fact.provenance());
            provenance.put("existingFactPreferred", true);
            explanation.put("chosenFulfilmentMode", "EXISTING_CANONICAL_FACT");
            explanation.put("whyChosen", "Valid canonical fact already present — no customer request");
            explanation.put("currentCustomerFulfilment", CustomerFulfilmentState.NOT_APPLICABLE.name());
            explanation.put("currentDataReadiness", DataReadinessState.READY_FOR_POLICY.name());
            explanation.put("currentSourceState", SourceAcquisitionState.SUCCEEDED.name());
            return new Resolution(
                    RequirementClass.ALREADY_AVAILABLE,
                    null,
                    List.of(),
                    List.of(),
                    CustomerFulfilmentState.NOT_APPLICABLE,
                    DataReadinessState.READY_FOR_POLICY,
                    SourceAcquisitionState.SUCCEEDED,
                    null,
                    "Policy requires " + id,
                    "Existing canonical fact acceptable for Policy",
                    null,
                    productionReadiness,
                    List.of(),
                    sourceHints,
                    provenance,
                    explanation);
        }

        PathAssessment paths = assessPaths(id, defOpt.orElse(null), projection, hints);

        // Prefer order: DERIVATION → AUTOMATIC → DOCUMENT → DIRECT → none
        FulfilmentMode preferred = null;
        String whyChosen = null;
        RequirementClass clazz = RequirementClass.UNAVAILABLE_BLOCKER;
        CustomerFulfilmentState fulfilment = CustomerFulfilmentState.REQUIRED;
        DataReadinessState readiness = DataReadinessState.NOT_AVAILABLE;
        SourceAcquisitionState sourceState = SourceAcquisitionState.NOT_STARTED;
        String documentGroup = null;
        String blockingReason = null;

        if (paths.derivation) {
            preferred = FulfilmentMode.DERIVATION;
            clazz = RequirementClass.DERIVABLE;
            fulfilment = CustomerFulfilmentState.NOT_APPLICABLE;
            readiness = DataReadinessState.NOT_AVAILABLE; // calculator exists ≠ READY
            whyChosen = "Proven derivation/calculator metadata — plan DERIVATION (not executed in W4)";
            sourceHints.put("calculatorAvailable", true);
            if (defOpt.isPresent() && defOpt.get().existingImplementationBinding() != null) {
                sourceHints.put("calculatorBinding", defOpt.get().existingImplementationBinding());
            }
        } else if (paths.automatic) {
            preferred = FulfilmentMode.AUTOMATIC_SOURCE;
            clazz = RequirementClass.AUTO_SOURCE;
            fulfilment = CustomerFulfilmentState.NOT_APPLICABLE;
            readiness = DataReadinessState.NOT_AVAILABLE;
            whyChosen = "Proven automatic source/provider path from GACAT/workflow metadata";
            sourceState = SourceAcquisitionState.NOT_STARTED;
            if (paths.automaticProvider != null) {
                sourceHints.put("providerLabel", paths.automaticProvider);
            }
            if (paths.automaticStep != null) {
                sourceHints.put("workflowStep", paths.automaticStep);
            }
        } else if (paths.document) {
            preferred = FulfilmentMode.DOCUMENT_UPLOAD;
            clazz = RequirementClass.CUSTOMER_PROVIDED;
            fulfilment = CustomerFulfilmentState.REQUESTED;
            readiness = DataReadinessState.NOT_AVAILABLE;
            documentGroup = paths.documentGroup;
            whyChosen = "Document-first fallback — customer upload preferred over direct declaration";
            sourceHints.put("pendingDocumentGroup", documentGroup);
            sourceHints.put("documentRequirement", documentGroup);
        } else if (paths.direct) {
            preferred = FulfilmentMode.DIRECT_INPUT;
            clazz = RequirementClass.CUSTOMER_PROVIDED;
            fulfilment = CustomerFulfilmentState.REQUESTED;
            readiness = DataReadinessState.NOT_AVAILABLE;
            whyChosen = "Direct customer input permitted by GACAT/Workflow configuration";
        } else {
            preferred = FulfilmentMode.MANUAL_REVIEW;
            clazz = RequirementClass.UNAVAILABLE_BLOCKER;
            fulfilment = CustomerFulfilmentState.REQUIRED;
            readiness = DataReadinessState.FAILED;
            sourceState = SourceAcquisitionState.UNAVAILABLE;
            blockingReason = "NO_FULFILMENT_PATH";
            whyChosen = "No proven fact, derivation, automatic source, document route, or permitted direct input";
        }

        List<FulfilmentMode> allowed = new ArrayList<>();
        if (paths.derivation) allowed.add(FulfilmentMode.DERIVATION);
        if (paths.automatic) allowed.add(FulfilmentMode.AUTOMATIC_SOURCE);
        if (paths.document) allowed.add(FulfilmentMode.DOCUMENT_UPLOAD);
        if (paths.direct) allowed.add(FulfilmentMode.DIRECT_INPUT);
        if (allowed.isEmpty()) {
            allowed.add(FulfilmentMode.MANUAL_REVIEW);
        }

        // Bureau / automatic-only: never add DIRECT_INPUT or CUSTOMER_FALLBACK
        if (preferred == FulfilmentMode.AUTOMATIC_SOURCE && paths.automaticOnly) {
            allowed = new ArrayList<>(List.of(FulfilmentMode.AUTOMATIC_SOURCE));
            if (paths.documentAsConfiguredAlternative) {
                allowed.add(FulfilmentMode.DOCUMENT_UPLOAD);
            }
        }

        // When preferred is document but direct also allowed (item C)
        if (preferred == FulfilmentMode.DOCUMENT_UPLOAD && paths.direct && paths.allowBothCustomerModes) {
            if (!allowed.contains(FulfilmentMode.DIRECT_INPUT)) {
                allowed.add(FulfilmentMode.DIRECT_INPUT);
            }
            whyChosen = "Document upload preferred; direct input also permitted";
        }
        // When preferred is direct but document also allowed without forcing document-first
        if (preferred == FulfilmentMode.DIRECT_INPUT && paths.document && paths.allowBothCustomerModes) {
            if (!allowed.contains(FulfilmentMode.DOCUMENT_UPLOAD)) {
                allowed.add(FulfilmentMode.DOCUMENT_UPLOAD);
            }
        }

        List<FulfilmentMode> other = new ArrayList<>();
        for (FulfilmentMode m : allowed) {
            if (m != preferred) {
                other.add(m);
            }
        }

        // Alternatives (e.g. AA primary + bank statement document)
        if (paths.automatic && paths.documentAsConfiguredAlternative) {
            Map<String, Object> alt = new LinkedHashMap<>();
            alt.put("mode", FulfilmentMode.DOCUMENT_UPLOAD.name());
            alt.put("documentGroup", paths.documentGroup != null ? paths.documentGroup : "BANK_STATEMENT");
            alt.put("reason", "Configured document alternative — not executed as fallback in W4");
            alternatives.add(alt);
            sourceHints.put("alternativeDocumentGroup", alt.get("documentGroup"));
        }
        if (paths.derivation && paths.automatic) {
            Map<String, Object> alt = new LinkedHashMap<>();
            alt.put("mode", FulfilmentMode.AUTOMATIC_SOURCE.name());
            alt.put("reason", "Automatic source also proven; derivation preferred when calculator exists");
            alternatives.add(alt);
        }

        explanation.put("chosenFulfilmentMode", preferred != null ? preferred.name() : null);
        explanation.put("whyChosen", whyChosen);
        explanation.put("otherAvailableModes", other.stream().map(Enum::name).toList());
        explanation.put("allowedModes", allowed.stream().map(Enum::name).toList());
        explanation.put("documentRequirement", documentGroup);
        explanation.put("blockingReason", blockingReason);
        explanation.put("currentCustomerFulfilment", fulfilment.name());
        explanation.put("currentDataReadiness", readiness.name());
        explanation.put("currentSourceState", sourceState.name());
        explanation.put("alternativeSources", alternatives);
        if (paths.automaticProvider != null) {
            explanation.put("sourceProvider", paths.automaticProvider);
        }

        sourceHints.put("preferredMode", preferred != null ? preferred.name() : null);
        sourceHints.put("allowedModes", allowed.stream().map(Enum::name).toList());
        sourceHints.put("whyChosen", whyChosen);
        if (blockingReason != null) {
            sourceHints.put("blockingReason", blockingReason);
        }
        provenance.put("plannedBy", "W4_DataRequirementPlanner");
        provenance.put("policyRuleRefs", req.ruleReferences());

        return new Resolution(
                clazz,
                preferred,
                List.copyOf(allowed),
                List.copyOf(other),
                fulfilment,
                readiness,
                sourceState,
                documentGroup,
                "Policy requires " + id,
                whyChosen,
                blockingReason,
                productionReadiness,
                List.copyOf(alternatives),
                sourceHints,
                provenance,
                explanation);
    }

    private PathAssessment assessPaths(
            String id,
            CanonicalParameterDefinition def,
            Map<String, Object> projection,
            Map<String, Object> hints) {

        PathAssessment a = new PathAssessment();
        Map<String, Object> workflow = projection.get("workflow") instanceof Map<?, ?> w
                ? castMap(w) : WorkflowParameterProvidesCatalog.lookupForParameter(id);
        String sourceType = projection.get("sourceType") != null
                ? String.valueOf(projection.get("sourceType")) : "";

        boolean calculatorAvailable = Boolean.TRUE.equals(projection.get("calculatorAvailable"));
        boolean derivationDefined = def != null && def.capability() != null && def.capability().derivationDefined();
        boolean derivedType = def != null && CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(def.type());
        Set<String> forceDerivation = stringSet(hints.get("derivationParameterIds"));
        a.derivation = forceDerivation.contains(id)
                || ((derivedType || derivationDefined) && calculatorAvailable
                && !Boolean.FALSE.equals(hintBool(hints, "enableDerivation", true)));

        // Automatic source — proven workflow step / integration / provider source type only
        @SuppressWarnings("unchecked")
        List<String> productionSteps = workflow.get("productionSteps") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        @SuppressWarnings("unchecked")
        List<String> integrations = workflow.get("integrations") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        boolean workflowAuto = !productionSteps.isEmpty()
                || Boolean.TRUE.equals(workflow.get("integrationAvailable"));
        boolean providerSource = GacatParameterReadinessProjection.SOURCE_PROVIDER.equals(sourceType);
        boolean runtimeOrProd = Boolean.TRUE.equals(projection.get("runtimeReady"))
                || Boolean.TRUE.equals(projection.get("productionReady"))
                || Boolean.TRUE.equals(projection.get("sourceAvailable"));

        // Explicit force-automatic from hints (tests / staging overlays on proven ids)
        Set<String> forceAuto = stringSet(hints.get("automaticSourceParameterIds"));
        boolean forcedAuto = forceAuto.contains(id);

        // Prefer automatic over derivation when both forced for acquisition candidates
        if (forcedAuto && forceDerivation.contains(id)) {
            a.derivation = false;
        }

        a.automatic = forcedAuto
                || ((providerSource || workflowAuto) && runtimeOrProd && hasProvenAutoSignal(productionSteps, integrations, projection, def));
        if (a.automatic) {
            if (!productionSteps.isEmpty()) {
                a.automaticStep = productionSteps.get(0);
                a.automaticProvider = a.automaticStep;
            } else if (!integrations.isEmpty()) {
                a.automaticStep = integrations.get(0);
                a.automaticProvider = a.automaticStep;
            } else if (projection.get("provider") instanceof Map<?, ?> p && p.get("label") != null) {
                a.automaticProvider = String.valueOf(p.get("label"));
            }
            a.automaticOnly = isAutomaticOnlyFamily(id, def, productionSteps);
        }
        if (a.automatic && a.derivation && !forceDerivation.contains(id)) {
            // Keep derivation preferred when calculator exists unless auto-only family
            if (a.automaticOnly || isBureauFamily(id, def)) {
                a.derivation = false;
            }
        }

        // Document path
        String docGroup = resolveDocumentGroup(id, def, projection, hints, integrations);
        boolean workflowDocs = stringSet(hints.get("workflowDocumentRequirements")).contains(docGroup)
                || stringSet(hints.get("workflowDocumentRequirements")).stream()
                .anyMatch(d -> d.equalsIgnoreCase(docGroup));
        // Also accept FINANCIAL_STATEMENTS in workflow list when param is financial.* even if docGroup resolved later
        if (!workflowDocs && isFinancialFamily(id, def)) {
            workflowDocs = stringSet(hints.get("workflowDocumentRequirements")).contains("FINANCIAL_STATEMENTS");
        }
        boolean mappedDoc = documentMapping(hints).containsKey(id);
        a.document = mappedDoc
                || (docGroup != null && workflowDocs)
                || (isFinancialFamily(id, def) && workflowDocs)
                || (isBankStatementFamily(id, def, integrations) && workflowDocs);
        if (mappedDoc) {
            a.document = true;
            a.documentGroup = documentMapping(hints).get(id);
        } else if (a.document) {
            a.documentGroup = docGroup != null ? docGroup
                    : (isFinancialFamily(id, def) ? "FINANCIAL_STATEMENTS" : null);
        }

        // Document alternative for AA banking (retain, do not execute)
        if (a.automatic && integrations.contains("ACCOUNT_AGGREGATOR")
                && (integrations.contains("BANK_STATEMENT_DOCUMENT") || workflowDocs
                || stringSet(hints.get("workflowDocumentRequirements")).contains("BANK_STATEMENT")
                || stringSet(hints.get("workflowDocumentRequirements")).contains("BANK_STATEMENT_DOCUMENT"))) {
            a.documentAsConfiguredAlternative = Boolean.TRUE.equals(hintBool(hints, "allowDocumentAlternativeForAutomatic", true));
            if (a.documentGroup == null) {
                a.documentGroup = "BANK_STATEMENT";
            }
        }

        // Explicit document alternative config
        if (stringSet(hints.get("documentAlternativeParameterIds")).contains(id)) {
            a.documentAsConfiguredAlternative = true;
            if (a.documentGroup == null) {
                a.documentGroup = "BANK_STATEMENT";
            }
        }

        // Document-first for financial statements when Workflow supports upload and
        // there is no proven production workflow/integration step (catalogue-only ≠ execute).
        if (a.document && a.automatic && isFinancialFamily(id, def)
                && productionSteps.isEmpty()
                && !forcedAuto) {
            a.automatic = false;
        }

        // Direct input — MANUAL / APPLICATION_INPUT or explicit allow list; never arbitrary financial/bureau
        Set<String> allowDirect = stringSet(hints.get("allowDirectInputParameterIds"));
        Set<String> denyDirect = stringSet(hints.get("denyDirectInputParameterIds"));
        boolean manualType = def != null && CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type());
        boolean applicationInput = GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT.equals(sourceType)
                || GacatParameterReadinessProjection.SOURCE_MANUAL.equals(sourceType);
        boolean familyAllows = def != null && def.evaluatedFrom() != null
                && (def.evaluatedFrom().toLowerCase(Locale.ROOT).contains("application")
                || def.evaluatedFrom().toLowerCase(Locale.ROOT).contains("manual")
                || def.evaluatedFrom().toLowerCase(Locale.ROOT).contains("program")
                || def.evaluatedFrom().toLowerCase(Locale.ROOT).contains("product"));

        a.direct = !denyDirect.contains(id)
                && !a.automaticOnly
                && (allowDirect.contains(id) || ((manualType || applicationInput || familyAllows)
                && !isFinancialFamily(id, def)
                && !isBureauFamily(id, def)
                && !isStrictProviderFamily(id, def)));

        // Item C style: both modes when explicitly configured
        if (allowDirect.contains(id) && a.document) {
            a.allowBothCustomerModes = true;
        }
        if (stringSet(hints.get("dualModeParameterIds")).contains(id)) {
            a.allowBothCustomerModes = true;
            a.direct = true;
            if (!a.document && a.documentGroup == null) {
                // dual mode without document group still allows direct; document if group known
            }
        }

        // Document-first: if both document and direct, prefer document unless dual-mode says both
        // (preferred selection happens in resolve())
        if (a.document && a.direct && !a.allowBothCustomerModes && isFinancialFamily(id, def)) {
            // keep both in allowed only if lender explicitly allows; default document-only for financials
            if (!allowDirect.contains(id)) {
                a.direct = false;
            } else {
                a.allowBothCustomerModes = true;
            }
        }

        // Never invent automatic when only name looks like a provider
        if (a.automatic && !forcedAuto && !hasProvenAutoSignal(productionSteps, integrations, projection, def)) {
            a.automatic = false;
        }

        return a;
    }

    private static boolean hasProvenAutoSignal(
            List<String> productionSteps,
            List<String> integrations,
            Map<String, Object> projection,
            CanonicalParameterDefinition def) {
        if (!productionSteps.isEmpty()) return true;
        if (!integrations.isEmpty()) return true;
        if (projection.get("provider") instanceof Map<?, ?> p
                && GacatParameterReadinessProjection.PROVIDER_INFERRED.equals(String.valueOf(p.get("status")))
                && p.get("label") != null) {
            return true;
        }
        // capability providerFieldPath is proven bureau/GST path
        if (def != null && def.capability() != null) {
            String path = def.capability().providerFieldPath();
            if (path != null && !path.isBlank()) return true;
            String schema = def.capability().schema();
            if (schema != null) {
                String s = schema.toUpperCase(Locale.ROOT);
                if (s.contains("BUREAU") || s.contains("EQUIFAX") || s.contains("GST")
                        || s.contains("ACCOUNT_AGGREGATOR")) {
                    return Boolean.TRUE.equals(projection.get("sourceAvailable"))
                            || Boolean.TRUE.equals(projection.get("runtimeReady"))
                            || Boolean.TRUE.equals(projection.get("productionReady"));
                }
            }
        }
        return false;
    }

    private static String resolveDocumentGroup(
            String id,
            CanonicalParameterDefinition def,
            Map<String, Object> projection,
            Map<String, Object> hints,
            List<String> integrations) {
        Map<String, String> mapped = documentMapping(hints);
        if (mapped.containsKey(id)) {
            return mapped.get(id);
        }
        if (def != null && def.capability() != null && def.capability().schema() != null) {
            String schema = def.capability().schema().toUpperCase(Locale.ROOT);
            if (schema.contains("FINANCIAL")) return "FINANCIAL_STATEMENTS";
            if (schema.contains("BANK")) return "BANK_STATEMENT";
            if (schema.contains("ITR")) return "ITR_RETURN";
            if (schema.contains("GST")) return "GST_RETURN";
        }
        if (isFinancialFamily(id, def)) return "FINANCIAL_STATEMENTS";
        if (id.startsWith("banking.") || id.startsWith("bank.")) {
            if (integrations.contains("BANK_STATEMENT_DOCUMENT") || !integrations.contains("ACCOUNT_AGGREGATOR")) {
                return "BANK_STATEMENT";
            }
        }
        return null;
    }

    private static boolean isFinancialFamily(String id, CanonicalParameterDefinition def) {
        if (id != null && id.startsWith("financial.")) return true;
        if (def != null && def.evaluatedFrom() != null) {
            String e = def.evaluatedFrom().toLowerCase(Locale.ROOT);
            return e.contains("financial") || e.contains("itr");
        }
        return false;
    }

    private static boolean isBankStatementFamily(String id, CanonicalParameterDefinition def, List<String> integrations) {
        if (id != null && (id.startsWith("banking.") || id.startsWith("bank."))) return true;
        return integrations.contains("BANK_STATEMENT_DOCUMENT");
    }

    private static boolean isBureauFamily(String id, CanonicalParameterDefinition def) {
        if (id != null && id.startsWith("bureau.")) return true;
        if (def != null && def.evaluatedFrom() != null) {
            return def.evaluatedFrom().toLowerCase(Locale.ROOT).contains("bureau");
        }
        return false;
    }

    private static boolean isStrictProviderFamily(String id, CanonicalParameterDefinition def) {
        if (id == null) return false;
        return id.startsWith("gst.") || id.startsWith("bureau.") || id.startsWith("banking.");
    }

    private static boolean isAutomaticOnlyFamily(String id, CanonicalParameterDefinition def, List<String> steps) {
        if (isBureauFamily(id, def)) return true;
        if (steps.contains("BUREAU_PULL") && id != null && id.startsWith("bureau.")) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> documentMapping(Map<String, Object> hints) {
        Map<String, String> out = new LinkedHashMap<>();
        Object raw = hints.get("documentMappings");
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(String.valueOf(k), String.valueOf(v));
                }
            });
        }
        return out;
    }

    private static Set<String> stringSet(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        }
        return out;
    }

    private static Boolean hintBool(Map<String, Object> hints, String key, boolean defaultValue) {
        if (!hints.containsKey(key)) return defaultValue;
        Object v = hints.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(String.valueOf(v));
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> w) {
        Map<String, Object> m = new LinkedHashMap<>();
        w.forEach((k, v) -> m.put(String.valueOf(k), v));
        return m;
    }

    private static final class PathAssessment {
        boolean derivation;
        boolean automatic;
        boolean automaticOnly;
        boolean document;
        boolean documentAsConfiguredAlternative;
        boolean direct;
        boolean allowBothCustomerModes;
        String documentGroup;
        String automaticProvider;
        String automaticStep;
    }
}
