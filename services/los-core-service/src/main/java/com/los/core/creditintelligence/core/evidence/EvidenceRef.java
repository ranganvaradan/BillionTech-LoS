package com.los.core.creditintelligence.core.evidence;

import java.util.UUID;

public record EvidenceRef(EvidenceType type, UUID id, String label) {
}
