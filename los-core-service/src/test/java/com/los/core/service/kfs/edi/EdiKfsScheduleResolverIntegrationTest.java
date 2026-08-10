package com.los.core.service.kfs.edi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.encore.client.api.EncoreLmsApi;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.legacy.BlCoreEncoreLmsAdapter;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.service.LmsApplicationConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdiKfsScheduleResolverIntegrationTest {

    private static final String PRE_OPEN_JSON = """
            {
              "repaymentSchedule": [
                {
                  "sequenceNum": 1,
                  "amount1": "500.00",
                  "amount2": "9500.00",
                  "amount3": "500.00",
                  "valueDateStr": "2026-06-01",
                  "part1": "50.0",
                  "part2": "450.0"
                }
              ]
            }
            """;

    @Mock
    private LmsLoanHandoverRepository handoverRepository;
    @Mock
    private EncoreLmsApi encoreLmsApi;
    @Mock
    private BlCoreEncoreLmsAdapter blCoreEncoreLmsAdapter;
    @Mock
    private LmsApplicationConfigResolver lmsApplicationConfigResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EdiKfsScheduleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EdiKfsScheduleResolver(
                handoverRepository,
                encoreLmsApi,
                blCoreEncoreLmsAdapter,
                lmsApplicationConfigResolver,
                objectMapper);
    }

    @Test
    void resolve_fallsBackToLivePreOpenWhenHandoverAndCacheEmpty() throws Exception {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("LOS-IND-TEST-001")
                .sanctionedAmount(new BigDecimal("10000"))
                .tenureMonths(90)
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(UUID.randomUUID())
                .sanctionedAmount(new BigDecimal("10000"))
                .tenureMonths(90)
                .additionalTerms(new LinkedHashMap<>())
                .build();

        when(handoverRepository.findByApplicationNumber("LOS-IND-TEST-001")).thenReturn(Optional.empty());
        when(encoreLmsApi.isActive()).thenReturn(true);
        when(lmsApplicationConfigResolver.resolveEncoreProductCode(app)).thenReturn("IPPO01");
        when(lmsApplicationConfigResolver.resolveTenureUnit(app)).thenReturn("Day");
        when(blCoreEncoreLmsAdapter.buildPreOpenSummaryRequestBody(
                anyString(), any(), any(), anyString(), any(Integer.class), anyString()))
                .thenReturn("{\"accountId\":\"LOS-IND-TEST-001\"}");
        when(encoreLmsApi.findPreOpenSummaryRaw(anyString())).thenReturn(PRE_OPEN_JSON);

        List<EdiKfsScheduleRow> rows = resolver.resolve(app, kfs);
        assertEquals(1, rows.size());
        assertEquals("2026-06-01", rows.get(0).demandDate());
    }

    @Test
    void resolve_usesKfsStoredScheduleJson() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("LOS-IND-TEST-002")
                .build();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sequenceNum", 1);
        row.put("valueDateStr", "2026-07-01");
        row.put("installmentAmount", "600");
        row.put("interestAmount", 60);
        row.put("principalAmount", 540);
        row.put("balance", "9400");

        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("encoreRepaymentScheduleJson", List.of(row));

        KfsDocument kfs = KfsDocument.builder()
                .applicationId(UUID.randomUUID())
                .additionalTerms(terms)
                .build();

        when(handoverRepository.findByApplicationNumber("LOS-IND-TEST-002")).thenReturn(Optional.empty());

        List<EdiKfsScheduleRow> rows = resolver.resolve(app, kfs);
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("600"), rows.get(0).installmentAmount());
    }

    @Test
    void resolve_usesLmsReferenceIdWhenHandoverMissing() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("LOS-IND-TEST-003")
                .lmsReferenceId("0000IPP00999")
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(UUID.randomUUID())
                .additionalTerms(new LinkedHashMap<>())
                .build();

        when(handoverRepository.findByApplicationNumber("LOS-IND-TEST-003")).thenReturn(Optional.empty());
        when(encoreLmsApi.isActive()).thenReturn(true);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sequenceNum", 1);
        row.put("valueDateStr", "2026-08-01");
        row.put("installmentAmount", "700");
        row.put("interestAmount", 70);
        row.put("principalAmount", 630);
        row.put("balance", "9300");
        when(encoreLmsApi.findRepaymentSchedule("0000IPP00999")).thenReturn(List.of(row));

        List<EdiKfsScheduleRow> rows = resolver.resolve(app, kfs);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).installmentAmount().compareTo(new BigDecimal("700")) == 0);
    }

    @Test
    void resolve_usesComputedScheduleWhenAllEncoreSourcesFail() {
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("LOS-IND-TEST-004")
                .sanctionedAmount(new BigDecimal("85000"))
                .approvedRate(new BigDecimal("18"))
                .tenureMonths(90)
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(UUID.randomUUID())
                .sanctionedAmount(new BigDecimal("85000"))
                .interestRate(new BigDecimal("18"))
                .tenureMonths(90)
                .emiAmount(new BigDecimal("950"))
                .additionalTerms(new LinkedHashMap<>())
                .build();

        when(handoverRepository.findByApplicationNumber("LOS-IND-TEST-004")).thenReturn(Optional.empty());
        when(encoreLmsApi.isActive()).thenReturn(false);
        when(lmsApplicationConfigResolver.resolveTenureUnit(app)).thenReturn("Day");

        List<EdiKfsScheduleRow> rows = resolver.resolve(app, kfs);
        assertEquals(90, rows.size());
        assertTrue(rows.get(0).installmentAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(rows.get(89).balance().compareTo(BigDecimal.ZERO) == 0);
    }
}
