package com.los.core.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * JDBC smokes: {@code aggregator_configs} and {@code esign_requests} column shapes
 * (H2; production is Flyway V14 on PostgreSQL with jsonb).
 * JPA entities live in {@code com.los.core.model.entity.schema.los2} for application use.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Sql(scripts = "/schema/los2/h2/V001__aggregator_esign.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LosDesignV2SchemaJdbcTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndCount_aggregatorConfigs() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO aggregator_configs (id, provider_name, step_type, is_active) VALUES (?, ?, ?, ?)",
                id, "KARZA", "PAN_VERIFY", true);
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aggregator_configs WHERE provider_name = ?", Integer.class, "KARZA");
        assertEquals(1, c);
    }

    @Test
    void insertAndSelect_esignRequests() {
        UUID id = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO esign_requests (id, application_id, document_type, provider, status, created_at) "
                        + "VALUES (?, ?, 'KFS_AGREEMENT', 'EMSIGNER', 'PENDING', CURRENT_TIMESTAMP)",
                id, appId);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM esign_requests WHERE application_id = ?",
                String.class,
                appId);
        assertEquals("PENDING", status);
    }
}
