package com.los.core.service.auth;

import com.los.core.config.PasswordEncodingConfig;
import com.los.core.model.dto.auth.LoginRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * H2 in-memory: verifies entity mapping + {@link DemoAuthService} with a user matching seed shape.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Import({DemoAuthService.class, PasswordEncodingConfig.class})
@TestPropertySource(properties = "spring.flyway.enabled=false")
class DemoAuthServiceJpaIT {

    @Autowired
    private LosUserRepository losUserRepository;

    @Autowired
    private DemoAuthService demoAuthService;

    @BeforeEach
    void clear() {
        losUserRepository.deleteAll();
    }

    @Test
    void login_withStoredBcrypt_succeeds() {
        // Same password as demo seed: Bltest@123 (precomputed hash from BCrypt, strength 10)
        String hash = "$2a$10$2kJcllP0SynwcqGz5DEtk.1/Jrz0nwXvtxeoTKTaAtZJPBkCF3VVa";
        LosUser u = LosUser.builder()
                .id(UUID.fromString("a1000000-0000-0000-0000-0000000000aa"))
                .name("Demo User")
                .email("seed-check@billionloans.com")
                .active(true)
                .passwordHash(hash)
                .primaryLosRole("CREDIT_OFFICER")
                .build();
        losUserRepository.save(u);

        var req = new LoginRequest();
        req.setEmail("seed-check@billionloans.com");
        req.setPassword("Bltest@123");
        var res = demoAuthService.login(req);
        assertEquals("CREDIT_OFFICER", res.getRole());
        assertEquals(DemoAuthService.DEMO_INSTITUTION, res.getInstitution());
    }
}
