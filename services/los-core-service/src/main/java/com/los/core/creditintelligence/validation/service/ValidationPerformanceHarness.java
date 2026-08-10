package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.validation.domain.DataOrigin;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Times synthetic bank txn processing stages for scale recommendations.
 * Labels SYNTHETIC; does not call live providers.
 */
@Component
public class ValidationPerformanceHarness {

    public record PerfResult(
            int transactionCount,
            DataOrigin origin,
            Map<String, Long> stageMs,
            long totalMs,
            long heapUsedMb,
            String recommendation
    ) {
    }

    public List<PerfResult> runDefaultSizes() {
        return List.of(run(1_000), run(10_000));
    }

    public PerfResult run(int txnCount) {
        Map<String, Long> stages = new LinkedHashMap<>();
        long t0 = System.nanoTime();

        long s = System.nanoTime();
        List<Map<String, Object>> txns = synthesize(txnCount);
        stages.put("synthesizeMs", elapsedMs(s));

        s = System.nanoTime();
        long creditSum = 0;
        long debitSum = 0;
        for (Map<String, Object> t : txns) {
            long amt = ((Number) t.get("amount")).longValue();
            if (Boolean.TRUE.equals(t.get("credit"))) {
                creditSum += amt;
            } else {
                debitSum += amt;
            }
        }
        stages.put("normalizeAggregateMs", elapsedMs(s));

        s = System.nanoTime();
        // Simulate metric rollup
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("creditSum", creditSum);
        metrics.put("debitSum", debitSum);
        metrics.put("txnCount", txns.size());
        stages.put("metricMs", elapsedMs(s));

        s = System.nanoTime();
        // Simulate lightweight recon
        long variance = Math.abs(creditSum - debitSum);
        metrics.put("flowVariance", variance);
        stages.put("reconciliationMs", elapsedMs(s));

        long total = elapsedMs(t0);
        stages.put("totalMs", total);
        Runtime rt = Runtime.getRuntime();
        long heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        String recommendation;
        if (txnCount <= 10_000 && total < 5_000) {
            recommendation = "retain relational transaction store; partition later; "
                    + "pre-aggregated monthly metrics recommended before 100k+";
        } else if (txnCount >= 100_000) {
            recommendation = "partition now by month/tenant; consider hot/cold split + object-store archival";
        } else {
            recommendation = "retain relational; add indexes (V98); partition later when volume warrants";
        }

        // prevent GC of metrics
        if (metrics.isEmpty()) {
            recommendation = "n/a";
        }

        return new PerfResult(txnCount, DataOrigin.SYNTHETIC, stages, total, heapMb, recommendation);
    }

    private static List<Map<String, Object>> synthesize(int n) {
        List<Map<String, Object>> list = new ArrayList<>(Math.min(n, 50_000));
        // Cap memory for unit tests — simulate larger counts with stride
        int materialize = Math.min(n, 20_000);
        int stride = Math.max(1, n / materialize);
        for (int i = 0; i < materialize; i++) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("amount", 1000L + (i * stride) % 50000);
            t.put("credit", i % 3 != 0);
            t.put("i", i * stride);
            list.add(t);
        }
        return list;
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
