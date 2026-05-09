package com.los.core.controller;

import com.los.core.model.entity.ApplicationStatusHistory;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.ApplicationStatusHistoryRepository;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-slice: one application, DELETE, assert empty DB. Requires demo mode on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
                "los.demo.enabled=true",
                "spring.flyway.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.data.redis.repositories.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration,org.springframework.cloud.netflix.eureka.serviceregistry.EurekaServiceRegistryAutoConfiguration"
        }
)
@Import(DemoApplicationsDeleteIT.AmqpStubConfig.class)
class DemoApplicationsDeleteIT {

    @TestConfiguration
    static class AmqpStubConfig {
        @Bean
        ConnectionFactory testConnectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private ApplicationStatusHistoryRepository applicationStatusHistoryRepository;

    @Test
    void clearDemo_deletesApplication_andReturnsCount() throws Exception {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("DEMO-IT-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .status(ApplicationStatus.DRAFT)
                .build();
        loanApplicationRepository.save(app);
        applicationStatusHistoryRepository.save(ApplicationStatusHistory.builder()
                .applicationId(app.getId())
                .fromStatus("DRAFT")
                .toStatus("SUBMITTED")
                .build());
        assertThat(loanApplicationRepository.count()).isEqualTo(1L);
        assertThat(applicationStatusHistoryRepository.count()).isEqualTo(1L);

        mockMvc.perform(delete("/api/v1/demo/applications").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedApplications").value(1))
                .andExpect(jsonPath("$.status").value("success"));

        assertThat(loanApplicationRepository.count()).isZero();
        assertThat(applicationStatusHistoryRepository.count()).isZero();
    }
}
