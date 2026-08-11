package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GACAT-PERSISTENCE-1 — loads DB catalogue into {@link CanonicalParameterRegistry} at startup.
 * Production/staging: fail closed when catalogue missing/corrupt. Never silently falls back to Java seed.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class GacatCatalogueRuntimeLoader implements ApplicationRunner {

    private final GacatCatalogueRepository repository;
    private final CreditIntelligenceProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean stagingOrProd = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("staging") || p.equals("prod") || p.equals("production"));
        boolean requireDb = properties.getGacat() != null && properties.getGacat().isRequireDatabase()
                || stagingOrProd;
        boolean allowSeed = properties.getGacat() != null && properties.getGacat().isAllowSeedFallback()
                && !requireDb;
        GacatCatalogueAuthority.configure(requireDb, allowSeed);

        if (!repository.tablesPresent()) {
            if (requireDb) {
                throw new IllegalStateException(
                        "GACAT catalogue tables missing — Flyway V112/V113 required (no Java-seed fallback)");
            }
            log.warn("GACAT tables absent; using test/dev seed-backed registry (requireDatabase=false)");
            CanonicalParameterRegistry.install(
                    CanonicalParameterRegistry.fromSeedForTestsOnly(),
                    GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
            return;
        }

        Map<String, Object> integrity = repository.integrityReport();
        int count = repository.parameterCount();
        if (count == 0 || !Boolean.TRUE.equals(integrity.get("structuralOk"))) {
            if (requireDb) {
                throw new IllegalStateException(
                        "GACAT catalogue empty or structurally corrupt — refusing silent Java-seed fallback. "
                                + integrity);
            }
            log.warn("GACAT DB empty/corrupt in non-require mode; seed-backed registry for tests: {}", integrity);
            CanonicalParameterRegistry.install(
                    CanonicalParameterRegistry.fromSeedForTestsOnly(),
                    GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
            return;
        }

        List<CanonicalParameterDefinition> defs = repository.loadActiveDefinitions();
        if (defs.isEmpty()) {
            throw new IllegalStateException("GACAT load returned zero definitions despite count=" + count);
        }
        CanonicalParameterRegistry.install(
                new CanonicalParameterRegistry(defs, GacatCatalogueAuthority.AUTHORITY_DATABASE),
                GacatCatalogueAuthority.AUTHORITY_DATABASE);
        log.info("GACAT catalogue loaded from database: count={} inventoryVersion={} authority=DATABASE",
                defs.size(), integrity.get("inventoryVersion"));
    }
}
