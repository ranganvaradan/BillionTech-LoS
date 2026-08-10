package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquifaxBureauAccountExtractorTest {

    @Test
    void extractsAccountsListFromSampleXml() {
        String xml = """
                <?xml version='1.0' encoding='UTF-8'?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soapenv:Body>
                <sch:InquiryResponse xmlns:sch="http://services.equifax.com/eport/ws/schemas/1.0">
                <sch:InquiryResponseHeader>
                  <sch:SuccessCode>1</sch:SuccessCode>
                  <sch:Date>22-10-2025</sch:Date>
                </sch:InquiryResponseHeader>
                <sch:Score><sch:Value>720</sch:Value></sch:Score>
                <sch:Account seq="1" ReportedDate="2025-10-15">
                  <sch:AccountNumber>1234567890</sch:AccountNumber>
                  <sch:Institution>TEST BANK</sch:Institution>
                  <sch:AccountType>Personal Loan</sch:AccountType>
                  <sch:OwnershipType>Individual</sch:OwnershipType>
                  <sch:Balance>15000</sch:Balance>
                  <sch:SanctionAmount>50000</sch:SanctionAmount>
                  <sch:HighCredit>50000</sch:HighCredit>
                  <sch:Open>Yes</sch:Open>
                  <sch:DateOpened>2022-12-20</sch:DateOpened>
                  <sch:DateReported>2025-10-15</sch:DateReported>
                  <sch:AccountStatus>Current Account</sch:AccountStatus>
                  <sch:InstallmentAmount>2500</sch:InstallmentAmount>
                  <sch:History48Months>
                    <sch:Month key="10-25">
                      <sch:PaymentStatus>000</sch:PaymentStatus>
                      <sch:DaysPastDue>0</sch:DaysPastDue>
                    </sch:Month>
                    <sch:Month key="09-25">
                      <sch:PaymentStatus>030</sch:PaymentStatus>
                      <sch:DaysPastDue>30</sch:DaysPastDue>
                    </sch:Month>
                  </sch:History48Months>
                </sch:Account>
                <sch:Account seq="2" ReportedDate="2025-09-01">
                  <sch:AccountNumber>********99</sch:AccountNumber>
                  <sch:Institution>HFCL</sch:Institution>
                  <sch:AccountType>Housing Loan</sch:AccountType>
                  <sch:Balance>0</sch:Balance>
                  <sch:SanctionAmount>2000000</sch:SanctionAmount>
                  <sch:Open>No</sch:Open>
                  <sch:DateOpened>2018-01-01</sch:DateOpened>
                  <sch:DateReported>2025-09-01</sch:DateReported>
                  <sch:AccountStatus>Closed</sch:AccountStatus>
                </sch:Account>
                </sch:InquiryResponse>
                </soapenv:Body>
                </soapenv:Envelope>
                """;

        Map<String, Object> data = EquifaxBureauAccountExtractor.enrichFromXml(xml, new LinkedHashMap<>());
        assertEquals(EquifaxBureauAccountExtractor.PARSER_VERSION, data.get("parserVersion"));
        assertEquals("2025-10-22", data.get("reportDate"));
        assertEquals("OK", data.get("tradelineExtractionStatus"));
        assertTrue(Boolean.TRUE.equals(data.get("tradelinesPresent")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) data.get("accounts");
        assertEquals(2, accounts.size());

        Map<String, Object> a0 = accounts.get(0);
        assertEquals("Personal Loan", a0.get("AccountType"));
        assertEquals("TEST BANK", a0.get("MemberName"));
        assertEquals("15000", a0.get("Balance"));
        assertEquals("2500", a0.get("InstallmentAmount"));
        assertNotNull(a0.get("AccountNumberHash"));
        assertEquals("7890", a0.get("AccountNumberLast4"));
        assertFalse(a0.containsKey("AccountNumber"));
        assertNotNull(a0.get("HistoryMonths"));
        assertEquals(2, ((List<?>) a0.get("HistoryMonths")).size());
    }

    @Test
    void simulatedMissingDoesNotInventAccounts() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("creditScore", 720);
        data.put("totalAccounts", 5);
        EquifaxBureauAccountExtractor.markSimulatedMissing(data);
        assertEquals("MISSING", data.get("tradelineExtractionStatus"));
        assertFalse(data.containsKey("accounts"));
        assertFalse(Boolean.TRUE.equals(data.get("tradelinesPresent")));
    }
}
