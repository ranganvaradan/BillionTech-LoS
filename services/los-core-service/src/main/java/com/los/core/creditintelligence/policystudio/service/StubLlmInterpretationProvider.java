package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional LLM stub — never invoked when ai-enabled=false (default).
 */
@Component
@ConditionalOnProperty(prefix = "credit-intelligence.policy-studio", name = "ai-enabled", havingValue = "true")
public class StubLlmInterpretationProvider implements PolicyInterpretationProvider {

    @Override
    public String providerCode() {
        return "STUB_LLM_V0";
    }

    @Override
    public List<CiPolicyInterpretation> interpret(
            List<CiPolicyClause> clauses,
            PolicyAuthoringRegistry registry,
            PolicyClauseExtractor.FixtureKind kind) {
        List<CiPolicyInterpretation> out = new ArrayList<>();
        for (CiPolicyClause c : clauses) {
            out.add(CiPolicyInterpretation.builder()
                    .id(UUID.randomUUID())
                    .clauseId(c.getId())
                    .interpretationVersion(1)
                    .interpretedClauseType(c.getClauseType())
                    .naturalLanguageMeaning("STUB LLM — not used in P0 production path")
                    .candidateExpression(Map.of("op", "STUB"))
                    .confidence(new BigDecimal("0.1000"))
                    .providerCode(providerCode())
                    .aiModel("STUB")
                    .build());
        }
        return out;
    }
}
