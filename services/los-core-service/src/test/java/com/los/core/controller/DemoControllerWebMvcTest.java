package com.los.core.controller;

import com.los.core.config.WebSecurityConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.demo.DemoApplicationPurgeService;
import com.los.core.service.demo.DemoModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DemoController.class)
@Import(WebSecurityConfig.class)
@TestPropertySource(properties = "los.demo.enabled=true")
class DemoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoModeService demoModeService;
    @MockBean
    private LoanApplicationRepository loanApplicationRepository;
    @MockBean
    private DemoApplicationPurgeService demoApplicationPurgeService;

    @Test
    void status_returnsDemoFlagsAndCount() throws Exception {
        when(demoModeService.isDemoModeEnabled()).thenReturn(true);
        when(demoModeService.getActiveProfilesDisplay()).thenReturn("test");
        when(loanApplicationRepository.count()).thenReturn(4L);

        mockMvc.perform(get("/api/v1/demo/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoEnabled").value(true))
                .andExpect(jsonPath("$.profile").value("test"))
                .andExpect(jsonPath("$.applicationCount").value(4));
    }

    @Test
    void deleteApplications_invokesPurgeService_andReturnsSuccess() throws Exception {
        when(demoModeService.isDemoModeEnabled()).thenReturn(true);
        when(demoApplicationPurgeService.deleteAllApplicationsAndDependents()).thenReturn(2);

        mockMvc.perform(delete("/api/v1/demo/applications").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedApplications").value(2))
                .andExpect(jsonPath("$.status").value("success"));

        verify(demoApplicationPurgeService).deleteAllApplicationsAndDependents();
    }

    @Test
    void deleteApplications_returns404_whenDemoModeDisabled() throws Exception {
        when(demoModeService.isDemoModeEnabled()).thenReturn(false);

        mockMvc.perform(delete("/api/v1/demo/applications").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(DemoModeService.DEMO_MODE_DISABLED_MESSAGE));
    }

    @Test
    void deleteApplications_returns500_whenPurgeThrows() throws Exception {
        when(demoModeService.isDemoModeEnabled()).thenReturn(true);
        when(demoApplicationPurgeService.deleteAllApplicationsAndDependents())
                .thenThrow(new RuntimeException("simulated FK error"));

        mockMvc.perform(delete("/api/v1/demo/applications").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to clear demo data"))
                .andExpect(jsonPath("$.reason").value("DEMO_DELETE_FAILED"));
    }
}
