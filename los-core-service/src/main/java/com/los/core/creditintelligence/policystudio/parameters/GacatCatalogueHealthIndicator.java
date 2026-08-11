package com.los.core.creditintelligence.policystudio.parameters;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GACAT-PERSISTENCE-1 — catalogue integrity health (empty/corrupt → DOWN when DB required).
 */
@Component("gacatCatalogueHealthIndicator")
public class GacatCatalogueHealthIndicator implements HealthIndicator {

    private final GacatCatalogueRepository repository;

    public GacatCatalogueHealthIndicator(GacatCatalogueRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        if (!repository.tablesPresent()) {
            if (GacatCatalogueAuthority.requireDatabase()) {
                return Health.down().withDetail("reason", "tables_missing").build();
            }
            return Health.unknown().withDetail("reason", "tables_missing_non_required").build();
        }
        Map<String, Object> report = repository.integrityReport();
        boolean ok = Boolean.TRUE.equals(report.get("structuralOk"));
        Health.Builder b = ok ? Health.up() : Health.down();
        b.withDetail("authority", GacatCatalogueAuthority.activeAuthority());
        b.withDetails(report);
        b.withDetail("allowCanonicalAuthority", false);
        b.withDetail("javaSeedIsRuntimeAuthority",
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY
                        .equals(GacatCatalogueAuthority.activeAuthority()));
        return b.build();
    }
}
