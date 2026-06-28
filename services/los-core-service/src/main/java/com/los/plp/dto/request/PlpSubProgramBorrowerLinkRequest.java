package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlpSubProgramBorrowerLinkRequest {
    private String sourceSystem;
    private String subProgramId;
    private String borrowerId;
    private BigDecimal borrowerLimit;
    private BigDecimal utilizedLimit;
    private BigDecimal availableLimit;
}
