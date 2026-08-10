package com.los.plp.mapper;

import com.los.plp.dto.request.PlpAnchorSyncRequest;
import com.los.plp.model.entity.AnchorMaster;

public final class PlpAnchorPayloadMapper {

    /** PLP {@code anchors.anchor_code} and API validation cap at 20 characters. */
    private static final int PLP_ANCHOR_CODE_MAX_LEN = 20;

    private PlpAnchorPayloadMapper() {
    }

    public static PlpAnchorSyncRequest toRequest(AnchorMaster anchor) {
        return PlpAnchorSyncRequest.builder()
                .losAnchorId(anchor.getId().toString())
                .anchor(PlpAnchorSyncRequest.AnchorPayload.builder()
                        .name(anchor.getName())
                        .code(truncateForPlp(anchor.getCode(), PLP_ANCHOR_CODE_MAX_LEN))
                        .pan(anchor.getPan())
                        .gstin(anchor.getGstin())
                        .email(anchor.getEmail())
                        .mobile(anchor.getMobile())
                        .address(anchor.getAddress())
                        .build())
                .build();
    }

    static String truncateForPlp(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(value.length() - maxLen);
    }
}
