package com.los.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.enums.BorrowerType;
import com.los.encore.client.api.EncoreOpenLoanParams;
import com.los.encore.client.api.EncoreTemporaryOverrides;
import com.los.encore.client.config.EncoreClientProperties;
import com.los.lms.config.LmsWorkflowMappingProperties;
import com.los.lms.legacy.BlCoreEncoreLmsAdapter;
import com.los.lms.repository.WorkflowLmsProductMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LMS-PRODUCT-MAPPING-P0 — fail-closed openLoanAccount product selection; no runtime hardcode.
 */
@ExtendWith(MockitoExtension.class)
class LmsProductMappingP0Test {

    @Mock
    private WorkflowLmsProductMappingRepository mappingRepository;
    @Mock
    private LmsWorkflowMappingProperties mappingProperties;

    @Test
    void workflowLmsProductResolver_doesNotHardcodeIppopaym01() {
        when(mappingProperties.isEnabled()).thenReturn(false);
        WorkflowLmsProductResolver resolver =
                new WorkflowLmsProductResolver(mappingRepository, mappingProperties);

        assertThat(resolver.resolveEncoreProductCode(
                BorrowerType.COMPANY, "TERM_LOAN", "CONFIGURED_CODE_A"))
                .isEqualTo("CONFIGURED_CODE_A");

        assertThatThrownBy(() -> resolver.resolveEncoreProductCode(
                BorrowerType.COMPANY, "TERM_LOAN", null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getReason())
                .isEqualTo(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING);

        verify(mappingRepository, never()).findByBorrowerTypeAndLoanProduct(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blCoreAdapter_blankProductCode_failsBeforePayload() {
        EncoreClientProperties props = new EncoreClientProperties();
        BlCoreEncoreLmsAdapter adapter = new BlCoreEncoreLmsAdapter(props, new ObjectMapper());
        EncoreOpenLoanParams params = EncoreOpenLoanParams.minimal(
                "LOS-1", "Borrower", BigDecimal.TEN, BigDecimal.ONE, 12, null);

        assertThatThrownBy(() -> adapter.buildLoanOdAccount(params, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getReason())
                        .isEqualTo(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING));
    }

    @Test
    void blCoreAdapter_configuredProductCode_writtenToPayload() {
        EncoreClientProperties props = new EncoreClientProperties();
        BlCoreEncoreLmsAdapter adapter = new BlCoreEncoreLmsAdapter(props, new ObjectMapper());
        EncoreOpenLoanParams params = EncoreOpenLoanParams.minimal(
                "LOS-1", "Borrower", BigDecimal.TEN, BigDecimal.ONE, 12, "ALT_PROD_99");

        ObjectNode node = adapter.buildLoanOdAccount(params, null);
        assertThat(node.get("productCode").asText()).isEqualTo("ALT_PROD_99");
        assertThat(node.get("productCode").asText())
                .isNotEqualTo(EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE);
    }

    @Test
    void allowCanonicalAuthority_remainsFalseOnResolutionEvidence() {
        LmsProductMappingResolution res = new LmsProductMappingResolution(
                "IPPOPAYM01",
                LmsProductMappingResolution.SOURCE_WORKFLOW,
                java.util.UUID.randomUUID(),
                2,
                "TERM_LOAN",
                "COMPANY");
        assertThat(res.toEvidenceMap().get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(res.toEvidenceMap().get("mappingSource"))
                .isEqualTo(LmsProductMappingResolution.SOURCE_WORKFLOW);
    }
}
