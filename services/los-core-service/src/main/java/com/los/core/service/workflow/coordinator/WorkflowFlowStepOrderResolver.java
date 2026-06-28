package com.los.core.service.workflow.coordinator;

import com.los.core.model.entity.WorkflowConfig;
import com.los.core.service.flow.step.FlowStepType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives a linear {@link FlowStepType} order from {@code workflow_configs.steps} JSON.
 * <p>
 * KYC JSON rows (e.g. {@code PAN_VERIFY}) do not each map 1:1 to {@link IStepExecutor}; a single
 * {@link FlowStepType#KYC_WORKFLOW} run covers the identity/verification KYC sub-steps in execution
 * order (see {@code KycIdentityWorkflow} — {@code BUREAU_PULL} is listed here for ordering but is
 * not executed inside the KYC sub-workflow). Rows whose {@code "step"} are not a known
 * {@link com.los.core.model.enums.KycStepType} are ignored for top-level flow ordering
 * (forward-compatible).
 */
public final class WorkflowFlowStepOrderResolver {

    private static final Set<String> POST_SANCTION_STEPS = Set.of(
            FlowStepType.ESIGN, FlowStepType.DISBURSE
    );

    private WorkflowFlowStepOrderResolver() {
    }

    public static List<String> resolve(WorkflowConfig config, WorkflowResolutionOptions options) {
        if (config == null || config.getSteps() == null || config.getSteps().isEmpty()) {
            return new ArrayList<>(options.emptyConfigFallback());
        }
        List<Map<String, Object>> sorted = new ArrayList<>(config.getSteps());
        sorted.sort(Comparator.comparingInt(m -> orderOf(m)));
        List<String> out = new ArrayList<>();
        if (!sorted.isEmpty()) {
            out.add(FlowStepType.KYC_WORKFLOW);
        }
        // Preserve first-seen BUREAU relative to sorted config (always after the logical KYC block).
        for (Map<String, Object> m : sorted) {
            if (vkyc(m)) {
                out.add(FlowStepType.VKYC);
            }
            if (bureauPull(m)) {
                out.add(FlowStepType.BUREAU_PULL);
                break;
            }
        }
        if (options.addBureauWhenMissingFromConfig() && out.stream().noneMatch(FlowStepType.BUREAU_PULL::equals)) {
            out.add(FlowStepType.BUREAU_PULL);
        }
        return dedupeOrder(out);
    }

    /**
     * True when a flow step is always permitted outside the KYC config JSON
     * (eSign and disburse are post-sanction in the loan flow).
     */
    public static boolean isPostSanctionFlowStep(String stepType) {
        return stepType != null && POST_SANCTION_STEPS.contains(stepType);
    }

    private static int orderOf(Map<String, Object> m) {
        Object o = m.get("order");
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(o.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String stepKey(Map<String, Object> m) {
        Object s = m.get("step");
        return s != null ? s.toString() : null;
    }

    private static boolean bureauPull(Map<String, Object> m) {
        return FlowStepType.BUREAU_PULL.equals(stepKey(m));
    }

    private static boolean vkyc(Map<String, Object> m) {
        String step = stepKey(m);
        return "VIDEO_KYC".equalsIgnoreCase(step) || "VKYC".equalsIgnoreCase(step);
    }

    private static List<String> dedupeOrder(List<String> steps) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String s : steps) {
            if (s != null && seen.add(s)) {
                out.add(s);
            }
        }
        return out;
    }

}
