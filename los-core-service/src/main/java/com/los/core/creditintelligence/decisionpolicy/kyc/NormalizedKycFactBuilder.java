package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.model.entity.KycStepResult;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds normalized KYC facts for future Decision Policy / DSL consumption.
 * Derives only from persisted step evidence — never fabricates.
 * Does not mutate production KYC orchestration.
 */
public final class NormalizedKycFactBuilder {

    private NormalizedKycFactBuilder() {}

    public record StepEvidence(
            KycStepType stepType,
            StepOutcome outcome,
            String errorMessage,
            Map<String, Object> parsedData,
            boolean fallbackUsed,
            Boolean nameMatchHint
    ) {
        public static StepEvidence of(KycStepResult r) {
            return of(r, false);
        }

        public static StepEvidence of(KycStepResult r, boolean fallbackUsed) {
            if (r == null) {
                return null;
            }
            Boolean nameMatch = extractNameMatch(r.getParsedData());
            return new StepEvidence(
                    r.getStepType(),
                    r.getOutcome(),
                    r.getErrorMessage(),
                    r.getParsedData(),
                    fallbackUsed,
                    nameMatch);
        }
    }

    public static Map<String, Object> buildFacts(Collection<StepEvidence> steps) {
        return buildFacts(steps, null, null);
    }

    /**
     * @param applicationHints optional present flags e.g. panPresent, gstinPresent, cinPresent, pkycCompleted, vkycStatus
     * @param legacyAggregateOutcome optional production PASS/FAIL/INCOMPLETE for overall mapping (informational)
     */
    public static Map<String, Object> buildFacts(
            Collection<StepEvidence> steps,
            Map<String, Object> applicationHints,
            String legacyAggregateOutcome
    ) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Map<String, Object> hints = applicationHints == null ? Map.of() : applicationHints;
        List<StepEvidence> list = steps == null ? List.of() : new ArrayList<>(steps);

        putPresent(facts, "kyc.pan.present", hints.get("panPresent"));
        putPresent(facts, "kyc.gstin.present", hints.get("gstinPresent"));
        putPresent(facts, "kyc.cin.present", hints.get("cinPresent"));

        applyStep(facts, list, KycStepType.PAN_VERIFY, "kyc.pan");
        applyStep(facts, list, KycStepType.CKYC_DOWNLOAD, "kyc.ckyc");
        applyCkycAvailable(facts, list);
        applyStep(facts, list, KycStepType.AADHAAR_OTP, "kyc.aadhaar");
        applyStep(facts, list, KycStepType.GSTIN_VERIFY, "kyc.gstin");
        applyStep(facts, list, KycStepType.CIN_MCA21, "kyc.cin");
        applyStep(facts, list, KycStepType.UDYAM_VERIFY, "kyc.udyam");
        applyStep(facts, list, KycStepType.BANK_PENNY_DROP, "kyc.bank_account");

        applyVkyc(facts, list, hints);
        putPresent(facts, "kyc.pkyc.completed", hints.get("pkycCompleted"));

        KycBusinessOutcome overall = deriveOverall(facts, list, legacyAggregateOutcome);
        facts.put("kyc.overall.outcome", overall.name());
        facts.put("kyc.overall.technical_status", deriveOverallTechnical(list).name());
        return facts;
    }

    private static void applyStep(
            Map<String, Object> facts,
            List<StepEvidence> list,
            KycStepType type,
            String prefix
    ) {
        StepEvidence step = latest(list, type);
        if (step == null) {
            return;
        }
        var classified = KycStepOutcomeSemantics.classify(
                step.outcome(), step.errorMessage(), step.parsedData(), step.fallbackUsed());

        if ("kyc.pan".equals(prefix)) {
            facts.put("kyc.pan.verification_status", classified.businessOutcome().name());
            if (classified.businessOutcome() == KycBusinessOutcome.PASS) {
                facts.put("kyc.pan.verified", true);
            } else if (classified.businessOutcome() == KycBusinessOutcome.FAIL) {
                facts.put("kyc.pan.verified", false);
            }
            // MISSING_INFORMATION / REFER → leave verified absent (unknown)
            if (step.nameMatchHint() != null) {
                facts.put("kyc.pan.name_match", step.nameMatchHint());
            }
            facts.put("kyc.pan.technical_status", classified.technicalStatus().name());
            if (classified.fallbackUsed()) {
                facts.put("kyc.pan.fallback_used", true);
            }
            return;
        }

        String verifiedKey = switch (prefix) {
            case "kyc.ckyc" -> "kyc.ckyc.verified";
            case "kyc.aadhaar" -> "kyc.aadhaar.verified";
            case "kyc.gstin" -> "kyc.gstin.verified";
            case "kyc.cin" -> "kyc.cin.verified";
            case "kyc.udyam" -> "kyc.udyam.verified";
            case "kyc.bank_account" -> "kyc.bank_account.verified";
            default -> prefix + ".verified";
        };
        if (classified.businessOutcome() == KycBusinessOutcome.PASS) {
            facts.put(verifiedKey, true);
        } else if (classified.businessOutcome() == KycBusinessOutcome.FAIL) {
            facts.put(verifiedKey, false);
        }
        facts.put(prefix + ".technical_status", classified.technicalStatus().name());
        if (classified.fallbackUsed()) {
            facts.put(prefix + ".fallback_used", true);
        }
        if (classified.businessOutcome() == KycBusinessOutcome.REFER) {
            facts.put(prefix + ".refer", KycReferPayload.fromStep(
                    classified.reasonCode(),
                    verifiedKey,
                    type.name(),
                    "Step requires manual KYC review").toMap());
        }
    }

    private static void applyCkycAvailable(Map<String, Object> facts, List<StepEvidence> list) {
        StepEvidence dl = latest(list, KycStepType.CKYC_DOWNLOAD);
        if (dl == null) {
            return;
        }
        var c = KycStepOutcomeSemantics.classify(dl.outcome(), dl.errorMessage(), dl.parsedData(), dl.fallbackUsed());
        if (c.businessOutcome() == KycBusinessOutcome.PASS) {
            facts.put("kyc.ckyc.available", true);
        } else if (c.technicalStatus().isTechnicalFailure()) {
            // unavailable ≠ false present
            facts.put("kyc.ckyc.technical_status", c.technicalStatus().name());
        } else if (c.businessOutcome() == KycBusinessOutcome.FAIL) {
            facts.put("kyc.ckyc.available", false);
        }
    }

    private static void applyVkyc(Map<String, Object> facts, List<StepEvidence> list, Map<String, Object> hints) {
        Object pkyc = hints.get("pkycCompleted");
        if (Boolean.TRUE.equals(pkyc)) {
            facts.put("kyc.vkyc.completed", true);
            facts.put("kyc.pkyc.completed", true);
            facts.put("kyc.vkyc.result", "PKYC_COMPLETED");
            return;
        }
        Object vkycStatus = hints.get("vkycStatus");
        if (vkycStatus != null) {
            String s = String.valueOf(vkycStatus).trim().toUpperCase(Locale.ROOT);
            facts.put("kyc.vkyc.result", s);
            if ("COMPLETED".equals(s) || "APPROVED".equals(s) || "SUCCESS".equals(s)) {
                facts.put("kyc.vkyc.completed", true);
            } else if ("REJECTED".equals(s) || "FAILED".equals(s)) {
                facts.put("kyc.vkyc.completed", false);
            }
        }
        StepEvidence video = latest(list, KycStepType.VIDEO_KYC);
        if (video != null && !facts.containsKey("kyc.vkyc.completed")) {
            var c = KycStepOutcomeSemantics.classify(
                    video.outcome(), video.errorMessage(), video.parsedData(), video.fallbackUsed());
            if (c.businessOutcome() == KycBusinessOutcome.PASS) {
                facts.put("kyc.vkyc.completed", true);
                facts.put("kyc.vkyc.result", "SUCCESS");
            } else if (c.businessOutcome() == KycBusinessOutcome.FAIL) {
                facts.put("kyc.vkyc.completed", false);
                facts.put("kyc.vkyc.result", "FAILED");
            }
        }
    }

    private static KycBusinessOutcome deriveOverall(
            Map<String, Object> facts,
            List<StepEvidence> list,
            String legacyAggregateOutcome
    ) {
        boolean anyRefer = list.stream().anyMatch(s ->
                KycStepOutcomeSemantics.classify(s.outcome(), s.errorMessage(), s.parsedData(), s.fallbackUsed())
                        .businessOutcome() == KycBusinessOutcome.REFER);
        if (anyRefer) {
            return KycBusinessOutcome.REFER;
        }
        boolean anyFail = list.stream().anyMatch(s ->
                KycStepOutcomeSemantics.classify(s.outcome(), s.errorMessage(), s.parsedData(), s.fallbackUsed())
                        .businessOutcome() == KycBusinessOutcome.FAIL);
        if (anyFail) {
            return KycBusinessOutcome.FAIL;
        }
        boolean anyMissing = list.stream().anyMatch(s -> {
            var c = KycStepOutcomeSemantics.classify(s.outcome(), s.errorMessage(), s.parsedData(), s.fallbackUsed());
            return c.businessOutcome() == KycBusinessOutcome.MISSING_INFORMATION
                    || c.technicalStatus().indicatesProviderContinuityIssue();
        });
        if (anyMissing) {
            return KycBusinessOutcome.MISSING_INFORMATION;
        }
        if (legacyAggregateOutcome != null) {
            return KycBusinessOutcome.fromLegacyAggregate(legacyAggregateOutcome);
        }
        if (list.isEmpty()) {
            return KycBusinessOutcome.MISSING_INFORMATION;
        }
        return KycBusinessOutcome.PASS;
    }

    private static KycTechnicalStatus deriveOverallTechnical(List<StepEvidence> list) {
        boolean fallback = false;
        for (StepEvidence s : list) {
            var c = KycStepOutcomeSemantics.classify(s.outcome(), s.errorMessage(), s.parsedData(), s.fallbackUsed());
            if (c.technicalStatus().indicatesProviderContinuityIssue()) {
                return c.technicalStatus();
            }
            if (c.fallbackUsed() || c.technicalStatus() == KycTechnicalStatus.FALLBACK_USED) {
                fallback = true;
            }
        }
        return fallback ? KycTechnicalStatus.FALLBACK_USED : KycTechnicalStatus.SUCCESS;
    }

    private static StepEvidence latest(List<StepEvidence> list, KycStepType type) {
        StepEvidence found = null;
        for (StepEvidence s : list) {
            if (s != null && s.stepType() == type) {
                found = s;
            }
        }
        return found;
    }

    private static void putPresent(Map<String, Object> facts, String key, Object value) {
        if (value instanceof Boolean b) {
            facts.put(key, b);
        }
    }

    private static Boolean extractNameMatch(Map<String, Object> parsed) {
        if (parsed == null) {
            return null;
        }
        Object v = parsed.get("nameMatch");
        if (v == null) {
            v = parsed.get("name_match");
        }
        if (v == null) {
            v = parsed.get("matchResult");
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            String s = String.valueOf(v).trim().toUpperCase(Locale.ROOT);
            if ("TRUE".equals(s) || "MATCH".equals(s) || "Y".equals(s) || "YES".equals(s)) {
                return true;
            }
            if ("FALSE".equals(s) || "MISMATCH".equals(s) || "N".equals(s) || "NO".equals(s)) {
                return false;
            }
        }
        return null;
    }
}
