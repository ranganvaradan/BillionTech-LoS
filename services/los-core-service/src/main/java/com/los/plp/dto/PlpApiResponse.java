package com.los.plp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpApiResponse<T> {
    private String status;
    private String message;
    private T data;
}
