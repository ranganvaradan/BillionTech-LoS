package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Single-parameter truth invariant.
 *
 * <p>Every consumer (Data &amp; Parameters, Policy Studio, Scorecard, Policy Test, Workflow)
 * must use the same underlying authorities for a canonical parameter. No screen or service
 * may independently invent {@code implemented}, {@code executable}, calculation-ready,
 * policy-test-ready, or production-ready.
 *
 * <pre>
 * GACAT                              = identity, semantics, type, unit, source/lineage
 * CanonicalParameterExecutionService = executable capability + actual value production
 * Production Certification           = governed live-use approval (separate from catalogue flags)
 * </pre>
 *
 * <p>Data &amp; Parameters is only another view of these same authorities — not a parallel
 * readiness engine. Catalogue {@code implemented}/{@code production_ready} are claims /
 * lineage hints, never execution proof.
 */
public final class ParameterTruthAuthorities {

    public static final String GACAT_IDENTITY = "GACAT";
    public static final String EXECUTION_SPINE = "CanonicalParameterExecutionService";
    public static final String PRODUCTION_CERTIFICATION = "ProductionCertification";

    public static final String INVARIANT =
            "For every canonical parameter, Data & Parameters, Policy Studio, Scorecard, "
                    + "Policy Test and Workflow must consume the same GACAT identity and the same "
                    + "CanonicalParameterExecutionService execution-capability authority. "
                    + "Production Certification is the only governed live-use approval channel.";

    private ParameterTruthAuthorities() {}
}
