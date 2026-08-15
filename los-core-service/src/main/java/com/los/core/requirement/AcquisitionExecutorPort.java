package com.los.core.requirement;

import java.util.UUID;

/**
 * Port for existing source executors. W6 adapters implement this — no parallel engines.
 */
public interface AcquisitionExecutorPort {

    /** Normalized source key this executor handles (BUREAU, KYC, ACCOUNT_AGGREGATOR, …). */
    String sourceKey();

    boolean supports(String sourceKey);

    AcquisitionDtos.ExecutorOutcome execute(ExecutionContext ctx);

    record ExecutionContext(
            UUID applicationId,
            UUID planId,
            int planVersion,
            RequirementItemEntity item,
            String sourceKey,
            String actor,
            boolean dryRun
    ) {}
}
