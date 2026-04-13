package com.los.notification.config;

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
    public OpenAPI notificationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LOS Notification Service API")
                        .description("Event-driven notification service — SMS, Email, WhatsApp delivery with template engine, retry logic, and notification history.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("BillionTech LOS Team")
                                .email("los-support@billiontech.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8084").description("Local Development"),
                        new Server().url("http://localhost:8080/notification").description("Via API Gateway")
                ));
    }
}
