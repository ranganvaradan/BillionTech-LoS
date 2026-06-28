package com.los.notification.template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StyledWorkflowEmailTemplatesTest {

    @Test
    void esignLinkEmail_buildsWithoutFormatException() {
        assertThatCode(StyledWorkflowEmailTemplates::esignLinkEmail).doesNotThrowAnyException();
        String html = StyledWorkflowEmailTemplates.esignLinkEmail();
        assertThat(html).contains("width=\"100%\"");
        assertThat(html).contains("{{esignLink}}");
        assertThat(html).contains("{{applicationNumber}}");
    }

    @Test
    void vkycLinkEmail_buildsWithoutFormatException() {
        assertThatCode(StyledWorkflowEmailTemplates::vkycLinkEmail).doesNotThrowAnyException();
        String html = StyledWorkflowEmailTemplates.vkycLinkEmail();
        assertThat(html).contains("width=\"100%\"");
        assertThat(html).contains("{{vkycLink}}");
    }

    @Test
    void esignReminderEmail_buildsWithoutFormatException() {
        assertThatCode(StyledWorkflowEmailTemplates::esignReminderEmail).doesNotThrowAnyException();
    }

    @Test
    void allWorkflowTemplates_buildWithoutFormatException() {
        assertThatCode(StyledWorkflowEmailTemplates::vkycApprovedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::vkycRejectedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::vkycCompletedViaPkyEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::vkycExpiryReminderEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::esignCompletedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::kycSuccessEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::sanctionApprovedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::disbursementSuccessEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::applicationRejectedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::paymentReminderEmail).doesNotThrowAnyException();
    }

    @Test
    void sanctionFlowTemplates_buildWithoutFormatException() {
        assertThatCode(StyledWorkflowEmailTemplates::anchorSanctionApprovedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::idBorrowerSanctionApprovedEmail).doesNotThrowAnyException();
        assertThatCode(StyledWorkflowEmailTemplates::termLoanSanctionApprovedEmail).doesNotThrowAnyException();
        assertThat(StyledWorkflowEmailTemplates.termLoanSanctionApprovedEmail()).contains("{{sanctionedAmount}}");
        assertThat(StyledWorkflowEmailTemplates.termLoanSanctionApprovedEmail()).contains("Key Fact Statement");
    }

    @Test
    void anchorSanctionEmail_includesPortalCredentials() {
        String html = StyledWorkflowEmailTemplates.anchorSanctionApprovedEmail();
        assertThat(html).contains("{{anchorPortalUrl}}");
        assertThat(html).contains("{{loginEmail}}");
        assertThat(html).contains("{{temporaryPassword}}");
    }
}
