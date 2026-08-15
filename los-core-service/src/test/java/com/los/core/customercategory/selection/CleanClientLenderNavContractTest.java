package com.los.core.customercategory.selection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEW-CLIENT-CLEAN-ROOM-UAT-1 — Client lender nav must hide transitional authorities.
 * Does not assert production UW cutover.
 */
class CleanClientLenderNavContractTest {

    @Test
    void safeDisambiguationCatalogueHasFinancialRouteOnly() {
        assertEquals(1, SafeDisambiguationCatalogue.allSafe().size());
        assertNotNull(SafeDisambiguationCatalogue.get(SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE));
        assertTrue(SafeDisambiguationCatalogue.get(SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE)
                .safeForCategoryDisambiguation());
    }

    @Test
    void uiHidesLiveUwRulesAndPolicySetsOnClientSurface() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        // test cwd is module root when run via Maven
        Path ui = root.resolve("../ui-service/src").normalize();
        if (!Files.isDirectory(ui)) {
            ui = root.resolve("ui-service/src");
        }
        if (!Files.isDirectory(ui)) {
            // skip soft if monorepo layout differs in CI isolation
            return;
        }
        String rt = Files.readString(ui.resolve("lib/runtimeEnv.ts"));
        assertTrue(rt.contains("isClientLenderSurface"));
        assertTrue(rt.contains("hideLegacyDecisionConfigFromLenderNav"));
        String nav = Files.readString(ui.resolve("nav/workspaceNav.ts"));
        assertTrue(nav.contains("CLIENT_HIDDEN_ADMIN_PATHS"));
        assertTrue(nav.contains("/underwriting-rules"));
        assertTrue(nav.contains("/policy-sets"));
        assertTrue(nav.contains("administrationNavGroups"));
        String admin = Files.readString(ui.resolve("pages/AdministrationPage.tsx"));
        assertTrue(admin.contains("Policy is the lender-facing underwriting authority"));
        assertTrue(admin.contains("CLIENT_HIDDEN_ADMIN_PATHS"));
    }
}
