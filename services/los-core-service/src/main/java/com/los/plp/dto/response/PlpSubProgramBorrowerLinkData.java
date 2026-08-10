package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpSubProgramBorrowerLinkData {
    private String plpSubProgramBorrowerId;
    private String status;
    private Boolean created;
    private Boolean updated;
}
