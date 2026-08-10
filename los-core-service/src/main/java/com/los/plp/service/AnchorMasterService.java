package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.event.PlpMasterSyncRequestedEvent;
import com.los.plp.event.PlpMasterSyncType;
import com.los.plp.model.dto.AnchorMasterRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorMasterService {

    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpProperties plpProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AnchorMaster create(AnchorMasterRequest request) {
        AnchorMaster anchor = AnchorMaster.builder()
                .code(request.getCode())
                .name(request.getName())
                .pan(request.getPan())
                .gstin(request.getGstin())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .address(request.getAddress())
                .sourceAnchorApplicationId(request.getSourceAnchorApplicationId())
                .plpAnchorSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
        anchor = anchorMasterRepository.save(anchor);
        publishSyncAfterCommit(anchor.getId());
        return anchor;
    }

    @Transactional
    public AnchorMaster update(UUID id, AnchorMasterRequest request) {
        AnchorMaster anchor = anchorMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + id));
        anchor.setCode(request.getCode());
        anchor.setName(request.getName());
        anchor.setPan(request.getPan());
        anchor.setGstin(request.getGstin());
        anchor.setEmail(request.getEmail());
        anchor.setMobile(request.getMobile());
        anchor.setAddress(request.getAddress());
        anchor.setSourceAnchorApplicationId(request.getSourceAnchorApplicationId());
        anchor = anchorMasterRepository.save(anchor);
        publishSyncAfterCommit(anchor.getId());
        return anchor;
    }

    @Transactional(readOnly = true)
    public AnchorMaster get(UUID id) {
        return anchorMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AnchorMaster> list() {
        return anchorMasterRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AnchorMaster> listSyncedToPlp() {
        return anchorMasterRepository.findByPlpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
    }

    private void publishSyncAfterCommit(UUID anchorId) {
        if (!plpProperties.isEnabled()) {
            return;
        }
        eventPublisher.publishEvent(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.ANCHOR, anchorId));
    }
}
