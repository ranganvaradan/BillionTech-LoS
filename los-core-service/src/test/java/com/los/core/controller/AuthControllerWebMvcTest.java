package com.los.core.controller;

import com.los.core.config.WebSecurityConfig;
import com.los.core.model.dto.auth.LoginResponse;
import com.los.core.service.auth.DemoAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(WebSecurityConfig.class)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoAuthService demoAuthService;

    @Test
    void login_returnsBody() throws Exception {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        when(demoAuthService.login(any())).thenReturn(LoginResponse.builder()
                .userId(id)
                .name("Sahil C")
                .email("sahil@gmail.com")
                .role("BORROWER")
                .institution(DemoAuthService.DEMO_INSTITUTION)
                .build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sahil@gmail.com\",\"password\":\"Bltest@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.role").value("BORROWER"));
    }

    @Test
    void register_returns201() throws Exception {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000002");
        when(demoAuthService.register(any())).thenReturn(LoginResponse.builder()
                .userId(id)
                .name("New B")
                .email("newb@example.com")
                .role("BORROWER")
                .institution(DemoAuthService.DEMO_INSTITUTION)
                .build());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New B\",\"email\":\"newb@example.com\",\"mobile\":\"9876543210\","
                                + "\"password\":\"Abcd@1234\",\"confirmPassword\":\"Abcd@1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.role").value("BORROWER"));
    }
}
