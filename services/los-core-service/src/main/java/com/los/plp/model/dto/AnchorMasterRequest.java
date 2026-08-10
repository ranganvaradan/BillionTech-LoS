package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class AnchorMasterRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String pan;
    private String gstin;
    private String email;
    private String mobile;
    private String address;
    private UUID sourceAnchorApplicationId;
}
