package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.lifecycle.ApplicationPolicyQueryFactory;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.underwriting.UnderwritingEvaluationContextFactory;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wave-6 shared evaluation context contract helpers.
 * One semantic shape for Policy Test, target-live canonical, Graph test, W6→policy, Scorecard.
 * Does not invent wall-clock dates.
 */
public final class SharedCanonicalEvaluationSupport {

    /**
     * Intentional Policy Test / Graph Test default when the caller explicitly opts into the
     * Wave-0 golden business date (not wall-clock; not hidden production now()).
     */
    public static final LocalDate CANONICAL_POLICY_TEST_AS_OF = LocalDate.of(2026, 8, 1);

    public static final String ASOF_SOURCE_EXPLICIT = "EXPLICIT";
    public static final String ASOF_SOURCE_APPLICATION_BUSINESS = "APPLICATION_BUSINESS_DATE";
    public static final String ASOF_SOURCE_INTENTIONAL_TEST_DEFAULT = "INTENTIONAL_TEST_DEFAULT";
    public static final String ASOF_SOURCE_MISSING = "MISSING";

    private SharedCanonicalEvaluationSupport() {}

    public record AsOfResolution(LocalDate asOf, String source) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("evaluationAsOf", asOf == null ? null : asOf.toString());
            m.put("asOfSource", source);
            return m;
        }
    }

    /** Resolve asOf for Policy Test / Graph: explicit body → intentional test default. */
    public static AsOfResolution resolvePolicyTestAsOf(LocalDate explicitFromRequest) {
        if (explicitFromRequest != null) {
            return new AsOfResolution(explicitFromRequest, ASOF_SOURCE_EXPLICIT);
        }
        return new AsOfResolution(CANONICAL_POLICY_TEST_AS_OF, ASOF_SOURCE_INTENTIONAL_TEST_DEFAULT);
    }

    /** Resolve asOf for target-live / scorecard: explicit → application business date → missing. */
    public static AsOfResolution resolveLiveBusinessAsOf(LoanApplication app, LocalDate explicit) {
        if (explicit != null) {
            return new AsOfResolution(explicit, ASOF_SOURCE_EXPLICIT);
        }
        LocalDate business = ApplicationPolicyQueryFactory.resolveEvaluationBusinessDate(app, null);
        if (business != null) {
            return new AsOfResolution(business, ASOF_SOURCE_APPLICATION_BUSINESS);
        }
        return new AsOfResolution(null, ASOF_SOURCE_MISSING);
    }

    /**
     * Copy spine context into a normalized builder with required mode/asOf.
     * Facts/inputs/entities preserved — no second fact authority.
     */
    public static EvaluationContext normalize(
            EvaluationContext source,
            EvaluationMode mode,
            LocalDate evaluationAsOf) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(evaluationAsOf, "evaluationAsOf");
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(evaluationAsOf);
        if (source != null) {
            b.tenantId(source.tenantId())
                    .documentId(source.documentId())
                    .applicationId(source.applicationId());
            source.facts().forEach(b::fact);
            source.inputs().forEach(b::input);
            source.entities().forEach(b::entity);
        }
        b.entity("sharedCanonicalContext", true);
        b.entity("wave6ContextContract", "SharedCanonicalEvaluationSupport");
        return b.build();
    }

    /** W6 / materialization facts → shared UNDERWRITING or POLICY_TEST spine for CPR. */
    public static EvaluationContext fromMaterializedFacts(
            EvaluationMode mode,
            LocalDate evaluationAsOf,
            Map<String, Object> exactFacts,
            Map<String, Object> simulationInputs) {
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(evaluationAsOf);
        if (exactFacts != null) {
            exactFacts.forEach(b::fact);
        }
        if (simulationInputs != null) {
            simulationInputs.forEach(b::input);
        }
        b.entity("sharedCanonicalContext", true);
        b.entity("factAuthority", "materializedExactCanonicalFacts");
        return b.build();
    }

    /** Same UW factory as Scorecard, with Wave-6 business asOf preference. */
    public static EvaluationContext forUnderwritingAndScorecard(
            LoanApplication app,
            EffectiveUnderwritingContext uw,
            LocalDate evaluationAsOf) {
        AsOfResolution resolved = resolveLiveBusinessAsOf(app, evaluationAsOf);
        return UnderwritingEvaluationContextFactory.forUnderwriting(app, uw, resolved.asOf());
    }

    /** Honesty projection for UI / test responses. */
    public static Map<String, Object> honestyProjection(ExecutionResult er) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (er == null) {
            m.put("present", false);
            return m;
        }
        m.put("canonicalId", er.canonicalParameterId());
        m.put("executionStatus", er.status() == null ? null : er.status().name());
        m.put("realCapability", er.capability());
        m.put("valueSimulated", er.simulatedValue());
        m.put("value", er.value());
        m.put("realValue", er.capability() && !er.simulatedValue() ? er.value() : null);
        m.put("simulatedValue", er.simulatedValue() ? er.value() : null);
        m.put("notExecutableInRealContext", !er.capability());
        m.put("simulationChangesRealCapability", false);
        if (er.provenance() != null) {
            m.put("simulatedValueDoesNotImplyExecutable",
                    er.provenance().getOrDefault("simulatedValueDoesNotImplyExecutable", !er.capability()));
        }
        return m;
    }
}
