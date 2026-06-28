package com.los.plp.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PlpSyncedAnchorResponse {
    private UUID id;
    private String name;
    private String code;
    private String plpAnchorId;
    private UUID sourceAnchorApplicationId;
}
