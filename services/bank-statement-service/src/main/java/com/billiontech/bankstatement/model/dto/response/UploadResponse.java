package com.billiontech.bankstatement.model.dto.response;

import com.billiontech.bankstatement.model.enums.ParsingStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponse {
    private Long statementId;
    private String fileName;
    private ParsingStatus status;
    private String message;
}
