package com.los.encore.client.logging;

/**
 * Structured LMS integration log event codes (enterprise traceability).
 */
public enum LmsLogEvent {
    LMS_REQUEST_INITIATED,
    LMS_REQUEST_SUCCESS,
    LMS_REQUEST_FAILED,
    LMS_REQUEST_RETRY,
    LMS_CALLBACK_RECEIVED,
    LMS_SYNC_COMPLETED,
    LMS_SYNC_RETRY
}
