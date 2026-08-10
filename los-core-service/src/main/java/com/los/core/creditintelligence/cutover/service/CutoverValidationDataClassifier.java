package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.ValidationDataOrigin;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies C6 bundles as REPRESENTATIVE_FIXTURE; scans classpath provider-fixtures honestly.
 */
@Service
public class CutoverValidationDataClassifier {

    public ValidationDataOrigin classifyC6Bundle(String caseCode) {
        // C6 harness bundles are representative fixtures by design
        return ValidationDataOrigin.REPRESENTATIVE_FIXTURE;
    }

    public Map<String, Object> scanProviderFixtures() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> fixtures = new ArrayList<>();
        int storedProvider = 0;
        int synthetic = 0;
        int userSample = 0;
        int realDev = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] origins = resolver.getResources("classpath*:provider-fixtures/**/ORIGIN.md");
            for (Resource r : origins) {
                String path = r.getURL().toString();
                String content = read(r);
                ValidationDataOrigin origin = classifyOriginDoc(content);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("path", path.contains("provider-fixtures")
                        ? path.substring(path.indexOf("provider-fixtures"))
                        : path);
                row.put("origin", origin.name());
                row.put("liveProduction", false);
                fixtures.add(row);
                switch (origin) {
                    case STORED_PROVIDER -> storedProvider++;
                    case SYNTHETIC -> synthetic++;
                    case REAL_DEV -> realDev++;
                    default -> userSample++;
                }
            }
        } catch (Exception e) {
            out.put("scanError", e.getClass().getSimpleName());
        }
        out.put("fixtures", fixtures);
        out.put("counts", Map.of(
                "STORED_PROVIDER", storedProvider,
                "SYNTHETIC", synthetic,
                "REPRESENTATIVE_FIXTURE_OR_USER_SAMPLE", userSample,
                "REAL_DEV", realDev));
        out.put("hasStoredProvider", storedProvider > 0);
        out.put("hasRealDev", realDev > 0);
        out.put("honestyNote",
                "ORIGIN.md labels used as-is. USER_SUPPLIED_SAMPLE mapped to REPRESENTATIVE_FIXTURE. "
                        + "No live production pulls.");
        return out;
    }

    static ValidationDataOrigin classifyOriginDoc(String content) {
        if (content == null) return ValidationDataOrigin.SYNTHETIC;
        String c = content.toUpperCase(Locale.ROOT);
        if (c.contains("REAL_DEV") || c.contains("ANONYMIZED_REAL")) {
            return ValidationDataOrigin.REAL_DEV;
        }
        if (c.contains("STORED_PROVIDER") || c.contains("STORED PROVIDER")) {
            return ValidationDataOrigin.STORED_PROVIDER;
        }
        if (c.contains("SYNTHETIC") || c.contains("HAND-BUILT")) {
            return ValidationDataOrigin.SYNTHETIC;
        }
        // USER_SUPPLIED_SAMPLE / sanitized samples → representative fixture
        return ValidationDataOrigin.REPRESENTATIVE_FIXTURE;
    }

    private static String read(Resource r) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
