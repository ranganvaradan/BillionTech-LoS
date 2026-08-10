package com.los.plp.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VintageEligibilityResponse {

    private BigDecimal borrowerDependencyVintagePercent;
    private Integer borrowerAnchorRelationshipVintageMonths;

    private BigDecimal programDependencyVintagePercent;
    private Integer programAnchorRelationshipVintageMonths;

    /** ELIGIBLE | LOWER | NOT_CONFIGURED */
    private String dependencyVintageStatus;
    private String dependencyVintageMessage;

    /** ELIGIBLE | LOWER | NOT_CONFIGURED */
    private String anchorVintageStatus;
    private String anchorVintageMessage;
}
