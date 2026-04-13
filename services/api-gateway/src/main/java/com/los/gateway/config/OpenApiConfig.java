package com.los.gateway.config;

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
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LOS API Gateway")
                        .description("API Gateway for the Loan Origination System — routes requests to IAM, Enrollment, LOS Core, Notification, and LMS services with JWT authentication, rate limiting, and CORS support.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("BillionTech LOS Team")
                                .email("los-support@billiontech.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")
                ));
    }
}
