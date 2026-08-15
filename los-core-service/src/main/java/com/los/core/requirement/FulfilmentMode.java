package com.los.core.requirement;

/**
 * How a requirement may be satisfied. A requirement may allow more than one mode.
 */
public enum FulfilmentMode {
    DIRECT_INPUT,
    DOCUMENT_UPLOAD,
    AUTOMATIC_SOURCE,
    DERIVATION,
    MANUAL_REVIEW
}
