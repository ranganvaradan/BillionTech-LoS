package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpBorrowerSyncRequest {
    private String sourceSystem;
    private String losBorrowerId;
    private String plpProgramId;
    private String plpAnchorId;
    private BorrowerPayload borrower;

    @Data
    @Builder
    public static class BorrowerPayload {
        private String name;
        private String email;
        private String phone;
        private String pan;
        private String gstin;
    }
}
