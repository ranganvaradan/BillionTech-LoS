package com.billiontech.bankstatement.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyRequest {
    @NotBlank(message = "API key name is required")
    private String name;
    private String description;
    private Integer expiresInDays;
}
