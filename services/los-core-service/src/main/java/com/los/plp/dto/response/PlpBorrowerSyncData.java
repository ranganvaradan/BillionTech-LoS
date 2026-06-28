package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpBorrowerSyncData {
    private String plpBorrowerId;
    private String borrowerCode;
    private Boolean created;
    private Boolean updated;
}
