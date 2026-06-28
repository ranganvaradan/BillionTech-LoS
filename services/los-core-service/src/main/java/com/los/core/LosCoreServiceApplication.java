package com.los.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.los.core", "com.los.lms", "com.los.plp", "com.los.encore.client"})
@EntityScan(basePackages = {
        "com.los.core.model.entity",
        "com.los.core.payment.model",
        "com.los.lms.entity",
        "com.los.plp.model.entity"})
@EnableJpaRepositories(basePackages = {
        "com.los.core.repository",
        "com.los.core.payment.repository",
        "com.los.lms.repository",
        "com.los.plp.repository"})
@EnableDiscoveryClient
@EnableScheduling
public class LosCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LosCoreServiceApplication.class, args);
    }
}
