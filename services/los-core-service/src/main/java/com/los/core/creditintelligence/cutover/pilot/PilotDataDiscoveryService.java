package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.ValidationDataOrigin;
import com.los.core.creditintelligence.cutover.service.CutoverValidationDataClassifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Discovers validation-bundles + provider-fixtures; classifies ORIGIN honestly; no PII (§3).
 */
@Service
public class PilotDataDiscoveryService {

    private final CutoverValidationDataClassifier classifier;

    public PilotDataDiscoveryService(CutoverValidationDataClassifier classifier) {
        this.classifier = classifier != null ? classifier : new CutoverValidationDataClassifier();
    }

    public PilotDataDiscoveryService() {
        this(new CutoverValidationDataClassifier());
    }

    public Map<String, Object> discover() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> datasets = new ArrayList<>();
        EnumMap<ValidationDataOrigin, Integer> byOrigin = new EnumMap<>(ValidationDataOrigin.class);
        for (ValidationDataOrigin o : ValidationDataOrigin.values()) {
            byOrigin.put(o, 0);
        }

        for (String caseCode : List.of("CASE_A", "CASE_B", "CASE_C", "CASE_D", "CASE_E")) {
            ValidationDataOrigin origin = classifier.classifyC6Bundle(caseCode);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("application", "validation-bundle:" + caseCode);
            row.put("tenant", "fixture");
            row.put("product", "DIGILEAP");
            row.put("sourcesAvailable", List.of("bureau", "gst", "bank", "itr"));
            row.put("sourceDates", "fixture");
            row.put("dataOrigin", origin.name());
            row.put("piiStatus", "NONE_FIXTURE");
            row.put("safeForValidation", true);
            row.put("countsTowardRealStoredMinimum", CutoverEvidenceWeighting.countsAsRealOrStored(origin));
            datasets.add(row);
            byOrigin.merge(origin, 1, Integer::sum);
        }

        Map<String, Object> fixtureScan = classifier.scanProviderFixtures();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) fixtureScan.getOrDefault("fixtures", List.of());
        for (Map<String, Object> f : fixtures) {
            ValidationDataOrigin origin = ValidationDataOrigin.valueOf(String.valueOf(f.get("origin")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("application", "provider-fixture:" + sanitizePath(String.valueOf(f.get("path"))));
            row.put("tenant", "fixture");
            row.put("product", inferProduct(String.valueOf(f.get("path"))));
            row.put("sourcesAvailable", inferSources(String.valueOf(f.get("path"))));
            row.put("sourceDates", "unknown");
            row.put("dataOrigin", origin.name());
            row.put("piiStatus", "SANITIZED_OR_NONE");
            row.put("safeForValidation", true);
            row.put("countsTowardRealStoredMinimum", CutoverEvidenceWeighting.countsAsRealOrStored(origin));
            datasets.add(row);
            byOrigin.merge(origin, 1, Integer::sum);
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] manifests = resolver.getResources("classpath*:cutover-pilot/discovery-manifest.json");
            report.put("manifestPresent", manifests.length > 0);
        } catch (Exception e) {
            report.put("manifestPresent", false);
        }

        int realStored = byOrigin.getOrDefault(ValidationDataOrigin.REAL_DEV, 0)
                + byOrigin.getOrDefault(ValidationDataOrigin.STORED_PROVIDER, 0);
        int fixtureCount = byOrigin.getOrDefault(ValidationDataOrigin.REPRESENTATIVE_FIXTURE, 0)
                + byOrigin.getOrDefault(ValidationDataOrigin.SYNTHETIC, 0);

        Map<String, Integer> originCounts = new LinkedHashMap<>();
        byOrigin.forEach((k, v) -> originCounts.put(k.name(), v));

        report.put("datasets", datasets);
        report.put("datasetCount", datasets.size());
        report.put("countsByOrigin", originCounts);
        report.put("realStoredCaseCount", realStored);
        report.put("fixtureCaseCount", fixtureCount);
        report.put("weightedEvidenceScore", CutoverEvidenceWeighting.weightedScore(byOrigin));
        report.put("evidenceWeighting", CutoverEvidenceWeighting.descriptor());
        report.put("honestyNote",
                "No live production pulls. C6 CASE_A–E labeled REPRESENTATIVE_FIXTURE and do NOT count "
                        + "toward minRealOrStoredCases. realStoredCaseCount reflects ORIGIN.md classifications only.");
        report.put("piiExcluded", true);
        return report;
    }

    public int realStoredCaseCount() {
        Object v = discover().get("realStoredCaseCount");
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String sanitizePath(String path) {
        if (path == null) return "";
        int idx = path.toLowerCase(Locale.ROOT).indexOf("provider-fixtures");
        return idx >= 0 ? path.substring(idx) : path;
    }

    private static String inferProduct(String path) {
        String p = path == null ? "" : path.toUpperCase(Locale.ROOT);
        if (p.contains("DIGILEAP") || p.contains("STARTER")) return "DIGILEAP";
        return "MULTI";
    }

    private static List<String> inferSources(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        List<String> sources = new ArrayList<>();
        if (p.contains("cibil") || p.contains("commercial") || p.contains("bureau") || p.contains("equifax")) {
            sources.add("bureau");
        }
        if (p.contains("gst")) sources.add("gst");
        if (p.contains("bank") || p.contains("aa") || p.contains("setu")) sources.add("bank");
        if (p.contains("itr") || p.contains("tax")) sources.add("itr");
        if (sources.isEmpty()) sources.add("unknown");
        return sources;
    }
}
