package com.los.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AnchorRatingTemplateRequest {

    @NotBlank
    private String name;

    private int version = 1;

    private boolean active;

    @NotNull
    private Map<String, Object> configJson;
}
