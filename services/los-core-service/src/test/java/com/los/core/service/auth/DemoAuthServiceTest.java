package com.los.core.service.auth;

import com.los.core.exception.UnauthorizedException;
import com.los.core.model.dto.auth.ForgotPasswordRequest;
import com.los.core.model.dto.auth.LoginRequest;
import com.los.core.model.dto.auth.ResetPasswordRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAuthServiceTest {

    @Mock
    private LosUserRepository losUserRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private DemoAuthService demoAuthService;

    private UUID userId;

    @BeforeEach
    void wireEncoder() {
        userId = UUID.fromString("a1000000-0000-0000-0000-000000000002");
        demoAuthService = new DemoAuthService(losUserRepository, encoder);
    }

    @Test
    void login_success() {
        LosUser u = LosUser.builder()
                .id(userId)
                .name("Mohit C")
                .email("mohit@billionloans.com")
                .active(true)
                .passwordHash(encoder.encode("Bltest@123"))
                .primaryLosRole("CREDIT_MANAGER")
                .build();
        when(losUserRepository.findByEmailIgnoreCase("mohit@billionloans.com")).thenReturn(Optional.of(u));

        var res = demoAuthService.login(login("mohit@billionloans.com", "Bltest@123"));
        assertEquals(userId, res.getUserId());
        assertEquals("CREDIT_MANAGER", res.getRole());
        assertEquals(DemoAuthService.DEMO_INSTITUTION, res.getInstitution());
    }

    @Test
    void login_fails_wrong_password() {
        LosUser u = LosUser.builder()
                .id(userId)
                .name("X")
                .email("a@b.com")
                .active(true)
                .passwordHash(encoder.encode("Bltest@123"))
                .primaryLosRole("CREDIT_OFFICER")
                .build();
        when(losUserRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));
        assertThrows(UnauthorizedException.class, () -> demoAuthService.login(login("a@b.com", "wrong")));
    }

    @Test
    void forgotPassword_generatesToken() {
        LosUser u = LosUser.builder()
                .id(userId)
                .name("A")
                .email("a@b.com")
                .active(true)
                .passwordHash(encoder.encode("x"))
                .primaryLosRole("CREDIT_OFFICER")
                .build();
        when(losUserRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));
        when(losUserRepository.save(any(LosUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new ForgotPasswordRequest();
        req.setEmail("a@b.com");
        var res = demoAuthService.forgotPassword(req);
        assertNotNull(res.getResetToken());
        assertTrue(res.getResetPath().contains("token="));

        ArgumentCaptor<LosUser> cap = ArgumentCaptor.forClass(LosUser.class);
        verify(losUserRepository).save(cap.capture());
        assertNotNull(cap.getValue().getPasswordResetToken());
        assertNotNull(cap.getValue().getPasswordResetTokenExpiresAt());
    }

    @Test
    void resetPassword_updatesHash_andClearsToken() {
        String oldHash = encoder.encode("Bltest@123");
        LosUser u = LosUser.builder()
                .id(userId)
                .name("A")
                .email("a@b.com")
                .active(true)
                .passwordHash(oldHash)
                .primaryLosRole("CREDIT_OFFICER")
                .passwordResetToken("deadbeefcafebabe00000000dead0000deadbeefcafebabe00000000dead00")
                .passwordResetTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(losUserRepository.findByPasswordResetToken(u.getPasswordResetToken())).thenReturn(Optional.of(u));
        when(losUserRepository.save(any(LosUser.class))).thenAnswer(inv -> inv.getArgument(0));

        var r = new ResetPasswordRequest();
        r.setToken(u.getPasswordResetToken());
        r.setNewPassword("NewPass@999");
        demoAuthService.resetPassword(r);

        ArgumentCaptor<LosUser> cap = ArgumentCaptor.forClass(LosUser.class);
        verify(losUserRepository).save(cap.capture());
        assertTrue(encoder.matches("NewPass@999", cap.getValue().getPasswordHash()));
        assertNull(cap.getValue().getPasswordResetToken());
        assertNull(cap.getValue().getPasswordResetTokenExpiresAt());
    }

    @Test
    void resetPassword_rejects_expired() {
        LosUser u = LosUser.builder()
                .id(userId)
                .name("A")
                .email("a@b.com")
                .active(true)
                .passwordHash(encoder.encode("a"))
                .primaryLosRole("CREDIT_OFFICER")
                .passwordResetToken("t1")
                .passwordResetTokenExpiresAt(Instant.now().minusSeconds(10))
                .build();
        when(losUserRepository.findByPasswordResetToken("t1")).thenReturn(Optional.of(u));
        var r = new ResetPasswordRequest();
        r.setToken("t1");
        r.setNewPassword("NewPass@999");
        assertThrows(UnauthorizedException.class, () -> demoAuthService.resetPassword(r));
    }

    @Test
    void resetPassword_rejects_invalidToken() {
        when(losUserRepository.findByPasswordResetToken("no")).thenReturn(Optional.empty());
        var r = new ResetPasswordRequest();
        r.setToken("no");
        r.setNewPassword("NewPass@999");
        assertThrows(UnauthorizedException.class, () -> demoAuthService.resetPassword(r));
    }

    private static LoginRequest login(String email, String password) {
        var l = new LoginRequest();
        l.setEmail(email);
        l.setPassword(password);
        return l;
    }
}
