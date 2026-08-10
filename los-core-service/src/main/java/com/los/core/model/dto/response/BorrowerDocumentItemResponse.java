package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class BorrowerDocumentItemResponse {
    UUID id;
    /** UPLOAD = documents table; ESIGN = signed PDF from esign_requests. */
    String source;
    /** KYC or SIGNED — for borrower portal grouping. */
    String category;
    String documentType;
    String fileName;
    String contentType;
    long fileSize;
    Instant createdAt;
}
