package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "los.sanction.notification")
public class SanctionNotificationProperties {

    private boolean enabled = true;

    /** Anchor invoice-discounting onboarding sanction email. */
    private String anchorTemplateCode = "SANCTION_APPROVED_ANCHOR";

    /** Invoice-discounting borrower sanction / terms email. */
    private String idBorrowerTemplateCode = "SANCTION_APPROVED_ID_BORROWER";

    /** Term-loan borrower — KFS, agreement, and sanction details. */
    private String termLoanTemplateCode = "SANCTION_APPROVED_TERM_LOAN";

    /** Public URL for the PLP anchor portal sign-in page. */
    private String anchorPortalUrl = "http://localhost:3200/plp-anchor/";

    /** Temporary password provisioned for LOS-integrated anchor portal users. */
    private String defaultTemporaryPassword = "ChangeMe@PLP2026";
}
