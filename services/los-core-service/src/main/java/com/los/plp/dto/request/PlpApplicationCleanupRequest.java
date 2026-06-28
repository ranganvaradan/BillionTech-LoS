package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PlpApplicationCleanupRequest {

    private String sourceSystem = "LOS";
    private String losApplicationId;
    private UUID plpBorrowerId;
    private UUID plpSubProgramBorrowerId;
    private UUID plpBorrowerProgramMappingId;
  private boolean deleteBorrowerRecord;
}
