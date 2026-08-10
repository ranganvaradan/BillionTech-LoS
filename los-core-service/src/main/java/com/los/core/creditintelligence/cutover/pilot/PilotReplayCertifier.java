package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Replay certification — 100% hash match required (§18).
 */
@Service
public class PilotReplayCertifier {

    public record ReplayCase(
            String applicationLabel,
            String evaluateHash,
            String replayHash,
            String policyHash,
            String replayPolicyHash,
            String decisionHash,
            String replayDecisionHash
    ) {
    }

    public record ReplayCertResult(
            boolean pass,
            BigDecimal passRate,
            int total,
            int matched,
            int failed,
            List<String> failures
    ) {
    }

    public ReplayCertResult certify(List<ReplayCase> cases) {
        List<ReplayCase> list = cases == null ? List.of() : cases;
        List<String> failures = new ArrayList<>();
        int matched = 0;
        for (ReplayCase c : list) {
            boolean ok = Objects.equals(c.evaluateHash(), c.replayHash())
                    && Objects.equals(c.policyHash(), c.replayPolicyHash())
                    && Objects.equals(c.decisionHash(), c.replayDecisionHash());
            if (ok) {
                matched++;
            } else {
                failures.add(c.applicationLabel() + " replay hash mismatch");
            }
        }
        int total = list.size();
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(matched * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
        boolean pass = total > 0 && matched == total;
        return new ReplayCertResult(pass, rate, total, matched, total - matched, failures);
    }

    public Map<String, Object> asMap(ReplayCertResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pass", r.pass());
        out.put("passRate", r.passRate());
        out.put("total", r.total());
        out.put("matched", r.matched());
        out.put("failed", r.failed());
        out.put("failures", r.failures());
        out.put("requirement", "100% hash match");
        return out;
    }
}
