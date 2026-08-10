package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpAnchorSyncRequest {
    private String sourceSystem;
    private String losAnchorId;
    private AnchorPayload anchor;

    @Data
    @Builder
    public static class AnchorPayload {
        private String name;
        private String code;
        private String pan;
        private String gstin;
        private String email;
        private String mobile;
        private String address;
    }
}
