package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.model.enums.StepOutcome;

import java.util.Locale;
import java.util.Map;

/**
 * Separates technical/provider execution from borrower verification result.
 * Adapter only — production {@code computeKycOutcome} is unchanged and still collapses FAILURE.
 */
public final class KycStepOutcomeSemantics {

    private KycStepOutcomeSemantics() {}

    public record ClassifiedStep(
            KycTechnicalStatus technicalStatus,
            KycBusinessOutcome businessOutcome,
            boolean fallbackUsed,
            String reasonCode
    ) {}

    public static ClassifiedStep classify(StepOutcome outcome, String errorMessage, Map<String, Object> parsedData) {
        return classify(outcome, errorMessage, parsedData, false);
    }

    public static ClassifiedStep classify(
            StepOutcome outcome,
            String errorMessage,
            Map<String, Object> parsedData,
            boolean fallbackUsed
    ) {
        if (outcome == null || outcome == StepOutcome.PENDING) {
            return new ClassifiedStep(
                    KycTechnicalStatus.PENDING,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    "STEP_PENDING");
        }
        if (outcome == StepOutcome.SKIPPED) {
            return new ClassifiedStep(
                    KycTechnicalStatus.SKIPPED,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    "STEP_SKIPPED");
        }
        if (outcome == StepOutcome.MANUAL_REVIEW) {
            return new ClassifiedStep(
                    KycTechnicalStatus.AVAILABLE,
                    KycBusinessOutcome.REFER,
                    fallbackUsed,
                    "MANUAL_REVIEW_REQUIRED");
        }
        if (outcome == StepOutcome.SUCCESS) {
            KycTechnicalStatus tech = fallbackUsed ? KycTechnicalStatus.FALLBACK_USED : KycTechnicalStatus.SUCCESS;
            return new ClassifiedStep(tech, KycBusinessOutcome.PASS, fallbackUsed, "VERIFIED");
        }
        if (outcome == StepOutcome.ERROR) {
            KycTechnicalStatus tech = isTimeout(errorMessage) ? KycTechnicalStatus.TIMEOUT : KycTechnicalStatus.ERROR;
            return new ClassifiedStep(
                    tech,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    tech == KycTechnicalStatus.TIMEOUT ? "PROVIDER_TIMEOUT" : "PROVIDER_ERROR");
        }
        // FAILURE — distinguish technical unavailability from borrower verification failure
        if (isProviderUnavailable(errorMessage)) {
            return new ClassifiedStep(
                    KycTechnicalStatus.UNAVAILABLE,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    "PROVIDER_UNAVAILABLE");
        }
        if (isTimeout(errorMessage)) {
            return new ClassifiedStep(
                    KycTechnicalStatus.TIMEOUT,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    "PROVIDER_TIMEOUT");
        }
        if (looksLikeTechnicalError(errorMessage)) {
            return new ClassifiedStep(
                    KycTechnicalStatus.ERROR,
                    KycBusinessOutcome.MISSING_INFORMATION,
                    fallbackUsed,
                    "PROVIDER_ERROR");
        }
        // Conclusive borrower/business verification failure
        return new ClassifiedStep(
                KycTechnicalStatus.AVAILABLE,
                KycBusinessOutcome.FAIL,
                fallbackUsed,
                "VERIFICATION_FAILED");
    }

    /**
     * Safety: technical continuity issues never yield borrower FAIL.
     */
    public static boolean technicalIssueImpliesBorrowerFail(ClassifiedStep step) {
        return false;
    }

    public static boolean isProviderUnavailable(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String e = errorMessage.toLowerCase(Locale.ROOT);
        return e.contains("no registered provider")
                || e.contains("no kyc provider could handle")
                || e.contains("no kyc provider")
                || e.contains("provider unavailable")
                || e.contains("service unavailable")
                || e.contains("circuit breaker")
                || e.contains("connection refused")
                || e.contains("unknown host");
    }

    public static boolean isTimeout(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String e = errorMessage.toLowerCase(Locale.ROOT);
        return e.contains("timeout") || e.contains("timed out") || e.contains("deadline exceeded");
    }

    private static boolean looksLikeTechnicalError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String e = errorMessage.toLowerCase(Locale.ROOT);
        return e.contains("http 5")
                || e.contains("502")
                || e.contains("503")
                || e.contains("504")
                || e.contains("internal server error")
                || e.contains("ioexception")
                || e.contains("socket");
    }
}
