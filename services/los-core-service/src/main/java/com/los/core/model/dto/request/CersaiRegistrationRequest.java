package com.los.core.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CersaiRegistrationRequest {

    private UUID applicationId;
    private UUID collateralValuationId;
    private String assetType;
    private String assetDescription;
    private String assetIdentifier;
    private String securityInterestType;
    private BigDecimal securedAmount;
    private String borrowerName;
    private String borrowerPan;
    private String lenderName;
    private String lenderCin;
}
