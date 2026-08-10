package com.los.core.service.esign;

/**
 * Values stored in {@code esign_requests.status} (string column; not an enum in DB for flexibility).
 */
public final class EsignRequestStatuses {
    /** Session created at provider / awaiting signer (embedded flow initiation). */
    public static final String INITIATED = "INITIATED";
    /** Legacy / generic in-progress naming; prefer {@link #INITIATED} for new EMSIGNER embedded rows. */
    public static final String PENDING = "PENDING";
    public static final String SIGNED = "SIGNED";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";

    private EsignRequestStatuses() {
    }
}
