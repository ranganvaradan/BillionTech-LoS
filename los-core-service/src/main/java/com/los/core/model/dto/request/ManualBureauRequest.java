package com.los.core.model.dto.request;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualBureauRequest {
    private Integer manualBureauScore;
    private String manualBureauRemarks;
    private UUID manualBureauDocumentId;
}
