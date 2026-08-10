package com.los.core.config;

import com.los.core.service.workflow.coordinator.WorkflowResolutionOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defaults for {@link com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator}.
 */
@Configuration
public class WorkflowExecutionConfig {

    @Bean
    public WorkflowResolutionOptions workflowResolutionOptions() {
        return WorkflowResolutionOptions.defaults();
    }
}
