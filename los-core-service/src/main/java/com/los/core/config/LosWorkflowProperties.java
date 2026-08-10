package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "los.workflow")
public class LosWorkflowProperties {

    private ProgramApproval programApproval = new ProgramApproval();
    private BorrowerDelegation borrowerDelegation = new BorrowerDelegation();

    @Data
    public static class ProgramApproval {
        private boolean enabled = true;
    }

    @Data
    public static class BorrowerDelegation {
        private boolean enabled = true;
    }
}
