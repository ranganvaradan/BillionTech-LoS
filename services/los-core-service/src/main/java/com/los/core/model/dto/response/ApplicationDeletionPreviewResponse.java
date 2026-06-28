package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApplicationDeletionPreviewResponse {

    private String applicationId;
    private String applicationNumber;
    private String loanProduct;
    private String intakeSegment;
    private String status;
    private boolean borrowerApplication;
    private boolean invoiceDiscountingBorrower;
    private boolean plpLinked;
    private boolean requiresDoubleConfirm;
    private String warningMessage;
    private String summaryMessage;
}
