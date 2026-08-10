package com.los.core.controller;

import com.los.core.model.dto.response.GeoCityResponse;
import com.los.core.model.dto.response.GeoStateResponse;
import com.los.core.service.master.GeoMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/master", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Geo master", description = "India state/city read-only master data")
public class GeoMasterController {

    private final GeoMasterService geoMasterService;

    @GetMapping("/states")
    @Operation(summary = "List active states (sorted by name)")
    public List<GeoStateResponse> listStates() {
        return geoMasterService.listActiveStates();
    }

    @GetMapping("/states/{stateId}/cities")
    @Operation(summary = "List active cities for a state (sorted by name)")
    public List<GeoCityResponse> listCitiesForState(@PathVariable("stateId") UUID stateId) {
        return geoMasterService.listActiveCitiesForState(stateId);
    }
}
