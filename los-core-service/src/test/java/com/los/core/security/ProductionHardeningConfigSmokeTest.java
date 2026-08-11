package com.los.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves effective production YAML defaults for P0 live closure (no secret values).
 */
class ProductionHardeningConfigSmokeTest {

    @Test
    void applicationProd_hasLiveSafeDefaults() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties p = yaml.getObject();
        assertThat(p).isNotNull();
        assertThat(p.getProperty("los.demo.enabled")).isEqualTo("false");
        assertThat(p.getProperty("los.underwriting.provider-gap-defaults-enabled")).isEqualTo("false");
        assertThat(p.getProperty("los.security.admin-api-require-role")).isEqualTo("true");
        assertThat(p.getProperty("los.security.enforce-production-hardening")).isEqualTo("true");
        assertThat(p.getProperty("credit-intelligence.staging-demo.enabled")).isEqualTo("false");
        assertThat(p.getProperty("credit-intelligence.validation.enabled")).isEqualTo("false");
        assertThat(p.getProperty("credit-intelligence.internal-token-required")).isEqualTo("true");
        assertThat(p.getProperty("credit-intelligence.cutover.allow-canonical-authority")).isEqualTo("false");
    }

    @Test
    void applicationYml_gapDefaultsOffByDefault() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties p = yaml.getObject();
        assertThat(p).isNotNull();
        // May be property placeholder — check raw or resolved form
        String gap = p.getProperty("los.underwriting.provider-gap-defaults-enabled");
        assertThat(gap).isNotBlank();
        assertThat(gap).contains("false");
    }
}
