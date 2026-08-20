package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigurationAuthorityCutoverTest {

    @Test
    void discriminatorIsPersistedWorkflowId() {
        assertEquals("loan_applications.workflow_id", ApplicationConfigurationAuthority.CUTOVER_DISCRIMINATOR);
        LoanApplication unconfigured = new LoanApplication();
        unconfigured.setId(UUID.randomUUID());
        assertTrue(ApplicationConfigurationAuthority.isNewApplicationUnconfigured(unconfigured));

        LoanApplication historical = new LoanApplication();
        historical.setWorkflowId(UUID.randomUUID());
        assertTrue(ApplicationConfigurationAuthority.isPreCutoverWorkflowPin(historical));
        assertFalse(ApplicationConfigurationAuthority.isCategoryConfigurationPinned(historical));
    }

    @Test
    void ordinaryCreateRejectsIndependentWorkflowId() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                ApplicationConfigurationAuthority.assertOrdinaryCreateDoesNotSelectWorkflow(
                        UUID.randomUUID(), "RELATIONSHIP_MANAGER"));
        assertEquals(ApplicationConfigurationAuthority.WORKFLOW_ID_NOT_PERMITTED_ON_ORDINARY_INTAKE, ex.getReason());
        ApplicationConfigurationAuthority.assertOrdinaryCreateDoesNotSelectWorkflow(UUID.randomUUID(), "ADMIN");
        ApplicationConfigurationAuthority.assertOrdinaryCreateDoesNotSelectWorkflow(null, "BORROWER");
    }

    @Test
    void submitRequiresCategoryPinForNewApps_allowsHistoricalWorkflowPin() {
        LoanApplication neu = new LoanApplication();
        neu.setId(UUID.randomUUID());
        neu.setStatus(ApplicationStatus.DRAFT);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ApplicationConfigurationAuthority.assertReadyForSubmit(neu));
        assertEquals(ApplicationConfigurationAuthority.CATEGORY_CONFIGURATION_NOT_PINNED, ex.getReason());

        LoanApplication historical = new LoanApplication();
        historical.setId(UUID.randomUUID());
        historical.setWorkflowId(UUID.randomUUID());
        historical.setStatus(ApplicationStatus.DRAFT);
        ApplicationConfigurationAuthority.assertReadyForSubmit(historical);

        LoanApplication categoryPinned = new LoanApplication();
        categoryPinned.setId(UUID.randomUUID());
        categoryPinned.setSelectedCustomerCategoryId(UUID.randomUUID());
        categoryPinned.setWorkflowId(UUID.randomUUID());
        categoryPinned.setSelectedPolicyApplicabilityId(UUID.randomUUID());
        categoryPinned.setSelectedPolicyDocumentId(UUID.randomUUID());
        ApplicationConfigurationAuthority.assertReadyForSubmit(categoryPinned);
    }

    @Test
    void updateCannotReplacePinnedWorkflow() {
        LoanApplication app = new LoanApplication();
        app.setId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.DRAFT);
        app.setSelectedCustomerCategoryId(UUID.randomUUID());
        app.setWorkflowId(UUID.randomUUID());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                ApplicationConfigurationAuthority.assertWorkflowIdUpdateAllowed(app, UUID.randomUUID()));
        assertEquals(ApplicationConfigurationAuthority.WORKFLOW_PIN_IMMUTABLE, ex.getReason());
    }

    @Test
    void lmsFieldsCannotBeSetDirectlyOnCategoryGovernedApp() {
        LoanApplication categoryGoverned = new LoanApplication();
        categoryGoverned.setId(UUID.randomUUID());
        categoryGoverned.setWorkflowResolutionSource(WorkflowResolutionSource.CATEGORY_SELECTION.name());
        assertTrue(ApplicationConfigurationAuthority.isCategoryGoverned(categoryGoverned));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                ApplicationConfigurationAuthority.assertLmsFieldUpdateAllowed(categoryGoverned, "lmsProductCode"));
        assertEquals(ApplicationConfigurationAuthority.LMS_FIELD_NOT_PERMITTED_ON_CATEGORY_GOVERNED_APP, ex.getReason());

        LoanApplication legacy = new LoanApplication();
        legacy.setId(UUID.randomUUID());
        legacy.setWorkflowId(UUID.randomUUID());
        legacy.setWorkflowResolutionSource(WorkflowResolutionSource.EXPLICIT.name());
        assertFalse(ApplicationConfigurationAuthority.isCategoryGoverned(legacy));
        ApplicationConfigurationAuthority.assertLmsFieldUpdateAllowed(legacy, "lmsProductCode");

        LoanApplication noSourceYet = new LoanApplication();
        noSourceYet.setId(UUID.randomUUID());
        ApplicationConfigurationAuthority.assertLmsFieldUpdateAllowed(noSourceYet, "lmsTenureUnit");
    }

    @Test
    void resolverSourceDoesNotDiscoverDefault() throws Exception {
        Path resolver = Path.of("src/main/java/com/los/core/service/workflow/ApplicationWorkflowResolver.java");
        String src = Files.readString(resolver);
        String lower = src.toLowerCase(Locale.ROOT);
        assertFalse(src.contains("discoverDefault"));
        assertFalse(src.contains("persistResolution"));
        assertFalse(src.contains("WorkflowResolutionSource.DEFAULT"));
        assertFalse(lower.contains("orderbyversiondesc"));
        assertTrue(src.contains("never discover"));
        assertTrue(src.contains("read-only"));
    }
}
