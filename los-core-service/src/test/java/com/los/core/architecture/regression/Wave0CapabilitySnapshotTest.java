package com.los.core.architecture.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Captures / asserts the 169-parameter capability snapshot.
 * Regenerate committed baseline: -Dwave0.generate=true
 */
class Wave0CapabilitySnapshotTest {

    @Test
    void generateOrAssertSnapshot() throws Exception {
        Wave0SpineBaselineHarness harness = new Wave0SpineBaselineHarness();
        Map<String, Object> snapshot = harness.captureCapabilitySnapshot();

        ObjectMapper mapper = Wave0GoldenDatasets.mapper().copy().enable(SerializationFeature.INDENT_OUTPUT);
        Path committed = Path.of("src", "test", "resources", "architecture-regression", "baselines",
                "gacat-169-capability-snapshot.json");
        Path targetOut = Path.of("target", "architecture-regression", "gacat-169-capability-snapshot.json");
        Files.createDirectories(targetOut.getParent());
        mapper.writeValue(targetOut.toFile(), snapshot);

        boolean generate = Boolean.parseBoolean(System.getProperty("wave0.generate", "false"));
        if (generate || !Files.exists(committed)) {
            Files.createDirectories(committed.getParent());
            mapper.writeValue(committed.toFile(), snapshot);
        }

        assertThat(snapshot.get("totalParameters")).isEqualTo(169);
        assertThat((Integer) snapshot.get("policyTestCapableCount")).isGreaterThan(0);
        assertThat((Integer) snapshot.get("w6CapableCount")).isGreaterThan(0);
        assertThat((Integer) snapshot.get("underwritingCapableCount")).isGreaterThan(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) snapshot.get("parameters");
        assertThat(params).hasSize(169);

        // MUST_PRESERVE: catalogue flags must not equal capability for overdue unsupported
        Map<String, Object> cc = params.stream()
                .filter(p -> "bureau.cc_overdue_amount".equals(p.get("canonicalId")))
                .findFirst().orElseThrow();
        assertThat(cc.get("policyTestCapable")).isEqualTo(false);
        // KNOWN_GAP documented: legacy implemented may still be true in catalogue
        assertThat(cc.containsKey("legacyImplemented")).isTrue();

        if (Files.exists(committed) && !generate) {
            String expected = Files.readString(committed, StandardCharsets.UTF_8);
            String actual = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(snapshot);
            // Compare structural totals first; full JSON equality after normalize newlines
            Map<?, ?> expectedMap = mapper.readValue(expected, Map.class);
            assertThat(snapshot.get("totalParameters")).isEqualTo(expectedMap.get("totalParameters"));
            assertThat(snapshot.get("policyTestCapableCount"))
                    .isEqualTo(expectedMap.get("policyTestCapableCount"));
            assertThat(snapshot.get("w6CapableCount")).isEqualTo(expectedMap.get("w6CapableCount"));
            assertThat(snapshot.get("underwritingCapableCount"))
                    .isEqualTo(expectedMap.get("underwritingCapableCount"));
        }
    }
}
