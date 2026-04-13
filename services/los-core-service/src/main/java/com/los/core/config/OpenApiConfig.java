package com.los.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI losOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LOS Core Service API")
                        .description("Loan Origination System — Core service APIs for loan applications, KYC orchestration, workflow engine, credit decisions, document management, transactions, audit trail, and reporting.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("BillionTech LOS Team")
                                .email("los-support@billiontech.com"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local Development"),
                        new Server().url("http://localhost:8080/los-core").description("Via API Gateway")
                ));
    }
}
