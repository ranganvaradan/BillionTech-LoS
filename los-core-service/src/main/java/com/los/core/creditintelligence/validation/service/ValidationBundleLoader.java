package com.los.core.creditintelligence.validation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads multi-source validation bundles from classpath validation-bundles/&lt;case&gt;/bundle.json.
 */
@Component
public class ValidationBundleLoader {

    private final ObjectMapper objectMapper;

    public ValidationBundleLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public ValidationBundleLoader() {
        this(new ObjectMapper());
    }

    public ValidationBundle load(ValidationCaseCode caseCode) {
        String path = switch (caseCode) {
            case CASE_A_STRONG -> "validation-bundles/case_a_strong/bundle.json";
            case CASE_B_LEGACY_DEFAULT -> "validation-bundles/case_b_legacy_default/bundle.json";
            case CASE_C_TURNOVER_CONFLICT -> "validation-bundles/case_c_turnover_conflict/bundle.json";
            case CASE_D_OBLIGATION_CONFLICT -> "validation-bundles/case_d_obligation_conflict/bundle.json";
            case CASE_E_INCOMPLETE -> "validation-bundles/case_e_incomplete/bundle.json";
        };
        return loadClasspath(path);
    }

    public ValidationBundle loadClasspath(String resourcePath) {
        try (InputStream in = classLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Bundle not found: " + resourcePath);
            }
            JsonNode root = objectMapper.readTree(in);
            ValidationCaseCode caseCode = ValidationCaseCode.valueOf(root.path("caseCode").asText());
            DataOrigin origin = DataOrigin.valueOf(root.path("dataOrigin").asText());
            Map<String, String> sources = new LinkedHashMap<>();
            root.path("sources").fields().forEachRemaining(e -> sources.put(e.getKey(), e.getValue().asText()));
            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            root.path("metricStubs").fields().forEachRemaining(e ->
                    metrics.put(e.getKey(), new BigDecimal(e.getValue().asText())));
            Map<String, Object> legacy = objectMapper.convertValue(
                    root.path("legacyScorecardStub"), Map.class);
            BigDecimal declaredEmi = root.path("declaredEmi").isNull() || root.path("declaredEmi").isMissingNode()
                    ? null
                    : new BigDecimal(root.path("declaredEmi").asText());
            Map<String, Object> metadata = objectMapper.convertValue(root.path("metadata"), Map.class);
            return new ValidationBundle(caseCode, origin, sources, metrics, legacy, declaredEmi, metadata);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load bundle: " + resourcePath, e);
        }
    }

    public JsonNode loadFixture(String fixturePath) {
        try (InputStream in = classLoader().getResourceAsStream(fixturePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + fixturePath);
            }
            if (fixturePath.endsWith(".xml")) {
                String xml = new String(in.readAllBytes());
                return objectMapper.createObjectNode().put("rawXml", xml).put("_path", fixturePath);
            }
            return objectMapper.readTree(in);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load fixture: " + fixturePath, e);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : ValidationBundleLoader.class.getClassLoader();
    }
}
