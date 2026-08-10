package com.los.core.creditintelligence.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.service.aa.providers.AaFiDataParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AaFiDataParserBankingTest {

    @Test
    void simulatedSummaryIncludesTransactionsAcrossAccounts() {
        Map<String, Object> summary = AaFiDataParser.simulatedSummary();
        assertThat(summary.get("transactions")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns = (List<Map<String, Object>>) summary.get("transactions");
        assertThat(txns).isNotEmpty();
        assertThat(txns.stream().anyMatch(t -> String.valueOf(t.get("narration")).contains("EMI"))).isTrue();
        assertThat(txns.stream().anyMatch(t -> String.valueOf(t.get("narration")).contains("CASH DEPOSIT"))).isTrue();
        assertThat(txns.stream().anyMatch(t -> String.valueOf(t.get("narration")).contains("CHEQUE RETURN"))).isTrue();
        assertThat(txns.stream().anyMatch(t -> String.valueOf(t.get("narration")).contains("SELF TRANSFER"))).isTrue();
        assertThat(txns.stream().anyMatch(t -> String.valueOf(t.get("narration")).contains("LOAN DISBURSEMENT"))).isTrue();
        assertThat(summary.get("accountCount")).isEqualTo(2);
        assertThat(summary.get("avgMonthlyInflow")).isNotNull();
    }

    @Test
    void parseSetuFiPayloadCollectsTransactionsWhenPresent() throws Exception {
        String json = """
                {
                  "accounts": [{
                    "type": "CURRENT",
                    "bankName": "HDFC",
                    "maskedAccNumber": "XXXX5678",
                    "currentBalance": 100000,
                    "Transactions": [
                      {"date":"2026-01-15","amount":5000,"type":"CREDIT","narration":"UPI CR","mode":"UPI","balance":105000,"txnId":"T1"}
                    ]
                  }]
                }
                """;
        var root = new ObjectMapper().readTree(json);
        Map<String, Object> summary = AaFiDataParser.parseSetuFiPayload(root);
        assertThat(summary.get("transactions")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns = (List<Map<String, Object>>) summary.get("transactions");
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).get("txnId")).isEqualTo("T1");
    }

    @Test
    void parseSetuFiPayloadDoesNotInventTransactions() throws Exception {
        String json = """
                {"accounts":[{"type":"SAVINGS","bankName":"SBI","maskedAccNumber":"XXXX1234","currentBalance":1000}]}
                """;
        var root = new ObjectMapper().readTree(json);
        Map<String, Object> summary = AaFiDataParser.parseSetuFiPayload(root);
        assertThat(summary.containsKey("transactions")).isFalse();
    }
}
