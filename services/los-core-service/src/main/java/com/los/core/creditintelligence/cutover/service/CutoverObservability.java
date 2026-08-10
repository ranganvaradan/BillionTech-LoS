package com.los.core.creditintelligence.cutover.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cutover counters — no PII.
 */
@Component
public class CutoverObservability {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void inc(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public void add(String name, long delta) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    public long get(String name) {
        LongAdder a = counters.get(name);
        return a == null ? 0L : a.sum();
    }

    public Map<String, Long> snapshot() {
        ConcurrentHashMap<String, Long> out = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    public void reset() {
        counters.clear();
    }
}
