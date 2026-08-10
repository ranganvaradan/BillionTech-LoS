package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpProgramSyncData {
    private String plpProgramId;
    private String programCode;
    private Boolean created;
    private Boolean updated;
    /** PLP operational status: DRAFT, ACTIVE, PAUSED, CLOSED */
    private String status;
}
