package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.domain.SourceType;
import org.springframework.stereotype.Component;

/**
 * BANK_SOURCE_PRECEDENCE_V1: AA_STRUCTURED > BANK_API > DIGITAL_STATEMENT > OCR > MANUAL.
 */
@Component
public class BankSourcePrecedence {

    public enum BankingSourceRank {
        AA_STRUCTURED(100),
        BANK_API(80),
        DIGITAL_STATEMENT(60),
        OCR(40),
        MANUAL(20),
        UNKNOWN(0);

        private final int rank;

        BankingSourceRank(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public BankingSourceRank resolve(String sourceType, String providerCode) {
        if (sourceType == null) {
            return BankingSourceRank.UNKNOWN;
        }
        String st = sourceType.toUpperCase();
        if (SourceType.ACCOUNT_AGGREGATOR.name().equals(st)
                || "AA_STRUCTURED".equals(st)) {
            return BankingSourceRank.AA_STRUCTURED;
        }
        if ("BANK_API".equals(st) || "OPEN_BANKING".equals(st)) {
            return BankingSourceRank.BANK_API;
        }
        if ("DIGITAL_STATEMENT".equals(st) || "PDF_PARSED".equals(st)) {
            return BankingSourceRank.DIGITAL_STATEMENT;
        }
        if (SourceType.BANK_STATEMENT.name().equals(st) || "OCR".equals(st)) {
            return BankingSourceRank.OCR;
        }
        if ("MANUAL".equals(st) || SourceType.MANUAL_DECLARATION.name().equals(st)) {
            return BankingSourceRank.MANUAL;
        }
        if (providerCode != null && providerCode.toUpperCase().contains("AA")) {
            return BankingSourceRank.AA_STRUCTURED;
        }
        return BankingSourceRank.UNKNOWN;
    }

    public boolean prefers(BankingSourceRank candidate, BankingSourceRank existing) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        return candidate.rank() > existing.rank();
    }

    public String methodVersion() {
        return BankingConstants.BANK_SOURCE_PRECEDENCE_V1;
    }
}
