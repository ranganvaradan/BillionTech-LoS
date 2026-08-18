package com.los.core.creditintelligence.bureau.api;

import com.los.core.config.IntegrationProperties;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.flow.step.StepResult;
import com.los.core.service.integration.providers.impl.EquifaxBureauProvider;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * INTERNAL/TEST-ONLY Equifax classpath fixture ingest.
 * Enters at the provider-response boundary and uses the production bureau flow step.
 * Disabled unless {@code los.integration.equifax.internal-fixture-ingest-enabled=true}.
 * Does not enable Client or production simulation. Does not write RAW/DERIVED SQL directly.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/bureau")
@RequiredArgsConstructor
public class EquifaxInternalFixtureIngestController {

    private final IntegrationProperties integrationProperties;
    private final WorkflowExecutionCoordinator workflowExecutionCoordinator;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping("/applications/{applicationId}/ingest-classpath-fixture")
    public Map<String, Object> ingestClasspathFixture(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        if (!integrationProperties.getEquifax().isInternalFixtureIngestEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "EQUIFAX_FIXTURE_INGEST_DISABLED");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(EquifaxBureauProvider.FIXTURE_SOURCE_KEY, EquifaxBureauProvider.FIXTURE_CLASSPATH_SAMPLE);
        context.put("internalFixtureIngest", true);
        StepResult result = workflowExecutionCoordinator.executeFlowStepForApplication(
                applicationId, FlowStepType.BUREAU_PULL, context);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        out.put("fixtureSource", EquifaxBureauProvider.FIXTURE_CLASSPATH_SAMPLE);
        out.put("productionParserUsed", true);
        out.put("liveDecisionAuthorityUnchanged", true);
        if (result != null && result.output() != null) {
            out.putAll(result.output());
        }
        return out;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token is blank — allowing fixture ingest without token (local only)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
