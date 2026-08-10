package com.los.plp.mapper;

import com.los.plp.dto.request.PlpAnchorSyncRequest;
import com.los.plp.model.entity.AnchorMaster;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlpAnchorPayloadMapperTest {

    @Test
    void toRequest_mapsLosAnchorIdAndFields() {
        UUID id = UUID.randomUUID();
        AnchorMaster anchor = AnchorMaster.builder()
                .id(id)
                .code("ACME")
                .name("Acme Ltd")
                .pan("ABCDE1234F")
                .build();

        PlpAnchorSyncRequest request = PlpAnchorPayloadMapper.toRequest(anchor);

        assertThat(request.getLosAnchorId()).isEqualTo(id.toString());
        assertThat(request.getAnchor().getCode()).isEqualTo("ACME");
        assertThat(request.getAnchor().getPan()).isEqualTo("ABCDE1234F");
    }
}
