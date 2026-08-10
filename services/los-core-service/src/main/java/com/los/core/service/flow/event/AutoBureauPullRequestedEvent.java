package com.los.core.service.flow.event;

import java.util.UUID;

/**
 * Internal event raised when KYC becomes PASS and bureau pull can be auto-triggered.
 */
public record AutoBureauPullRequestedEvent(UUID applicationId, String triggerSource) {
}
