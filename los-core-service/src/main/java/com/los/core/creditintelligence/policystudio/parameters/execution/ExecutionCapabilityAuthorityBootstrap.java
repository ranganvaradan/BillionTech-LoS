package com.los.core.creditintelligence.policystudio.parameters.execution;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Installs the Spring spine into {@link ExecutionCapabilityAuthority} for static facades. */
@Component
@RequiredArgsConstructor
public class ExecutionCapabilityAuthorityBootstrap {

    private final CanonicalParameterExecutionService executionService;

    @PostConstruct
    public void install() {
        ExecutionCapabilityAuthority.install(executionService);
    }
}
