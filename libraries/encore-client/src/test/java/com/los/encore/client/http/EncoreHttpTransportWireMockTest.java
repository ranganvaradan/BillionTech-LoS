package com.los.encore.client.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.los.encore.client.config.EncoreClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract-style test: Encore GET retries on 503 then succeeds (WireMock).
 */
class EncoreHttpTransportWireMockTest {

    private WireMockServer wireMockServer;
    private EncoreClientProperties props;
    private EncoreHttpTransport transport;

    @BeforeEach
    void start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        props = new EncoreClientProperties();
        props.setBaseUrl("http://localhost:" + wireMockServer.port() + "/encore/");
        props.setApiUsername("user");
        props.setApiPassword("pass");
        props.setMaxRetriesForGet(2);
        props.setRetryBackoffMs(50);
        props.getApi().setFindWorkingDate("webservices/loans/accounts/findBankWorkingDate");

        transport = new EncoreHttpTransport(props);
    }

    @AfterEach
    void stop() {
        wireMockServer.stop();
    }

    @Test
    void getRetriesOn503Then200() {
        wireMockServer.stubFor(
                get(urlPathMatching("/encore/webservices/loans/accounts/findBankWorkingDate.*"))
                        .inScenario("retry")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(503))
                        .willSetStateTo("second"));
        wireMockServer.stubFor(
                get(urlPathMatching("/encore/webservices/loans/accounts/findBankWorkingDate.*"))
                        .inScenario("retry")
                        .whenScenarioStateIs("second")
                        .willReturn(aResponse().withStatus(200).withBody("\"2026-05-01\"")));

        String body = transport.httpGet(props.getApi().getFindWorkingDate(), null);
        assertEquals("\"2026-05-01\"", body);
    }
}
