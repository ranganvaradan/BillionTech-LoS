package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpSubProgramSyncData {
    private String plpSubProgramId;
    private String subProgramCode;
    private Boolean created;
    private Boolean updated;
}
