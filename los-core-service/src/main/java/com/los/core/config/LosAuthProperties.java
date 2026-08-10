package com.los.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "los.auth")
public class LosAuthProperties {

    /** Initial password for users created from the LOS user directory (must be changed on first login). */
    private String defaultTemporaryPassword = "Temp@123";
}
