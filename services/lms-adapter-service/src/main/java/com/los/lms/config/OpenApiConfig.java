package com.los.lms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LOS LMS Adapter Service API")
                        .description("Loan Management System integration adapter — loan handover, repayment schedule generation, account summaries, and repayment callbacks.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("BillionTech LOS Team")
                                .email("los-support@billiontech.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8085").description("Local Development"),
                        new Server().url("http://localhost:8080/lms").description("Via API Gateway")
                ));
    }
}
