package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Read model for eSign rows — no raw provider JSON. */
@Data
@Builder
public class EsignRequestView {
    private UUID id;
    private String documentType;
    private String provider;
    private String providerRequestId;
    private String signingUrl;
    private String status;
    private String signerName;
    private String signedDocumentUrl;
    private Instant createdAt;
    private Instant signedAt;
}
