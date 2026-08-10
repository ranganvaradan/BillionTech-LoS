package com.los.core.service.demo;

/**
 * Counts of LOS-local PLP master rows removed during demo purge (not the remote PLP app).
 */
public record DemoLosPlpMasterPurgeCounts(int subPrograms, int programs, int anchors) {

    public int total() {
        return subPrograms + programs + anchors;
    }
}
