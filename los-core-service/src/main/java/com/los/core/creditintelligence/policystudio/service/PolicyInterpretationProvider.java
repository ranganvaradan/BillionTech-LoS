package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;

import java.util.List;

/**
 * Pluggable interpretation backend. P0 uses DeterministicGoldenInterpretationProvider;
 * optional LLM stub behind ai-enabled=false.
 */
public interface PolicyInterpretationProvider {

    String providerCode();

    List<CiPolicyInterpretation> interpret(
            List<CiPolicyClause> clauses,
            PolicyAuthoringRegistry registry,
            PolicyClauseExtractor.FixtureKind kind);
}
