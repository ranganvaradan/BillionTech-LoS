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
        "com.los.core.customercategory",
        "com.los.core.requirement",
        "com.los.core.payment.model",
        "com.los.core.creditintelligence.domain",
        "com.los.core.creditintelligence.core.domain",
        "com.los.core.creditintelligence.evaluation.domain",
        "com.los.core.creditintelligence.bureau.domain",
        "com.los.core.creditintelligence.gst.domain",
        "com.los.core.creditintelligence.banking.domain",
        "com.los.core.creditintelligence.tax.domain",
        "com.los.core.creditintelligence.reconciliation.domain",
        "com.los.core.creditintelligence.validation.domain",
        "com.los.core.creditintelligence.policystudio.domain",
        "com.los.core.creditintelligence.policystudio.graph",
        "com.los.core.creditintelligence.policystudio.parameters.derived",
        "com.los.core.creditintelligence.policystudio.lifecycle.domain",
        "com.los.core.creditintelligence.policystudio.runtime.canonicalconfig",
        "com.los.core.creditintelligence.policystudio.runtime.canonicalshadow",
        "com.los.core.creditintelligence.policy.domain",
        "com.los.core.creditintelligence.decision.domain",
        "com.los.core.creditintelligence.aiunderwriter.domain",
        "com.los.core.creditintelligence.cutover.domain",
        "com.los.lms.entity",
        "com.los.plp.model.entity"})
@EnableJpaRepositories(basePackages = {
        "com.los.core.repository",
        "com.los.core.customercategory",
        "com.los.core.requirement",
        "com.los.core.payment.repository",
        "com.los.core.creditintelligence.repository",
        "com.los.core.creditintelligence.core.repository",
        "com.los.core.creditintelligence.evaluation.repository",
        "com.los.core.creditintelligence.bureau.repository",
        "com.los.core.creditintelligence.gst.repository",
        "com.los.core.creditintelligence.banking.repository",
        "com.los.core.creditintelligence.tax.repository",
        "com.los.core.creditintelligence.reconciliation.repository",
        "com.los.core.creditintelligence.validation.repository",
        "com.los.core.creditintelligence.policystudio.repository",
        "com.los.core.creditintelligence.policystudio.parameters.derived",
        "com.los.core.creditintelligence.policystudio.lifecycle.repository",
        "com.los.core.creditintelligence.policystudio.runtime.canonicalconfig",
        "com.los.core.creditintelligence.policystudio.runtime.canonicalshadow",
        "com.los.core.creditintelligence.policy.repository",
        "com.los.core.creditintelligence.decision.repository",
        "com.los.core.creditintelligence.aiunderwriter.repository",
        "com.los.core.creditintelligence.cutover.repository",
        "com.los.lms.repository",
        "com.los.plp.repository"})
@EnableDiscoveryClient
@EnableScheduling
public class LosCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LosCoreServiceApplication.class, args);
    }
}
