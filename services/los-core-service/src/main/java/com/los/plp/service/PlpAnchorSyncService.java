package com.los.plp.service;

import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.response.PlpAnchorSyncData;
import com.los.plp.mapper.PlpAnchorPayloadMapper;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.support.PlpSyncSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpAnchorSyncService {

    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnchorMaster sync(UUID anchorId) {
        AnchorMaster anchor = anchorMasterRepository.findById(anchorId).orElse(null);
        if (anchor == null) {
            log.warn("PLP anchor sync skipped — anchor not found: {}", anchorId);
            return null;
        }
        try {
            PlpApiResponse<PlpAnchorSyncData> response =
                    plpIntegrationClient.syncAnchor(PlpAnchorPayloadMapper.toRequest(anchor));
            PlpAnchorSyncData data = response.getData();
            anchor.setPlpAnchorId(PlpSyncSupport.parseUuid(data.getPlpAnchorId()));
            anchor.setPlpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            anchor.setPlpAnchorSyncError(null);
            anchor.setPlpAnchorSyncedAt(PlpSyncSupport.now());
            log.info("PLP anchor sync success: losAnchorId={}, plpAnchorId={}", anchorId, data.getPlpAnchorId());
        } catch (Exception e) {
            anchor.setPlpAnchorSyncStatus(PlpSyncStatus.SYNC_FAILED);
            anchor.setPlpAnchorSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP anchor sync failed for {}: {}", anchorId, e.getMessage(), e);
        }
        return anchorMasterRepository.save(anchor);
    }
}
