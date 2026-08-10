package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PlpSyncResultResponse {
    private UUID id;
    private PlpSyncStatus syncStatus;
    private String syncError;
    private Instant syncedAt;
    private UUID plpId;
}
