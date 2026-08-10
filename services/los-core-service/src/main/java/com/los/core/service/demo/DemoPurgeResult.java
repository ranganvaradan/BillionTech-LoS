package com.los.core.service.demo;

/**
 * Result of a full LOS demo purge (applications, borrowers, LOS-local PLP master cache).
 */
public record DemoPurgeResult(
        int deletedApplications,
        int deletedBorrowerUsers,
        int deletedLosPlpSubPrograms,
        int deletedLosPlpPrograms,
        int deletedLosPlpAnchors) {

    public int deletedLosPlpMasterRows() {
        return deletedLosPlpSubPrograms + deletedLosPlpPrograms + deletedLosPlpAnchors;
    }
}
