package com.los.core.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "los.payu")
public class LosPayuProperties {

    private String merchantKey = "";
    private String merchantSalt = "";
    private String gatewayUrl = "https://test.payu.in/_payment";
    private String verifyCommand = "verify_payment";
    private String postServiceUrl = "";
    private String transactionIdSuffix = "LOS";
    /** Public API base reachable by PayU and the user browser (e.g. http://localhost:8080 or gateway URL). */
    private String callbackBaseUrl = "http://localhost:8080";
    /** LOS borrower portal base including path, e.g. http://localhost:5173/los/borrower */
    private String borrowerUiUrl = "http://localhost:5173/los/borrower";
}
