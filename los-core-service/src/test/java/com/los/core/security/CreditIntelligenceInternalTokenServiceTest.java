package com.los.core.security;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditIntelligenceInternalTokenServiceTest {

    @Test
    void prodRequiredBlankToken_failClosed() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setInternalToken("");
        props.setInternalTokenRequired(true);
        CreditIntelligenceInternalTokenService svc = new CreditIntelligenceInternalTokenService(props);
        assertThatThrownBy(() -> svc.assertToken(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(svc::assertConfiguredWhenRequired)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void correctToken_allowed() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setInternalToken("secret-token");
        props.setInternalTokenRequired(true);
        CreditIntelligenceInternalTokenService svc = new CreditIntelligenceInternalTokenService(props);
        assertThatCode(() -> svc.assertToken("secret-token")).doesNotThrowAnyException();
        assertThatCode(svc::assertConfiguredWhenRequired).doesNotThrowAnyException();
    }

    @Test
    void wrongOrMissingToken_denied() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setInternalToken("secret-token");
        props.setInternalTokenRequired(true);
        CreditIntelligenceInternalTokenService svc = new CreditIntelligenceInternalTokenService(props);
        assertThatThrownBy(() -> svc.assertToken(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> svc.assertToken("wrong")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void stagingBlankNotRequired_softOpen() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setInternalToken("");
        props.setInternalTokenRequired(false);
        CreditIntelligenceInternalTokenService svc = new CreditIntelligenceInternalTokenService(props);
        assertThatCode(() -> svc.assertToken(null)).doesNotThrowAnyException();
        assertThat(props.isInternalTokenRequired()).isFalse();
    }
}
