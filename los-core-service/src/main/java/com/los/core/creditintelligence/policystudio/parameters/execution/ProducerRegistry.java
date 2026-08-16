package com.los.core.creditintelligence.policystudio.parameters.execution;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Programmatic exact-ID producer registry. Catalogue flags are never consulted here.
 */
@Component
public class ProducerRegistry {

    private final Map<String, ParameterProducer> exact = new LinkedHashMap<>();
    private final List<ParameterProducer> dynamic = new CopyOnWriteArrayList<>();

    public synchronized void registerExact(String canonicalParameterId, ParameterProducer producer) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank() || producer == null) {
            return;
        }
        exact.put(canonicalParameterId, producer);
    }

    public void registerDynamic(ParameterProducer producer) {
        if (producer != null) {
            dynamic.add(producer);
        }
    }

    public Optional<ParameterProducer> find(String canonicalParameterId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        ParameterProducer p = exact.get(canonicalParameterId);
        if (p != null) {
            return Optional.of(p);
        }
        for (ParameterProducer d : dynamic) {
            if (d.claims(canonicalParameterId)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public List<String> exactIds() {
        return List.copyOf(exact.keySet());
    }

    public List<ParameterProducer> dynamicProducers() {
        return List.copyOf(dynamic);
    }

    /** Test helper — clear registrations. */
    public synchronized void clear() {
        exact.clear();
        dynamic.clear();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exactIds", new ArrayList<>(exact.keySet()));
        m.put("dynamicCount", dynamic.size());
        return m;
    }
}
