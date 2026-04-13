package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {

    private UUID id;
    private UUID applicationId;
    private String documentType;
    private String fileName;
    private String contentType;
    private long fileSize;
    private String checksum;
    private UUID uploadedBy;
    private Instant createdAt;
}
