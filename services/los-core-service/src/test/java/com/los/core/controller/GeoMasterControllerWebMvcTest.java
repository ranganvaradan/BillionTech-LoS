package com.los.core.controller;

import com.los.core.config.MasterDataCacheConfig;
import com.los.core.config.WebSecurityConfig;
import com.los.core.model.dto.response.GeoCityResponse;
import com.los.core.model.dto.response.GeoStateResponse;
import com.los.core.service.master.GeoMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GeoMasterController.class)
@Import({WebSecurityConfig.class, MasterDataCacheConfig.class})
class GeoMasterControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeoMasterService geoMasterService;

    private static final UUID SID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void listStates_returnsAlphabeticalStates() throws Exception {
        when(geoMasterService.listActiveStates()).thenReturn(List.of(
                new GeoStateResponse(SID, "KA", "Karnataka"),
                new GeoStateResponse(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "TN", "Tamil Nadu")
        ));

        mockMvc.perform(get("/api/v1/master/states").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stateName").value("Karnataka"))
                .andExpect(jsonPath("$[1].stateName").value("Tamil Nadu"));
    }

    @Test
    void listCities_returnsCitiesForState() throws Exception {
        when(geoMasterService.listActiveCitiesForState(SID)).thenReturn(List.of(
                new GeoCityResponse(UUID.randomUUID(), "Bengaluru", null)
        ));

        mockMvc.perform(get("/api/v1/master/states/" + SID + "/cities").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cityName").value("Bengaluru"));
    }
}
