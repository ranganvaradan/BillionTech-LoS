package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpAnchorSyncData {
    private String plpAnchorId;
    private String anchorCode;
    private Boolean created;
    private Boolean updated;
}
