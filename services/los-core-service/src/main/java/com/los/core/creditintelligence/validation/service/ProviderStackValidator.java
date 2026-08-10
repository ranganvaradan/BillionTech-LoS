package com.los.core.creditintelligence.validation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.provider.equifax.EquifaxConsumerBureauAdapter;
import com.los.core.creditintelligence.provider.karza.KarzaGstAdapter;
import com.los.core.creditintelligence.provider.karza.KarzaItrAdapter;
import com.los.core.creditintelligence.provider.setu.SetuAaAdapter;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderAdapter;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassBsaAdapter;
import com.los.core.creditintelligence.provider.surepass.SurePassCibilAdapter;
import com.los.core.creditintelligence.provider.surepass.SurePassCommercialBureauAdapter;
import com.los.core.creditintelligence.provider.surepass.SurePassGstAdapter;
import com.los.core.creditintelligence.provider.surepass.SurePassItrAdapter;
import com.los.core.creditintelligence.provider.surepass.SurePassTisAdapter;
import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.model.ProviderStackResult;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs provider adapters against fixture JSON and records entity/fact counts.
 * Does not call live providers.
 */
@Service
public class ProviderStackValidator {

    private final ObjectMapper objectMapper;
    private final ValidationBundleLoader bundleLoader;
    private final List<ProviderAdapter> adapters;

    public ProviderStackValidator(ObjectMapper objectMapper, ValidationBundleLoader bundleLoader) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.bundleLoader = bundleLoader != null ? bundleLoader : new ValidationBundleLoader(this.objectMapper);
        ObjectMapper om = this.objectMapper;
        this.adapters = List.of(
                new SurePassGstAdapter(om),
                new SurePassItrAdapter(om),
                new SurePassBsaAdapter(om),
                new SurePassCibilAdapter(om),
                new SurePassCommercialBureauAdapter(om),
                new SurePassTisAdapter(om),
                new KarzaGstAdapter(om),
                new KarzaItrAdapter(om),
                new SetuAaAdapter(om),
                new EquifaxConsumerBureauAdapter(om)
        );
    }

    public ProviderStackValidator() {
        this(new ObjectMapper(), null);
    }

    public List<ProviderStackResult> validateBundle(ValidationBundle bundle) {
        List<ProviderStackResult> results = new ArrayList<>();
        for (Map.Entry<String, String> e : bundle.sources().entrySet()) {
            results.add(validateFixture(e.getValue(), bundle.dataOrigin(), e.getKey()));
        }
        return results;
    }

    public List<ProviderStackResult> validateAllMajorFixtures() {
        List<String> paths = List.of(
                "provider-fixtures/surepass/gst/gst_monthly_minimal.json",
                "provider-fixtures/surepass/itr/itr_income_heads_distinct.json",
                "provider-fixtures/surepass/bsa/bsa_minimal.json",
                "provider-fixtures/surepass/cibil/cibil_minimal.json",
                "provider-fixtures/surepass/commercial/commercial_minimal.json",
                "provider-fixtures/surepass/tis/tis_amounts_distinct.json",
                "provider-fixtures/karza/gst/karza_gst_minimal.json",
                "provider-fixtures/karza/itr/karza_itr_minimal.json",
                "provider-fixtures/setu/aa_fi_minimal.json",
                "provider-fixtures/equifax/equifax_accounts_minimal.xml"
        );
        List<ProviderStackResult> out = new ArrayList<>();
        for (String path : paths) {
            out.add(validateFixture(path, DataOrigin.USER_SUPPLIED_SAMPLE, inferSourceKey(path)));
        }
        return out;
    }

    public ProviderStackResult validateFixture(String fixturePath, DataOrigin origin, String sourceKey) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        try {
            JsonNode payload = loadPayload(fixturePath);
            ProviderAdapter adapter = findAdapter(payload, fixturePath);
            if (adapter == null) {
                errors.add("No adapter supports fixture: " + fixturePath);
                return new ProviderStackResult(
                        "UNKNOWN", sourceKey, origin, null, null, 0, 0, 0,
                        errors, warnings, ignored, Map.of("fixturePath", fixturePath));
            }
            ExtractionRequest req = new ExtractionRequest(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    UUID.randomUUID(), null, Map.of("validation", true));
            ProviderExtractionResult extracted = adapter.extract(payload, req);
            int facts = extracted.facts() != null ? extracted.facts().size() : 0;
            int obs = extracted.observations() != null ? extracted.observations().size() : 0;
            ignored.addAll(detectIgnoredHighValue(extracted));
            if (facts == 0 && obs == 0) {
                warnings.add("Adapter produced zero facts and observations");
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("fixturePath", fixturePath);
            meta.put("schemaVersion", adapter.schemaVersion());
            if (extracted.metadata() != null) {
                meta.putAll(extracted.metadata());
            }
            return new ProviderStackResult(
                    adapter.providerCode(),
                    adapter.sourceType() != null ? adapter.sourceType().name() : sourceKey,
                    origin,
                    extracted.parserVersion() != null ? extracted.parserVersion() : adapter.parserVersion(),
                    extracted.normalizerVersion() != null ? extracted.normalizerVersion() : adapter.normalizerVersion(),
                    facts + obs,
                    facts,
                    obs,
                    errors,
                    warnings,
                    ignored,
                    meta);
        } catch (Exception ex) {
            errors.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return new ProviderStackResult(
                    "ERROR", sourceKey, origin, null, null, 0, 0, 0,
                    errors, warnings, ignored, Map.of("fixturePath", fixturePath));
        }
    }

    private JsonNode loadPayload(String fixturePath) throws Exception {
        if (fixturePath.endsWith(".xml")) {
            try (var in = classLoader().getResourceAsStream(fixturePath)) {
                if (in == null) {
                    throw new IllegalArgumentException("Missing: " + fixturePath);
                }
                String xml = new String(in.readAllBytes());
                return objectMapper.createObjectNode().put("rawXml", xml);
            }
        }
        return bundleLoader.loadFixture(fixturePath);
    }

    private ProviderAdapter findAdapter(JsonNode payload, String path) {
        // Prefer path-based selection for known fixture trees to avoid cross-provider supports() collisions
        if (path.contains("/equifax/")) {
            return adapters.stream().filter(a -> a instanceof EquifaxConsumerBureauAdapter).findFirst().orElse(null);
        }
        if (path.contains("karza") && path.contains("/gst/")) {
            return adapters.stream().filter(a -> a instanceof KarzaGstAdapter).findFirst().orElse(null);
        }
        if (path.contains("karza") && path.contains("/itr/")) {
            return adapters.stream().filter(a -> a instanceof KarzaItrAdapter).findFirst().orElse(null);
        }
        if (path.contains("/setu/")) {
            return adapters.stream().filter(a -> a instanceof SetuAaAdapter).findFirst().orElse(null);
        }
        if (path.contains("/tis/")) {
            return adapters.stream().filter(a -> a instanceof SurePassTisAdapter).findFirst().orElse(null);
        }
        if (path.contains("/commercial/")) {
            return adapters.stream().filter(a -> a instanceof SurePassCommercialBureauAdapter).findFirst().orElse(null);
        }
        if (path.contains("/cibil/")) {
            return adapters.stream().filter(a -> a instanceof SurePassCibilAdapter).findFirst().orElse(null);
        }
        if (path.contains("/bsa/")) {
            return adapters.stream().filter(a -> a instanceof SurePassBsaAdapter).findFirst().orElse(null);
        }
        if (path.contains("surepass") && path.contains("/gst/")) {
            return adapters.stream().filter(a -> a instanceof SurePassGstAdapter).findFirst().orElse(null);
        }
        if (path.contains("surepass") && path.contains("/itr/")) {
            return adapters.stream().filter(a -> a instanceof SurePassItrAdapter).findFirst().orElse(null);
        }
        for (ProviderAdapter a : adapters) {
            try {
                if (a.supports(payload)) {
                    return a;
                }
            } catch (Exception ignoredEx) {
                // try next
            }
        }
        return null;
    }

    private static List<String> detectIgnoredHighValue(ProviderExtractionResult extracted) {
        List<String> ignored = new ArrayList<>();
        // Document known high-value fields adapters may skip in minimal fixtures
        if (extracted.metadata() != null && extracted.metadata().containsKey("ignoredFields")) {
            Object raw = extracted.metadata().get("ignoredFields");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    ignored.add(String.valueOf(o));
                }
            }
        }
        return ignored;
    }

    private static String inferSourceKey(String path) {
        if (path.contains("/gst/")) return "gst";
        if (path.contains("/itr/")) return "itr";
        if (path.contains("/bsa/") || path.contains("/setu/")) return "bank";
        if (path.contains("/cibil/") || path.contains("/equifax/") || path.contains("/commercial/")) return "bureau";
        if (path.contains("/tis/")) return "tis";
        return "other";
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : ProviderStackValidator.class.getClassLoader();
    }
}
