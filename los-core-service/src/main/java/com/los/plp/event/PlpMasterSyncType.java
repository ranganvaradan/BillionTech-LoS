package com.los.plp.event;

/**
 * Master entities that LOS pushes to PLP after the owning LOS transaction commits.
 */
public enum PlpMasterSyncType {
    ANCHOR,
    PROGRAM,
    SUB_PROGRAM
}
