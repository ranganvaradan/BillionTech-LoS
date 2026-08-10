package com.los.plp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpApplicationCleanupResponse {

    private String summary;
    private int loansRemoved;
    private int invoicesRemoved;
    private boolean borrowerRemoved;
}
