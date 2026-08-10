package com.los.core.controller;

import com.los.core.config.WebSecurityConfig;
import com.los.core.model.dto.response.StepExecutionRecordView;
import com.los.core.service.flow.step.StepExecutionReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StepExecutionQueryController.class)
@Import(WebSecurityConfig.class)
class StepExecutionQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StepExecutionReadService stepExecutionReadService;

    @Test
    void getStepExecutions_returnsJsonArrayForApplication() throws Exception {
        UUID app = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        Instant t = Instant.parse("2025-04-25T10:00:00Z");
        when(stepExecutionReadService.listStepExecutionRecords(eq(app)))
                .thenReturn(List.of(
                        new StepExecutionRecordView(
                                UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c0"),
                                app,
                                "KYC_WORKFLOW",
                                "SUCCESS",
                                "{\"k\":1}",
                                "{\"o\":2}",
                                null,
                                null,
                                t,
                                t)));

        mockMvc.perform(get("/api/v1/applications/{applicationId}/step-executions", app)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stepType").value("KYC_WORKFLOW"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].inputJson").value("{\"k\":1}"));
        verify(stepExecutionReadService).listStepExecutionRecords(app);
    }

    @Test
    void getStepExecutions_emptyList() throws Exception {
        UUID app = UUID.randomUUID();
        when(stepExecutionReadService.listStepExecutionRecords(app)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/applications/{applicationId}/step-executions", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
