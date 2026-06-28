package com.los.core.service.master;

import com.los.core.config.MasterDataCacheConfig;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.GeoCityResponse;
import com.los.core.model.dto.response.GeoStateResponse;
import com.los.core.model.entity.CityMaster;
import com.los.core.model.entity.StateMaster;
import com.los.core.repository.CityMasterRepository;
import com.los.core.repository.StateMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeoMasterService {

    private final StateMasterRepository stateMasterRepository;
    private final CityMasterRepository cityMasterRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MasterDataCacheConfig.GEO_STATES)
    public List<GeoStateResponse> listActiveStates() {
        return stateMasterRepository.findAllByActiveTrueOrderByStateNameAsc().stream()
                .map(GeoMasterService::toStateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MasterDataCacheConfig.GEO_CITIES_BY_STATE, key = "#stateId")
    public List<GeoCityResponse> listActiveCitiesForState(UUID stateId) {
        if (stateId == null) {
            return List.of();
        }
        StateMaster st = stateMasterRepository.findByIdAndActiveTrue(stateId)
                .orElseThrow(() -> new ResourceNotFoundException("State not found or inactive: " + stateId));
        return cityMasterRepository.findAllByState_IdAndActiveTrueOrderByCityNameAsc(st.getId()).stream()
                .map(GeoMasterService::toCityResponse)
                .toList();
    }

    private static GeoStateResponse toStateResponse(StateMaster s) {
        return new GeoStateResponse(s.getId(), s.getStateCode(), s.getStateName());
    }

    private static GeoCityResponse toCityResponse(CityMaster c) {
        return new GeoCityResponse(c.getId(), c.getCityName(), c.getCityCode());
    }
}
