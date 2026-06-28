package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpBorrowerProgramMappingData {
    private String plpBorrowerProgramMappingId;
    private String mappingStatus;
    private Boolean created;
    private Boolean updated;
}
