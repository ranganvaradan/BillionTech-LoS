package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.LiveAccountDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Versioned live-account classifier: {@link LiveAccountDefinition#BUREAU_LIVE_ACCOUNT_DEFINITION_V1}.
 */
@Component
public class BureauLiveAccountClassifier {

    public record LiveInput(
            String accountStatus,
            BigDecimal currentBalance,
            boolean writtenOff,
            boolean settled,
            LocalDate lastReportedDate,
            Boolean openIndicator) {
    }

    public record LiveClassification(boolean live, String reason, String definitionVersion, String qualityFlag) {
    }

    public LiveClassification classify(LiveInput input, int freshnessDays) {
        return classify(input, freshnessDays, LocalDate.now());
    }

    public LiveClassification classify(LiveInput input, int freshnessDays, LocalDate asOf) {
        String def = LiveAccountDefinition.BUREAU_LIVE_ACCOUNT_DEFINITION_V1;
        if (input == null) {
            return new LiveClassification(false, "NULL_INPUT", def, null);
        }

        String status = input.accountStatus() != null
                ? input.accountStatus().trim().toUpperCase(Locale.ROOT) : "";
        BigDecimal bal = input.currentBalance() != null ? input.currentBalance() : BigDecimal.ZERO;
        boolean positiveBalance = bal.compareTo(BigDecimal.ZERO) > 0;
        boolean explicitlyClosed = isExplicitlyClosed(status, input.openIndicator());
        boolean activeOrOpen = isActiveOrOpen(status, input.openIndicator());

        // Written-off / settled with zero balance = NOT LIVE
        if ((input.writtenOff() || input.settled()) && !positiveBalance) {
            return new LiveClassification(false,
                    input.writtenOff() ? "WRITTEN_OFF_ZERO_BALANCE" : "SETTLED_ZERO_BALANCE",
                    def, null);
        }
        // Written-off/settled with positive balance + active status may still be LIVE (documented assumption)
        if ((input.writtenOff() || input.settled()) && positiveBalance && !activeOrOpen) {
            return new LiveClassification(false, "WRITTEN_OFF_OR_SETTLED_NOT_ACTIVE", def, null);
        }

        if (explicitlyClosed && !positiveBalance) {
            return new LiveClassification(false, "CLOSED_ZERO_BALANCE", def, null);
        }
        // Settled as terminal historical if settled + zero balance + no open indicator (covered above)

        boolean openOrBalance = activeOrOpen || positiveBalance;
        if (!openOrBalance) {
            return new LiveClassification(false, "NOT_OPEN_AND_ZERO_BALANCE", def, null);
        }

        String quality = null;
        if (input.lastReportedDate() == null) {
            quality = "WARNING";
            return new LiveClassification(true, "LIVE_UNKNOWN_FRESHNESS", def, quality);
        }

        int days = freshnessDays > 0 ? freshnessDays : 365;
        LocalDate cutoff = asOf.minusDays(days);
        if (input.lastReportedDate().isBefore(cutoff)) {
            return new LiveClassification(false, "STALE_LAST_REPORTED", def, "STALE");
        }

        return new LiveClassification(true, "LIVE", def, quality);
    }

    private static boolean isExplicitlyClosed(String statusUpper, Boolean openIndicator) {
        if (Boolean.FALSE.equals(openIndicator)) {
            return true;
        }
        if (statusUpper.isEmpty()) {
            return false;
        }
        return statusUpper.equals("CLOSED")
                || statusUpper.contains("CLOSED")
                || statusUpper.equals("SETTLED")
                || statusUpper.startsWith("SETTLED");
    }

    private static boolean isActiveOrOpen(String statusUpper, Boolean openIndicator) {
        if (Boolean.TRUE.equals(openIndicator)) {
            return true;
        }
        if (statusUpper.isEmpty()) {
            return false;
        }
        return statusUpper.contains("ACTIVE")
                || statusUpper.contains("CURRENT")
                || statusUpper.contains("OPEN")
                || statusUpper.equals("OVERDUE")
                || statusUpper.contains("DELINQUENT");
    }
}
