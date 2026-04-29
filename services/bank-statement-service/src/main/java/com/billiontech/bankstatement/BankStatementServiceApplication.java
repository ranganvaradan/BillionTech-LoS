package com.billiontech.bankstatement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BankStatementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankStatementServiceApplication.class, args);
    }
}
