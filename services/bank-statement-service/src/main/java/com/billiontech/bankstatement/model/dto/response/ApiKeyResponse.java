package com.billiontech.bankstatement.model.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {
    private Long id;
    private String apiKey;
    private String keyPrefix;
    private String name;
    private String description;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
