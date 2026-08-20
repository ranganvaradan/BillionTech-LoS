package com.los.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.service.audit.IntegrationApiAuditService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.InvoiceDiscountingLosLoanGuard;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.encore.client.api.EncoreLmsApi;
import com.los.encore.client.config.EncoreClientProperties;
import com.los.lms.config.LmsCallbackSecurityProperties;
import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.legacy.BlCoreEncoreLmsAdapter;
import com.los.lms.repository.ExternalProductMappingRepository;
import com.los.lms.repository.LmsAccountSummaryRepository;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.repository.LmsRepaymentCallbackRepository;
import com.los.lms.service.LmsApplicationConfigResolver;
import com.los.lms.service.LmsProgramResolver;
import com.los.lms.service.LmsService;
import com.los.lms.service.WorkflowLmsProductResolver;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LMS-PRODUCT-MAPPING-E2E-CERTIFICATION-1 — end-to-end proof for:
 * external_product_mapping -> pin -> LoanApplication.lmsProductCode -> openLoanAccount JSON productCode.
 *
 * External LMS calls are stubbed (mocked) and outbound JSON is asserted locally.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LmsProductMappingE2ECertificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExternalProductMappingRepository externalProductMappingRepository;

    @Mock
    private ActiveWorkflowConfigService activeWorkflowConfigService;

    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;

    @Mock
    private ProgramMasterRepository programMasterRepository;

    @Mock
    private EncoreLmsApi encoreLmsApi;

    @Mock
    private WorkflowLmsProductResolver workflowLmsProductResolver;

    @Mock
    private LmsProgramResolver lmsProgramResolver;

    @Mock
    private LmsLoanHandoverRepository handoverRepository;

    @Mock
    private LmsRepaymentCallbackRepository repaymentRepository;

    @Mock
    private LmsAccountSummaryRepository summaryRepository;

    @Mock
    private com.los.lms.config.LmsCallbackSecurityProperties callbackSecurity;

    @Mock
    private InvoiceDiscountingLosLoanGuard invoiceDiscountingLosLoanGuard;

    @Mock
    private KfsDocumentRepository kfsDocumentRepository;

    @Mock
    private KfsService kfsService;

    @Mock
    private IntegrationApiAuditService integrationApiAuditService;

    private final EncoreClientProperties encoreClientProperties = new EncoreClientProperties();

    private LmsApplicationConfigResolver lmsApplicationConfigResolver() {
        return new LmsApplicationConfigResolver(
                activeWorkflowConfigService,
                subProgramMasterRepository,
                programMasterRepository);
    }

    private ExternalProductMappingPinningService pinningService() {
        return new ExternalProductMappingPinningService(externalProductMappingRepository);
    }

    private LmsService buildLmsService() {
        BlCoreEncoreLmsAdapter adapter = new BlCoreEncoreLmsAdapter(encoreClientProperties, objectMapper);
        return new LmsService(
                encoreLmsApi,
                encoreClientProperties,
                adapter,
                workflowLmsProductResolver,
                lmsProgramResolver,
                lmsApplicationConfigResolver(),
                objectMapper,
                handoverRepository,
                repaymentRepository,
                summaryRepository,
                callbackSecurity,
                invoiceDiscountingLosLoanGuard,
                kfsDocumentRepository,
                kfsService,
                integrationApiAuditService
        );
    }

    private LoanApplication baseCategoryGovernedBusinessTermApp(String appNo) {
        return LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber(appNo)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.BUSINESS_TERM_LOAN)
                .sanctionedAmount(new BigDecimal("100000"))
                .approvedRate(new BigDecimal("18.5"))
                .tenureMonths(12)
                .lmsTenureUnit("Month")
                .workflowResolutionSource("CATEGORY_SELECTION")
                .categorySelectedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .intakeSegment(IntakeSegment.BORROWER)
                .bookType("OWN_BOOK")
                .build();
    }

    @Test
    void BUSINESS_TERM_LOAN_E2E_productCodeMatchesPinnedExternalProductMapping() throws Exception {
        TestContext ctx = new TestContext();

        UUID mappingId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        int mappingVersion = 3;
        String pinnedEncoreProductCode = "BT_ENCORE_CODE_03";

        LoanApplication app = baseCategoryGovernedBusinessTermApp("BT-E2E-1");

        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(ExternalProductMapping.builder()
                        .id(mappingId)
                        .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                        .externalSystem("ENCORE")
                        .externalProductCode(pinnedEncoreProductCode)
                        .version(mappingVersion)
                        .status("ACTIVE")
                        .effectiveFrom(LocalDate.of(2025, 1, 1))
                        .effectiveTo(LocalDate.of(2027, 1, 1))
                        .build()));

        ExternalProductMappingPinningService pinning = pinningService();
        pinning.pinEncoreMappingIfNeeded(app);

        assertEquals(mappingId, app.getExternalProductMappingId());
        assertEquals(mappingVersion, app.getExternalProductMappingVersion());
        assertEquals(pinnedEncoreProductCode, app.getLmsProductCode());

        LmsService lmsService = buildLmsService();

        when(invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)).thenReturn(false);
        when(lmsProgramResolver.resolveForApplication(app)).thenReturn(Optional.empty());
        when(lmsProgramResolver.resolveByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());

        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());
        when(workflowLmsProductResolver.resolveFullMapping(null, app.getBorrowerType(), app.getLoanProduct()))
                .thenReturn(Optional.empty());

        when(encoreLmsApi.isActive()).thenReturn(true);
        when(handoverRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());
        when(summaryRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());

        when(encoreLmsApi.openLoanAccountWithJson(anyString(), anyString())).thenReturn("ENCORE_ACC_BT_1");
        when(encoreLmsApi.findRepaymentSchedule("ENCORE_ACC_BT_1")).thenReturn(List.of());

        when(handoverRepository.save(any(LmsLoanHandover.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        lmsService.createLmsAccountOnSanction(app);

        verify(encoreLmsApi).openLoanAccountWithJson(anyString(), jsonCaptor.capture());
        String payload = jsonCaptor.getValue();
        JsonNode node = objectMapper.readTree(payload);
        String productCode = node.get("productCode").asText();

        assertEquals(pinnedEncoreProductCode, productCode);
    }

    @Test
    void INVOICE_DISCOUNTING_E2E_programMasterEncoreProductCodeRemainsAuthority() throws Exception {
        UUID subProgramId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID programId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("INV-E2E-1")
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .subProgramId(subProgramId)
                .sanctionedAmount(new BigDecimal("90000"))
                .approvedRate(new BigDecimal("16.0"))
                .tenureMonths(10)
                .lmsTenureUnit("Month")
                .workflowResolutionSource("CATEGORY_SELECTION")
                .categorySelectedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .intakeSegment(IntakeSegment.BORROWER)
                .lmsProductCode("WRONG_CODE_SHOULD_NOT_BE_USED")
                .build();
        String programEncoreProductCode = "ID_PROG_CODE_777";

        // ProgramMaster authority
        when(subProgramMasterRepository.findById(subProgramId))
                .thenReturn(Optional.of(SubProgramMaster.builder().programId(programId).build()));
        when(programMasterRepository.findById(programId))
                .thenReturn(Optional.of(ProgramMaster.builder()
                        .productType("INVOICE_DISCOUNTING")
                        .encoreProductCode(programEncoreProductCode)
                        .build()));

        when(activeWorkflowConfigService.findActiveForApplication(any())).thenReturn(Optional.empty());

        ExternalProductMappingPinningService pinning = pinningService();
        pinning.pinEncoreMappingIfNeeded(app); // should be a no-op for invoice-discounting

        verify(externalProductMappingRepository, never()).findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                anyString(), anyString(), any(), anyString(), any(LocalDate.class));

        LmsService lmsService = buildLmsService();

        when(invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)).thenReturn(false);
        when(lmsProgramResolver.resolveForApplication(app)).thenReturn(Optional.empty());
        when(lmsProgramResolver.resolveByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());

        when(workflowLmsProductResolver.resolveFullMapping(null, app.getBorrowerType(), app.getLoanProduct()))
                .thenReturn(Optional.empty());

        when(encoreLmsApi.isActive()).thenReturn(true);
        when(handoverRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());
        when(summaryRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());

        when(encoreLmsApi.openLoanAccountWithJson(anyString(), anyString())).thenReturn("ENCORE_ACC_INV_1");
        when(encoreLmsApi.findRepaymentSchedule("ENCORE_ACC_INV_1")).thenReturn(List.of());

        when(handoverRepository.save(any(LmsLoanHandover.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        lmsService.createLmsAccountOnSanction(app);

        verify(encoreLmsApi).openLoanAccountWithJson(anyString(), jsonCaptor.capture());
        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals(programEncoreProductCode, node.get("productCode").asText());
    }

    @Test
    void MISSING_MAPPING_failClosed_noLmsRequestSent() {
        LoanApplication app = baseCategoryGovernedBusinessTermApp("BT-E2E-MISSING-1");

        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE")))
                .thenReturn(List.of());

        ExternalProductMappingPinningService pinning = pinningService();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> pinning.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING, ex.getReason());
        verify(encoreLmsApi, never()).openLoanAccountWithJson(anyString(), anyString());
    }

    @Test
    void AMBIGUOUS_MAPPING_failClosed_noArbitraryRowChosen() {
        LoanApplication app = baseCategoryGovernedBusinessTermApp("BT-E2E-AMBIG-1");

        ExternalProductMapping m1 = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("CODE_A")
                .version(1)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .effectiveTo(LocalDate.of(2027, 1, 1))
                .build();
        ExternalProductMapping m2 = ExternalProductMapping.builder()
                .id(UUID.randomUUID())
                .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                .externalSystem("ENCORE")
                .externalProductCode("CODE_A")
                .version(2)
                .status("ACTIVE")
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .effectiveTo(LocalDate.of(2027, 1, 1))
                .build();

        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(m2, m1));

        ExternalProductMappingPinningService pinning = pinningService();
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> pinning.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_AMBIGUOUS, ex.getReason());
        verify(encoreLmsApi, never()).openLoanAccountWithJson(anyString(), anyString());
    }

    @Test
    void EXPIRED_NOT_EFFECTIVE_failClosed_noLmsRequestSent() {
        LoanApplication app = baseCategoryGovernedBusinessTermApp("BT-E2E-EXPIRED-1");

        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of());

        // Coverage within effective date is empty
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), any(LocalDate.class)))
                .thenReturn(List.of());

        // But mappings exist for the product/system (expired/not covering)
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE")))
                .thenReturn(List.of(ExternalProductMapping.builder()
                        .id(UUID.randomUUID())
                        .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                        .externalSystem("ENCORE")
                        .bookType("OWN_BOOK")
                        .externalProductCode("CODE_EXPIRED")
                        .version(1)
                        .status("ACTIVE")
                        .effectiveFrom(LocalDate.of(2020, 1, 1))
                        .effectiveTo(LocalDate.of(2020, 12, 31))
                        .build()));

        ExternalProductMappingPinningService pinning = pinningService();
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> pinning.pinEncoreMappingIfNeeded(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_NOT_EFFECTIVE, ex.getReason());
        verify(encoreLmsApi, never()).openLoanAccountWithJson(anyString(), anyString());
    }

    @Test
    void RETRY_idempotency_reusesPinnedMappingAndDoesNotReresolveOrChangeProductCode() throws Exception {
        LoanApplication app = baseCategoryGovernedBusinessTermApp("BT-E2E-RETRY-1");

        UUID mappingIdV1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        int mappingVersionV1 = 1;
        String productCodeV1 = "BT_ENCORE_CODE_V1";

        UUID mappingIdV2 = UUID.fromString("22222222-2222-2222-2222-222222222223");
        int mappingVersionV2 = 2;
        String productCodeV2 = "BT_ENCORE_CODE_V2";

        // First pin -> version 1
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(ExternalProductMapping.builder()
                        .id(mappingIdV1)
                        .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                        .externalSystem("ENCORE")
                        .externalProductCode(productCodeV1)
                        .version(mappingVersionV1)
                        .status("ACTIVE")
                        .effectiveFrom(LocalDate.of(2025, 1, 1))
                        .effectiveTo(LocalDate.of(2027, 1, 1))
                        .build()));

        ExternalProductMappingPinningService pinning = pinningService();
        pinning.pinEncoreMappingIfNeeded(app);

        assertEquals(mappingIdV1, app.getExternalProductMappingId());
        assertEquals(mappingVersionV1, app.getExternalProductMappingVersion());
        assertEquals(productCodeV1, app.getLmsProductCode());

        // Prepare lmsService and mocks for first open
        LmsService lmsService = buildLmsService();

        when(invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)).thenReturn(false);
        when(lmsProgramResolver.resolveForApplication(app)).thenReturn(Optional.empty());
        when(lmsProgramResolver.resolveByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());
        when(workflowLmsProductResolver.resolveFullMapping(null, app.getBorrowerType(), app.getLoanProduct()))
                .thenReturn(Optional.empty());

        when(encoreLmsApi.isActive()).thenReturn(true);

        // Idempotent behavior: first call returns no existing handover; second call returns existing.
        LmsLoanHandover existing = LmsLoanHandover.builder()
                .applicationNumber(app.getApplicationNumber())
                .encoreAccountId("ENCORE_ACC_REUSED")
                .encoreRepaymentScheduleJson("[{\"cached\":true}]")
                .build();

        when(handoverRepository.findByApplicationNumber(app.getApplicationNumber()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        when(summaryRepository.findByApplicationNumber(app.getApplicationNumber()))
                .thenReturn(Optional.empty());

        when(encoreLmsApi.openLoanAccountWithJson(anyString(), anyString())).thenReturn("ENCORE_ACC_REUSED");
        when(encoreLmsApi.findRepaymentSchedule("ENCORE_ACC_REUSED")).thenReturn(List.of());

        when(handoverRepository.save(any(LmsLoanHandover.class))).thenAnswer(inv -> inv.getArgument(0));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First open
        ArgumentCaptor<String> firstJson = ArgumentCaptor.forClass(String.class);
        lmsService.createLmsAccountOnSanction(app);
        verify(encoreLmsApi).openLoanAccountWithJson(anyString(), firstJson.capture());

        // Payload proof: Encore JSON productCode must match initially pinned code.
        JsonNode firstNode = objectMapper.readTree(firstJson.getValue());
        assertEquals(productCodeV1, firstNode.get("productCode").asText());

        // Now "new mapping version exists" in isolated test data, but app pin must not change.
        when(externalProductMappingRepository.findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class)))
                .thenReturn(List.of(ExternalProductMapping.builder()
                        .id(mappingIdV2)
                        .losProductCode(StandardLoanProduct.BUSINESS_TERM_LOAN)
                        .externalSystem("ENCORE")
                        .externalProductCode(productCodeV2)
                        .version(mappingVersionV2)
                        .status("ACTIVE")
                        .effectiveFrom(LocalDate.of(2025, 1, 1))
                        .effectiveTo(LocalDate.of(2027, 1, 1))
                        .build()));

        String pinnedBeforeRetry = app.getLmsProductCode();
        UUID mappingIdBeforeRetry = app.getExternalProductMappingId();
        Integer mappingVersionBeforeRetry = app.getExternalProductMappingVersion();

        // Retry pinning: must be a no-op (no re-resolution)
        pinning.pinEncoreMappingIfNeeded(app);

        assertEquals(mappingIdBeforeRetry, app.getExternalProductMappingId());
        assertEquals(mappingVersionBeforeRetry, app.getExternalProductMappingVersion());
        assertEquals(pinnedBeforeRetry, app.getLmsProductCode());

        verify(externalProductMappingRepository,
                times(1)).findByLosProductCodeAndExternalSystemAndBookTypeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrderByVersionDesc(
                eq(StandardLoanProduct.BUSINESS_TERM_LOAN), eq("ENCORE"), eq("OWN_BOOK"), eq("ACTIVE"), any(LocalDate.class));

        // Retry createLmsAccountOnSanction: must skip openLoanAccount since handover already exists.
        lmsService.createLmsAccountOnSanction(app);
        verify(encoreLmsApi, times(1)).openLoanAccountWithJson(anyString(), anyString());
    }

    @Test
    void HISTORICAL_APPLICATION_compatibility_usesPersistedLmsProductCode_noRewrite() throws Exception {
        String legacyProductCode = "LEGACY_LMS_PRODUCT_CODE_01";

        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("BT-E2E-HIST-1")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .sanctionedAmount(new BigDecimal("120000"))
                .approvedRate(new BigDecimal("19.0"))
                .tenureMonths(12)
                .lmsTenureUnit("Month")
                .lmsProductCode(legacyProductCode)
                .workflowResolutionSource("EXPLICIT")
                .categorySelectedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .intakeSegment(IntakeSegment.BORROWER)
                .build();

        ExternalProductMappingPinningService pinning = pinningService();
        pinning.pinEncoreMappingIfNeeded(app);

        assertEquals(legacyProductCode, app.getLmsProductCode());
        assertEquals(null, app.getExternalProductMappingId());
        assertEquals(null, app.getExternalProductMappingVersion());
        verify(externalProductMappingRepository, never()).findByLosProductCodeAndExternalSystemOrderByVersionDesc(
                anyString(), anyString());

        LmsService lmsService = buildLmsService();

        when(invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)).thenReturn(false);
        when(lmsProgramResolver.resolveForApplication(app)).thenReturn(Optional.empty());
        when(lmsProgramResolver.resolveByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());
        when(workflowLmsProductResolver.resolveFullMapping(null, app.getBorrowerType(), app.getLoanProduct()))
                .thenReturn(Optional.empty());

        when(encoreLmsApi.isActive()).thenReturn(true);
        when(handoverRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());
        when(summaryRepository.findByApplicationNumber(app.getApplicationNumber())).thenReturn(Optional.empty());

        when(encoreLmsApi.openLoanAccountWithJson(anyString(), anyString())).thenReturn("ENCORE_ACC_HIST_1");
        when(encoreLmsApi.findRepaymentSchedule("ENCORE_ACC_HIST_1")).thenReturn(List.of());

        when(handoverRepository.save(any(LmsLoanHandover.class))).thenAnswer(inv -> inv.getArgument(0));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        lmsService.createLmsAccountOnSanction(app);
        verify(encoreLmsApi).openLoanAccountWithJson(anyString(), jsonCaptor.capture());

        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals(legacyProductCode, node.get("productCode").asText());
    }

    /**
     * Convenience place to hold any future per-scenario derived outputs
     * (IDs, versions, codes) without making tests harder to scan.
     */
    private static class TestContext {
    }
}

