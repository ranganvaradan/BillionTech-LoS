package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * DATA-PARAMETERS-CAPABILITY-SEMANTICS-CLEANUP-1 — lender-facing SETUP capability model.
 * <p>
 * Read-only projection over GACAT + Gate3 + workflow provides. Does not mutate catalogue
 * {@code production_ready} flags. Does not represent application-specific value availability.
 */
public final class DataParametersCapabilitySemantics {

    public static final String SOURCE_PLATFORM_PRODUCTION_READY = "PRODUCTION_READY";
    public static final String SOURCE_PLATFORM_NOT_INTEGRATED = "NOT_INTEGRATED";
    public static final String SOURCE_PLATFORM_NOT_APPLICABLE = "NOT_APPLICABLE";

    public static final String SUPPORT_SUPPORTED_RAW = "SUPPORTED_RAW";
    public static final String SUPPORT_SUPPORTED_DERIVED = "SUPPORTED_DERIVED";
    public static final String SUPPORT_PROVIDER_DOES_NOT_SUPPORT = "PROVIDER_DOES_NOT_SUPPORT";
    public static final String SUPPORT_CALCULATION_NOT_IMPLEMENTED = "CALCULATION_NOT_IMPLEMENTED";
    public static final String SUPPORT_SOURCE_NOT_INTEGRATED = "SOURCE_NOT_INTEGRATED";
    public static final String SUPPORT_NOT_APPLICABLE = "NOT_APPLICABLE";

    public static final String LENDER_SUBSCRIBED = "SUBSCRIBED";
    public static final String LENDER_NOT_YET_SUBSCRIBED = "NOT_YET_SUBSCRIBED";
    public static final String LENDER_SUBSCRIPTION_SETUP_PENDING = "SUBSCRIPTION_SETUP_PENDING";
    public static final String LENDER_NOT_APPLICABLE = "NOT_APPLICABLE";

    /** Optional lender/org subscription probe — null-safe; defaults to NOT_YET_SUBSCRIBED for provider sources. */
    @FunctionalInterface
    public interface LenderSourceSubscriptionProbe {
        String statusForSourceFamily(String sourceFamily);
    }

    private DataParametersCapabilitySemantics() {}

    public static Map<String, Object> project(CanonicalParameterDefinition def) {
        return project(def, null);
    }

    public static Map<String, Object> project(
            CanonicalParameterDefinition def,
            LenderSourceSubscriptionProbe subscriptionProbe) {
        Objects.requireNonNull(def, "def");
        Map<String, Object> readiness = GacatParameterReadinessProjection.project(def);
        return project(def, readiness, subscriptionProbe);
    }

    public static Map<String, Object> project(
            CanonicalParameterDefinition def,
            Map<String, Object> readiness,
            LenderSourceSubscriptionProbe subscriptionProbe) {
        Objects.requireNonNull(def, "def");
        Objects.requireNonNull(readiness, "readiness");

        String family = def.evaluatedFrom() == null ? "" : def.evaluatedFrom().trim();
        String sourceType = String.valueOf(readiness.getOrDefault("sourceType", ""));
        SourcePlatform platform = resolveSourcePlatform(family, sourceType);

        ParameterSupport support = resolveParameterSupport(def, readiness, platform);
        String lender = resolveLenderSubscription(family, sourceType, platform, subscriptionProbe);
        // Platform/support remain descriptive; execution + design/cert from shared projection
        Map<String, Object> spineView = CanonicalParameterCapabilityProjection.project(def);
        // Spine-executable authored derived must not remain "calculation not implemented"
        support = alignSupportWithSpine(def, support, spineView);
        ProductionPolicyAvailability avail = resolveProductionPolicyAvailability(
                def, readiness, platform, support, lender);
        LiveUseAvailability live = resolveLiveUseAvailability(platform, support, lender, avail);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capabilityModel", "DATA-PARAMETERS-CAPABILITY-SEMANTICS-2-SPINE");
        out.put("lenderUx", "DATA-PARAMETERS-LENDER-UX-CLEANUP-2");
        out.put("applicationDataStateExcluded", true);
        out.put("canonicalParameterId", def.id());
        out.put("displayName", def.businessName());
        out.put("sourceFamily", family);
        out.put("sourceType", sourceType);

        Map<String, Object> sourcePlatform = new LinkedHashMap<>();
        sourcePlatform.put("status", platform.status());
        sourcePlatform.put("label", platform.label());
        sourcePlatform.put("providerLabel", platform.providerLabel());
        sourcePlatform.put("evidence", platform.evidence());
        out.put("platformIntegration", sourcePlatform);

        Map<String, Object> paramSupport = new LinkedHashMap<>();
        paramSupport.put("status", support.status());
        paramSupport.put("label", supportLabel(support.status()));
        paramSupport.put("businessLabel", supportBusinessLabel(support.status()));
        paramSupport.put("how", support.how());
        // Prefer authored lender narrative over generic "Calculated by BillionTech from …"
        String businessHowText = businessHow(platform, support);
        if (SUPPORT_SUPPORTED_DERIVED.equals(support.status())
                && support.how() != null
                && support.how().length() > 48) {
            businessHowText = support.how();
        }
        paramSupport.put("businessHow", businessHowText);
        paramSupport.put("evidence", support.evidence());
        out.put("parameterSupport", paramSupport);

        Map<String, Object> org = new LinkedHashMap<>();
        org.put("status", lender);
        org.put("label", lenderLabel(lender));
        out.put("yourOrganisation", org);

        // DESIGNABLE (GACAT) — separate from execution
        @SuppressWarnings("unchecked")
        Map<String, Object> designFromSpine = spineView.get("policyDesign") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of("available", true, "label", "Available");
        Map<String, Object> designMap = new LinkedHashMap<>(designFromSpine);
        out.put("policyDesign", designMap);

        // Execution capability — CanonicalParameterExecutionService only
        out.put("policyTest", spineView.get("policyTest"));
        out.put("workflow", spineView.get("workflow"));
        out.put("underwriting", spineView.get("underwriting"));
        out.put("policyTestExecutable", spineView.get("policyTestExecutable"));
        out.put("policyTestReady", spineView.get("policyTestReady"));
        out.put("runtimeReady", spineView.get("runtimeReady"));
        out.put("executable", spineView.get("executable"));
        out.put("executionState", spineView.get("executionState"));

        // Production certification — never catalogue production_ready
        out.put("productionCertification", spineView.get("productionCertification"));
        out.put("productionReady", false);
        out.put("productionCertified", false);

        Map<String, Object> liveMap = new LinkedHashMap<>();
        liveMap.put("status", live.status());
        liveMap.put("available", false); // live production requires certification — not established
        liveMap.put("label", "Not certified");
        liveMap.put("reason", "Production certification not established; organisation subscription is separate");
        liveMap.put("organisationSubscriptionStatus", live.status());
        liveMap.put("organisationSubscriptionLabel", live.label());
        out.put("liveUse", liveMap);

        Map<String, Object> prod = new LinkedHashMap<>();
        prod.put("available", false);
        prod.put("label", "Not certified");
        prod.put("reason", "Production certification not established");
        prod.put("means", "PRODUCTION_CERTIFICATION_NOT_ESTABLISHED");
        out.put("availableForProductionPolicyUse", prod);

        boolean canSupport = SUPPORT_SUPPORTED_RAW.equals(support.status())
                || SUPPORT_SUPPORTED_DERIVED.equals(support.status())
                || SUPPORT_NOT_APPLICABLE.equals(support.status());
        out.put("canBillionTechSupport", canSupport);
        out.put("canBillionTechSupportLabel", canSupport ? "Yes" : "No");

        out.put("legacyOverallReadiness", readiness.get("overallReadiness"));
        out.put("legacyCatalogueClaims", spineView.get("legacyCatalogueClaims"));
        out.put("catalogueProductionReady", readiness.get("legacyCatalogueProductionReadyClaim"));
        out.put("flagsMutated", false);
        out.put("executionAuthority", "CanonicalParameterExecutionService");
        return out;
    }

    /** Aggregate source-family rollup for lender source glance. */
    public static Map<String, Object> sourceFamilySummary(
            String sourceFamily,
            List<CanonicalParameterDefinition> parameters,
            LenderSourceSubscriptionProbe subscriptionProbe) {
        String family = sourceFamily == null ? "" : sourceFamily.trim();
        SourcePlatform platform = resolveSourcePlatform(family, "");
        String lender = resolveLenderSubscription(family, "", platform, subscriptionProbe);

        int raw = 0, derived = 0, noSupport = 0, calcMissing = 0, sourceMissing = 0, na = 0, other = 0;
        int canonicalDirect = 0, canonicalCalculated = 0, canonicalManual = 0, canonicalIngredient = 0;
        int readyToTest = 0, setupRequired = 0, liveApproved = 0, canWhenData = 0;
        int businessParameters = 0;
        for (CanonicalParameterDefinition def : parameters) {
            Map<String, Object> cap = project(def, subscriptionProbe);
            @SuppressWarnings("unchecked")
            Map<String, Object> ps = (Map<String, Object>) cap.get("parameterSupport");
            String st = String.valueOf(ps.get("status"));
            switch (st) {
                case SUPPORT_SUPPORTED_RAW -> raw++;
                case SUPPORT_SUPPORTED_DERIVED -> derived++;
                case SUPPORT_PROVIDER_DOES_NOT_SUPPORT -> noSupport++;
                case SUPPORT_CALCULATION_NOT_IMPLEMENTED -> calcMissing++;
                case SUPPORT_SOURCE_NOT_INTEGRATED -> sourceMissing++;
                case SUPPORT_NOT_APPLICABLE -> na++;
                default -> other++;
            }
            Map<String, Object> truth = CanonicalParameterStateService.state(def.id());
            String primary = String.valueOf(truth.getOrDefault("primaryStatus", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = truth.get("semantic") instanceof Map<?, ?>
                    ? (Map<String, Object>) truth.get("semantic") : Map.of();
            String calcMode = String.valueOf(semantic.getOrDefault("calculationMode", ""));
            String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));
            if ("INGREDIENT".equals(paramClass)) {
                canonicalIngredient++;
                // Never count source ingredients toward calculation-setup required
                continue;
            }
            businessParameters++;
            if ("MANUAL_INPUT".equals(paramClass) || "MANUAL".equalsIgnoreCase(calcMode)) {
                canonicalManual++;
            } else if ("RAW".equalsIgnoreCase(calcMode)) {
                canonicalDirect++;
            } else {
                canonicalCalculated++;
            }
            switch (primary) {
                case "READY_TO_TEST", "APPROVED_FOR_LIVE_USE" -> readyToTest++;
                case "CAN_CALCULATE_WHEN_DATA_AVAILABLE" -> {
                    canWhenData++;
                    readyToTest++; // still testable / executable path
                }
                case "CALCULATION_NEEDS_SETUP", "NEEDS_MANUAL_INPUT", "NOT_YET_SUPPORTED" -> setupRequired++;
                default -> {
                }
            }
            if ("APPROVED_FOR_LIVE_USE".equals(primary)) {
                liveApproved++;
            }
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("supportedRaw", raw);
        counts.put("supportedDerived", derived);
        counts.put("providerDoesNotSupport", noSupport);
        counts.put("calculationNotImplemented", calcMissing);
        counts.put("sourceNotIntegrated", sourceMissing);
        counts.put("notApplicable", na);
        counts.put("other", other);
        counts.put("total", parameters.size());
        int available = raw + derived + na;
        counts.put("parametersAvailable", available);
        // Wave 10A — honest canonical aggregates (lender primary)
        Map<String, Object> canonicalCounts = new LinkedHashMap<>();
        canonicalCounts.put("authority", CanonicalParameterStateService.AUTHORITY);
        canonicalCounts.put("catalogueListed", parameters.size());
        canonicalCounts.put("businessParameters", businessParameters);
        canonicalCounts.put("sourceIngredients", canonicalIngredient);
        canonicalCounts.put("directlyProvided", canonicalDirect);
        canonicalCounts.put("calculated", canonicalCalculated);
        canonicalCounts.put("manual", canonicalManual);
        canonicalCounts.put("readyToTest", readyToTest);
        canonicalCounts.put("canCalculateWhenDataAvailable", canWhenData);
        canonicalCounts.put("setupRequired", setupRequired);
        canonicalCounts.put("liveApproved", liveApproved);
        counts.put("canonical", canonicalCounts);

        Map<String, Object> lenderFacing = new LinkedHashMap<>();
        lenderFacing.put("integrationLabel", "BillionTech integration: " + platform.label());
        if (!SOURCE_PLATFORM_NOT_APPLICABLE.equals(platform.status())) {
            lenderFacing.put("organisationLabel", "Your organisation: " + lenderLabel(lender));
        }
        if (SOURCE_PLATFORM_NOT_APPLICABLE.equals(platform.status())) {
            lenderFacing.put("summaryLine", parameters.size() + " application / internal parameters");
        } else if (SOURCE_PLATFORM_NOT_INTEGRATED.equals(platform.status())) {
            lenderFacing.put("summaryLine", "Integration not yet available");
        } else {
            lenderFacing.put("summaryLine",
                    businessParameters + " business parameters · "
                            + canonicalIngredient + " source ingredients · "
                            + readyToTest + " ready to test · "
                            + setupRequired + " setup required");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("businessParameters", businessParameters);
        detail.put("sourceIngredients", canonicalIngredient);
        detail.put("directlyProvided", canonicalDirect);
        detail.put("calculatedByBillionTech", canonicalCalculated);
        detail.put("manual", canonicalManual);
        detail.put("readyToTest", readyToTest);
        detail.put("setupRequired", setupRequired);
        detail.put("liveApproved", liveApproved);
        detail.put("catalogueListed", parameters.size());
        // legacy support buckets — diagnostics only
        detail.put("legacySupportedRaw", raw);
        detail.put("legacySupportedDerived", derived);
        detail.put("legacyCalculationsNotYetImplemented", calcMissing);
        detail.put("unsupportedByProvider", noSupport);
        detail.put("integrationNotYetAvailable", sourceMissing);
        detail.put("notApplicable", na);
        lenderFacing.put("expandableDetail", detail);
        lenderFacing.put("countAuthority", CanonicalParameterStateService.AUTHORITY);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", family);
        out.put("platformIntegration", platform.status());
        out.put("platformLabel", platform.label());
        out.put("providerLabel", platform.providerLabel());
        out.put("platformEvidence", platform.evidence());
        out.put("yourOrganisation", lender);
        out.put("yourOrganisationLabel", lenderLabel(lender));
        out.put("parameterSupportCounts", counts);
        out.put("canonicalCounts", canonicalCounts);
        out.put("lenderFacing", lenderFacing);
        return out;
    }

    public static List<Map<String, Object>> summariseAllSources(
            Iterable<CanonicalParameterDefinition> all,
            Function<String, List<CanonicalParameterDefinition>> byFamily,
            List<String> families,
            LenderSourceSubscriptionProbe subscriptionProbe) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String family : families) {
            rows.add(sourceFamilySummary(family, byFamily.apply(family), subscriptionProbe));
        }
        // unused but keeps signature honest for callers that pass all
        Objects.requireNonNull(all);
        return rows;
    }

    // ─── Source platform (BillionTech capability) ─────────────────────

    record SourcePlatform(String status, String label, String providerLabel, List<String> evidence) {}

    static SourcePlatform resolveSourcePlatform(String family, String sourceType) {
        String f = family == null ? "" : family.toLowerCase(Locale.ROOT);
        String st = sourceType == null ? "" : sourceType;

        if (GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT.equals(st)
                || GacatParameterReadinessProjection.SOURCE_MANUAL.equals(st)
                || f.contains("application") || f.contains("program") || f.contains("product")
                || f.contains("manual")) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_NOT_APPLICABLE,
                    "Not a provider source",
                    null,
                    List.of("Application / Manual / Product / Program — not an external provider integration"));
        }

        if (f.contains("bureau retail") || (f.contains("bureau") && !f.contains("commercial"))) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_PRODUCTION_READY,
                    "Production Ready",
                    "Equifax Bureau Retail",
                    List.of(
                            "EquifaxBureauProvider SOAP/XML retail inquiry",
                            "EquifaxBureauAccountExtractor + BureauNormalizationService",
                            "BureauMetricService / PolicyBureauMetricService certified retail metrics",
                            "Workflow BUREAU_PULL production provides list"));
        }
        if (f.contains("bureau commercial") || (f.contains("commercial") && f.contains("bureau"))) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_NOT_INTEGRATED,
                    "Not Integrated",
                    "Commercial Bureau",
                    List.of(
                            "Commercial GACAT rows reference CommercialBureauResponseDetails fixture schema",
                            "SurePass fixture / commercial path — engineering evidence only",
                            "No production-certified commercial bureau go-live evidence in GACAT production_ready rollup"));
        }
        if (f.contains("bank") || f.contains("banking") || f.contains("account aggregator") || f.equals("aa")) {
            if (f.contains("account aggregator") || f.startsWith("aa")) {
                return new SourcePlatform(
                        SOURCE_PLATFORM_PRODUCTION_READY,
                        "Production Ready",
                        "Account Aggregator",
                        List.of(
                                "ACCOUNT_AGGREGATOR workflow integration provides core banking metrics",
                                "AA meta parameters are consent/transport — FIP facts come from bank/GST sources"));
            }
            return new SourcePlatform(
                    SOURCE_PLATFORM_PRODUCTION_READY,
                    "Production Ready",
                    "Bank Statement",
                    List.of(
                            "BankingMetricService + BankAverageDailyBalanceCalculator production paths",
                            "BANK_STATEMENT_DOCUMENT / ACCOUNT_AGGREGATOR acquisition",
                            "Some banking.* metrics remain studio/defined-only (parameter-level)"));
        }
        if (f.contains("gst")) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_PRODUCTION_READY,
                    "Production Ready",
                    "GST",
                    List.of("GstMetricService production GACAT metrics", "GST registration/return raw bindings"));
        }
        if (f.contains("financial") || f.contains("itr")) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_NOT_INTEGRATED,
                    "Not Integrated",
                    "Financial Statements / ITR",
                    List.of("Financial/ITR GACAT rows largely catalogue or not production-certified"));
        }
        if (f.contains("kyc")) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_PRODUCTION_READY,
                    "Production Ready",
                    "KYC",
                    List.of("KYC workflow steps + aggregator_configs provider matrix", "GACAT kyc.* catalogue bindings"));
        }
        if (f.contains("computed") || f.contains("obligation") || f.contains("collateral") || f.contains("derived")) {
            return new SourcePlatform(
                    SOURCE_PLATFORM_NOT_APPLICABLE,
                    "Internal / derived",
                    null,
                    List.of("Internal derived or obligation/collateral — not a single external provider"));
        }
        return new SourcePlatform(
                SOURCE_PLATFORM_NOT_INTEGRATED,
                "Not Integrated",
                null,
                List.of("No proven production provider integration evidence for source family: " + family));
    }

    // ─── Parameter support ───────────────────────────────────────────

    record ParameterSupport(String status, String how, List<String> evidence) {}

    static ParameterSupport resolveParameterSupport(
            CanonicalParameterDefinition def,
            Map<String, Object> readiness,
            SourcePlatform platform) {
        String type = def.type() == null ? "" : def.type().trim().toUpperCase(Locale.ROOT);
        String sourceType = String.valueOf(readiness.getOrDefault("sourceType", ""));
        CanonicalParameterDefinition.Capability cap = def.capability();
        boolean implemented = cap != null && cap.implemented();
        boolean sourceAvailable = cap != null && cap.sourceAvailable();
        String binding = def.existingImplementationBinding() == null ? "" : def.existingImplementationBinding();
        boolean definedNotImplemented = "DEFINED_NOT_IMPLEMENTED".equalsIgnoreCase(binding)
                || (def.calculationSummary() != null && def.calculationSummary().contains("DEFINED_NOT_IMPLEMENTED"));
        String bindingLower = binding.toLowerCase(Locale.ROOT);
        String summaryLower = def.calculationSummary() == null ? "" : def.calculationSummary().toLowerCase(Locale.ROOT);
        String missingTreat = cap != null && cap.missingDataTreatment() != null
                ? cap.missingDataTreatment().toUpperCase(Locale.ROOT) : "";
        // Vocabulary-gated stubs are not executable calculators — surface Define calculation.
        boolean vocabularyGatedStub = bindingLower.contains("vocabulary-gated")
                || missingTreat.contains("NEEDS_CONFIGURATION")
                || summaryLower.contains("must be confirmed")
                || summaryLower.contains("customer-defined");
        boolean hasCalculator = Boolean.TRUE.equals(readiness.get("calculatorAvailable")) && !vocabularyGatedStub;
        String path = cap == null || cap.providerFieldPath() == null ? "" : cap.providerFieldPath().trim();
        List<String> primitives = def.requiredPrimitives() == null ? List.of() : def.requiredPrimitives();
        if (vocabularyGatedStub && CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(type)) {
            definedNotImplemented = true;
        }

        if (GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT.equals(sourceType)
                || GacatParameterReadinessProjection.SOURCE_MANUAL.equals(sourceType)
                || CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(type)) {
            return new ParameterSupport(
                    SUPPORT_NOT_APPLICABLE,
                    "Captured as application / customer / RM input — not a provider payload field",
                    List.of("sourceType=" + sourceType, "parameterKind=" + type));
        }

        if (SOURCE_PLATFORM_NOT_INTEGRATED.equals(platform.status())) {
            return new ParameterSupport(
                    SUPPORT_SOURCE_NOT_INTEGRATED,
                    "Underlying source/provider is not production-integrated",
                    List.of("platformIntegration=" + platform.status(), platform.label()));
        }

        if (SOURCE_PLATFORM_NOT_APPLICABLE.equals(platform.status())
                && CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(type)) {
            if (definedNotImplemented || !implemented) {
                return new ParameterSupport(
                        SUPPORT_CALCULATION_NOT_IMPLEMENTED,
                        "Internal derivation not implemented",
                        List.of("binding=" + binding));
            }
            if (implemented && hasCalculator) {
                return new ParameterSupport(
                        SUPPORT_SUPPORTED_DERIVED,
                        howFrom(def),
                        List.of("binding=" + binding, "implemented=true"));
            }
        }

        if (CanonicalParameterDefinition.RAW.equalsIgnoreCase(type)) {
            if (!sourceAvailable && path.isBlank()) {
                return new ParameterSupport(
                        SUPPORT_PROVIDER_DOES_NOT_SUPPORT,
                        "No provider field path / source availability for this raw parameter",
                        List.of("sourceAvailable=false", "providerFieldPath blank"));
            }
            if (implemented || !path.isBlank() || sourceAvailable) {
                List<String> ev = new ArrayList<>();
                if (!path.isBlank()) ev.add("providerFieldPath=" + path);
                ev.add("sourceAvailable=" + sourceAvailable);
                ev.add("implemented=" + implemented);
                return new ParameterSupport(
                        SUPPORT_SUPPORTED_RAW,
                        path.isBlank()
                                ? "Provider/source supplies this value directly"
                                : "Mapped from provider field: " + path,
                        ev);
            }
            return new ParameterSupport(
                    SUPPORT_PROVIDER_DOES_NOT_SUPPORT,
                    "Raw parameter lacks proven provider mapping",
                    List.of("implemented=" + implemented));
        }

        // DERIVED
        if (definedNotImplemented || (!implemented && !primitives.isEmpty())) {
            return new ParameterSupport(
                    SUPPORT_CALCULATION_NOT_IMPLEMENTED,
                    "Provider primitives exist (or are declared) but BillionTech has not implemented/certified the derivation",
                    List.of(
                            "binding=" + (binding.isBlank() ? "(blank)" : binding),
                            "implemented=" + implemented,
                            "primitives=" + primitives));
        }
        if (!implemented && primitives.isEmpty() && path.isBlank()) {
            return new ParameterSupport(
                    SUPPORT_PROVIDER_DOES_NOT_SUPPORT,
                    "No implemented calculator and no declared primitives/path",
                    List.of("implemented=false"));
        }
        if (implemented && hasCalculator) {
            return new ParameterSupport(
                    SUPPORT_SUPPORTED_DERIVED,
                    howFrom(def),
                    List.of(
                            "binding=" + binding,
                            "calculatorAvailable=true",
                            "primitives=" + primitives,
                            "catalogueProductionReady=" + readiness.get("productionReady")));
        }
        if (!sourceAvailable) {
            return new ParameterSupport(
                    SUPPORT_SOURCE_NOT_INTEGRATED,
                    "Source marked unavailable on catalogue capability",
                    List.of("sourceAvailable=false"));
        }
        return new ParameterSupport(
                SUPPORT_CALCULATION_NOT_IMPLEMENTED,
                "Derivation path incomplete",
                List.of("implemented=" + implemented, "calculatorAvailable=" + hasCalculator));
    }

    private static String howFrom(CanonicalParameterDefinition def) {
        if (def.calculationSummary() != null && !def.calculationSummary().isBlank()) {
            return def.calculationSummary();
        }
        if (def.existingImplementationBinding() != null && !def.existingImplementationBinding().isBlank()) {
            return "Calculated via " + def.existingImplementationBinding();
        }
        return "Derived from provider primitives";
    }

    // ─── Lender subscription ─────────────────────────────────────────

    static String resolveLenderSubscription(
            String family,
            String sourceType,
            SourcePlatform platform,
            LenderSourceSubscriptionProbe probe) {
        if (SOURCE_PLATFORM_NOT_APPLICABLE.equals(platform.status())
                || GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT.equals(sourceType)
                || GacatParameterReadinessProjection.SOURCE_MANUAL.equals(sourceType)) {
            return LENDER_NOT_APPLICABLE;
        }
        if (SOURCE_PLATFORM_NOT_INTEGRATED.equals(platform.status())) {
            return LENDER_NOT_APPLICABLE;
        }
        if (probe != null) {
            String s = probe.statusForSourceFamily(family);
            if (s != null && !s.isBlank()) {
                return s.trim().toUpperCase(Locale.ROOT);
            }
        }
        // No commercial subscription table yet — do not invent SUBSCRIBED.
        return LENDER_NOT_YET_SUBSCRIBED;
    }

    // ─── Production policy availability (compat / live evaluation) ───

    record ProductionPolicyAvailability(boolean available, String reason) {}

    static ProductionPolicyAvailability resolveProductionPolicyAvailability(
            CanonicalParameterDefinition def,
            Map<String, Object> readiness,
            SourcePlatform platform,
            ParameterSupport support,
            String lender) {
        // Retained for engineering/compat — mirrors live evaluation, not policy design.
        LiveUseAvailability live = resolveLiveUseAvailability(
                platform, support, lender,
                new ProductionPolicyAvailability(false, "deferred"));
        return new ProductionPolicyAvailability(live.available(), live.reason());
    }

    /**
     * Policy / Scorecard design availability.
     * Audit: Policy Studio authoring is gated by GACAT/executability, not lender subscription.
     * Subscription must not block designing a future policy.
     */
    record PolicyDesignAvailability(boolean available, String label, String reason) {}

    static PolicyDesignAvailability resolvePolicyDesignAvailability(
            SourcePlatform platform,
            ParameterSupport support) {
        if (SUPPORT_NOT_APPLICABLE.equals(support.status())) {
            return new PolicyDesignAvailability(
                    true,
                    "Available for policy design",
                    "Application / manual / internal parameter — selectable while designing Policy/Scorecard");
        }
        if (SOURCE_PLATFORM_NOT_INTEGRATED.equals(platform.status())
                || SUPPORT_SOURCE_NOT_INTEGRATED.equals(support.status())) {
            return new PolicyDesignAvailability(
                    false,
                    "Not currently available",
                    "Integration not yet available");
        }
        if (SUPPORT_PROVIDER_DOES_NOT_SUPPORT.equals(support.status())) {
            return new PolicyDesignAvailability(
                    false,
                    "Not currently available",
                    "Provider does not supply required data");
        }
        if (SUPPORT_CALCULATION_NOT_IMPLEMENTED.equals(support.status())) {
            return new PolicyDesignAvailability(
                    false,
                    "Not currently available",
                    "Calculation not yet implemented");
        }
        if (SUPPORT_SUPPORTED_RAW.equals(support.status())
                || SUPPORT_SUPPORTED_DERIVED.equals(support.status())) {
            return new PolicyDesignAvailability(
                    true,
                    "Available for policy design",
                    "BillionTech can support this parameter — may be selected in Policy/Scorecard design");
        }
        return new PolicyDesignAvailability(
                false,
                "Not currently available",
                "Parameter support incomplete");
    }

    /**
     * Live production evaluation for this lender organisation.
     * Runtime remains fail-closed until subscription/configuration is valid.
     */
    record LiveUseAvailability(String status, boolean available, String label, String reason) {}

    static final String LIVE_AVAILABLE = "AVAILABLE";
    static final String LIVE_SUBSCRIPTION_REQUIRED = "SUBSCRIPTION_REQUIRED";
    static final String LIVE_SETUP_PENDING = "SUBSCRIPTION_SETUP_PENDING";
    static final String LIVE_NOT_AVAILABLE = "NOT_AVAILABLE";

    static LiveUseAvailability resolveLiveUseAvailability(
            SourcePlatform platform,
            ParameterSupport support,
            String lender,
            ProductionPolicyAvailability ignoredCompat) {
        if (SUPPORT_NOT_APPLICABLE.equals(support.status())) {
            return new LiveUseAvailability(
                    LIVE_AVAILABLE,
                    true,
                    "Available for live use",
                    "Application / RM input — evaluated when captured on the application");
        }
        if (SOURCE_PLATFORM_NOT_INTEGRATED.equals(platform.status())
                || SUPPORT_SOURCE_NOT_INTEGRATED.equals(support.status())) {
            return new LiveUseAvailability(
                    LIVE_NOT_AVAILABLE,
                    false,
                    "Not available for live use",
                    "Integration not yet available");
        }
        if (SUPPORT_PROVIDER_DOES_NOT_SUPPORT.equals(support.status())) {
            return new LiveUseAvailability(
                    LIVE_NOT_AVAILABLE,
                    false,
                    "Not available for live use",
                    "Provider does not supply required data");
        }
        if (SUPPORT_CALCULATION_NOT_IMPLEMENTED.equals(support.status())) {
            return new LiveUseAvailability(
                    LIVE_NOT_AVAILABLE,
                    false,
                    "Not available for live use",
                    "Calculation not yet implemented");
        }
        if (LENDER_NOT_YET_SUBSCRIBED.equals(lender)) {
            String subName = subscriptionDisplayName(platform);
            return new LiveUseAvailability(
                    LIVE_SUBSCRIPTION_REQUIRED,
                    false,
                    "Live use requires subscription",
                    "Live use requires " + subName + " subscription");
        }
        if (LENDER_SUBSCRIPTION_SETUP_PENDING.equals(lender)) {
            return new LiveUseAvailability(
                    LIVE_SETUP_PENDING,
                    false,
                    "Subscription/setup required",
                    "Subscription accepted — setup still pending before live evaluation");
        }
        if (SUPPORT_SUPPORTED_RAW.equals(support.status())
                || SUPPORT_SUPPORTED_DERIVED.equals(support.status())) {
            return new LiveUseAvailability(
                    LIVE_AVAILABLE,
                    true,
                    "Available for live use",
                    "Platform integrated, parameter supported, organisation subscribed");
        }
        return new LiveUseAvailability(
                LIVE_NOT_AVAILABLE,
                false,
                "Not available for live use",
                "Live evaluation not available");
    }

    private static String subscriptionDisplayName(SourcePlatform platform) {
        String label = platform.providerLabel();
        if (label != null && !label.isBlank()) {
            if (label.toLowerCase(Locale.ROOT).contains("equifax")) {
                return "Equifax";
            }
            return label;
        }
        return "provider";
    }

    public static String supportBusinessLabel(String status) {
        return switch (String.valueOf(status)) {
            case SUPPORT_SUPPORTED_RAW -> "Supported — directly provided";
            case SUPPORT_SUPPORTED_DERIVED -> "Supported — calculated by BillionTech";
            case SUPPORT_PROVIDER_DOES_NOT_SUPPORT -> "Provider does not supply required data";
            case SUPPORT_CALCULATION_NOT_IMPLEMENTED -> "Calculation not yet implemented";
            case SUPPORT_SOURCE_NOT_INTEGRATED -> "Integration not yet available";
            case SUPPORT_NOT_APPLICABLE -> "Application / internal input";
            default -> status;
        };
    }

    static String businessHow(SourcePlatform platform, ParameterSupport support) {
        String st = support.status();
        if (SUPPORT_SUPPORTED_RAW.equals(st)) {
            String provider = platform.providerLabel() == null ? "the source" : platform.providerLabel();
            return "Directly provided by " + provider;
        }
        if (SUPPORT_SUPPORTED_DERIVED.equals(st)) {
            String provider = platform.providerLabel() == null ? "source data" : platform.providerLabel() + " data";
            return "Calculated by BillionTech from " + provider;
        }
        if (SUPPORT_NOT_APPLICABLE.equals(st)) {
            return "Captured as application / customer / RM input";
        }
        if (SUPPORT_CALCULATION_NOT_IMPLEMENTED.equals(st)) {
            return "Calculation not yet implemented";
        }
        if (SUPPORT_PROVIDER_DOES_NOT_SUPPORT.equals(st)) {
            return "Provider does not supply required data";
        }
        if (SUPPORT_SOURCE_NOT_INTEGRATED.equals(st)) {
            return "Integration not yet available";
        }
        return support.how();
    }

    /**
     * When the spine can execute an authored derived calculation, catalogue
     * {@code DEFINED_NOT_IMPLEMENTED} must not keep D&amp;P on CALCULATION_NOT_IMPLEMENTED.
     */
    static ParameterSupport alignSupportWithSpine(
            CanonicalParameterDefinition def,
            ParameterSupport support,
            Map<String, Object> spineView) {
        if (def == null || support == null || spineView == null) {
            return support;
        }
        if (!CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(def.type())) {
            return support;
        }
        if (!Boolean.TRUE.equals(spineView.get("policyTestReady"))) {
            return support;
        }
        if (SUPPORT_SUPPORTED_DERIVED.equals(support.status())
                || SUPPORT_SUPPORTED_RAW.equals(support.status())
                || SUPPORT_NOT_APPLICABLE.equals(support.status())) {
            // Already supported — still prefer authored narrative when longer
            return AuthoredDerivedCalculationSupport.latestExecutableHow(def.id())
                    .filter(h -> h.length() > 48)
                    .map(h -> new ParameterSupport(support.status(), h, withSpineEvidence(support.evidence())))
                    .orElse(support);
        }
        if (!SUPPORT_CALCULATION_NOT_IMPLEMENTED.equals(support.status())
                && !SUPPORT_PROVIDER_DOES_NOT_SUPPORT.equals(support.status())) {
            return support;
        }
        String how = AuthoredDerivedCalculationSupport.latestExecutableHow(def.id())
                .orElse("Counts values using an approved BillionTech derived calculation.");
        return new ParameterSupport(
                SUPPORT_SUPPORTED_DERIVED,
                how,
                withSpineEvidence(support.evidence()));
    }

    private static List<String> withSpineEvidence(List<String> prior) {
        List<String> ev = new ArrayList<>(prior == null ? List.of() : prior);
        ev.add("spinePolicyTestReady=true");
        ev.add("authority=CanonicalParameterExecutionService");
        return ev;
    }

    public static String supportLabel(String status) {
        return switch (String.valueOf(status)) {
            case SUPPORT_SUPPORTED_RAW -> "Supported — Raw";
            case SUPPORT_SUPPORTED_DERIVED -> "Supported — Derived";
            case SUPPORT_PROVIDER_DOES_NOT_SUPPORT -> "Provider Does Not Support";
            case SUPPORT_CALCULATION_NOT_IMPLEMENTED -> "Calculation Not Implemented";
            case SUPPORT_SOURCE_NOT_INTEGRATED -> "Source Not Integrated";
            case SUPPORT_NOT_APPLICABLE -> "Not applicable (non-provider)";
            default -> status;
        };
    }

    public static String lenderLabel(String status) {
        return switch (String.valueOf(status)) {
            case LENDER_SUBSCRIBED -> "Subscribed";
            case LENDER_NOT_YET_SUBSCRIBED -> "Not Yet Subscribed";
            case LENDER_SUBSCRIPTION_SETUP_PENDING -> "Subscription Setup Pending";
            case LENDER_NOT_APPLICABLE -> "Not applicable";
            default -> status;
        };
    }

    public static String platformLabel(String status) {
        return switch (String.valueOf(status)) {
            case SOURCE_PLATFORM_PRODUCTION_READY -> "Production Ready";
            case SOURCE_PLATFORM_NOT_INTEGRATED -> "Not Integrated";
            case SOURCE_PLATFORM_NOT_APPLICABLE -> "Not applicable";
            default -> status;
        };
    }
}
