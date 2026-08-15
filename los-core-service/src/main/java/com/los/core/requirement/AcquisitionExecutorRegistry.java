package com.los.core.requirement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of thin adapters over existing source executors.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionExecutorRegistry {

    private final List<AcquisitionExecutorPort> executors;

    public Optional<AcquisitionExecutorPort> find(String sourceKey) {
        if (sourceKey == null) {
            return Optional.empty();
        }
        String key = AcquisitionSourceResolver.normalize(sourceKey);
        for (AcquisitionExecutorPort exec : executors) {
            if (exec.supports(key)) {
                return Optional.of(exec);
            }
        }
        return Optional.empty();
    }

    public List<String> registeredSourceKeys() {
        List<String> keys = new ArrayList<>();
        for (AcquisitionExecutorPort exec : executors) {
            keys.add(exec.sourceKey());
        }
        return keys;
    }

    public Map<String, String> diagnostics() {
        Map<String, String> m = new LinkedHashMap<>();
        for (AcquisitionExecutorPort exec : executors) {
            m.put(exec.sourceKey(), exec.getClass().getSimpleName());
        }
        return m;
    }
}
