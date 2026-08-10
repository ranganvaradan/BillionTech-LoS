package com.los.core.creditintelligence.cutover.domain;

/**
 * G0.1 limited-pilot certification statuses (§33).
 * LIMITED_PILOT_READY requires meeting real/stored minimum; sample-size exceptions yield READY_WITH_EXCEPTIONS only.
 */
public enum LimitedPilotCertificationStatus {
    NOT_READY,
    READY_WITH_EXCEPTIONS,
    LIMITED_PILOT_READY,
    REVOKED
}
