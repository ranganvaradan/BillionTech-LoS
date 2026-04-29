package com.billiontech.bankstatement.service.extraction;

import com.billiontech.bankstatement.model.entity.BankParserConfig;
import com.billiontech.bankstatement.repository.BankParserConfigRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankFormatDetector {

    private final BankParserConfigRepository configRepository;

    private static final Map<String, List<String>> BANK_KEYWORDS = new LinkedHashMap<>();

    static {
        BANK_KEYWORDS.put("SBI", List.of("STATE BANK OF INDIA", "STATE BANK", "SBI", "SBIN"));
        BANK_KEYWORDS.put("HDFC", List.of("HDFC BANK", "HDFC LTD", "HDFCBANK"));
        BANK_KEYWORDS.put("ICICI", List.of("ICICI BANK", "ICICI LTD"));
        BANK_KEYWORDS.put("AXIS", List.of("AXIS BANK", "AXIS LTD"));
        BANK_KEYWORDS.put("PNB", List.of("PUNJAB NATIONAL BANK", "PNB"));
        BANK_KEYWORDS.put("KOTAK", List.of("KOTAK MAHINDRA", "KOTAK BANK"));
        BANK_KEYWORDS.put("YES", List.of("YES BANK"));
        BANK_KEYWORDS.put("INDUSIND", List.of("INDUSIND BANK", "INDUSIND"));
        BANK_KEYWORDS.put("BOB", List.of("BANK OF BARODA", "BOB"));
        BANK_KEYWORDS.put("CANARA", List.of("CANARA BANK"));
        BANK_KEYWORDS.put("UNION", List.of("UNION BANK OF INDIA", "UNION BANK"));
        BANK_KEYWORDS.put("IDBI", List.of("IDBI BANK", "IDBI LTD"));
        BANK_KEYWORDS.put("FEDERAL", List.of("FEDERAL BANK"));
        BANK_KEYWORDS.put("RBL", List.of("RBL BANK", "RATNAKAR BANK"));
        BANK_KEYWORDS.put("BANDHAN", List.of("BANDHAN BANK"));
        BANK_KEYWORDS.put("IDFC", List.of("IDFC FIRST", "IDFC BANK"));
        BANK_KEYWORDS.put("AU_SFB", List.of("AU SMALL FINANCE", "AU BANK"));
        BANK_KEYWORDS.put("INDIAN", List.of("INDIAN BANK"));
        BANK_KEYWORDS.put("BOI", List.of("BANK OF INDIA"));
        BANK_KEYWORDS.put("CBI", List.of("CENTRAL BANK OF INDIA", "CENTRAL BANK"));
        BANK_KEYWORDS.put("UCO", List.of("UCO BANK"));
        BANK_KEYWORDS.put("PSB", List.of("PUNJAB & SIND", "PUNJAB AND SIND"));
        BANK_KEYWORDS.put("IOB", List.of("INDIAN OVERSEAS BANK", "IOB"));
        BANK_KEYWORDS.put("KARNATAKA", List.of("KARNATAKA BANK"));
        BANK_KEYWORDS.put("SIB", List.of("SOUTH INDIAN BANK"));
    }

    private static final Map<String, String> BANK_FULL_NAMES = new LinkedHashMap<>();

    static {
        BANK_FULL_NAMES.put("SBI", "State Bank of India");
        BANK_FULL_NAMES.put("HDFC", "HDFC Bank");
        BANK_FULL_NAMES.put("ICICI", "ICICI Bank");
        BANK_FULL_NAMES.put("AXIS", "Axis Bank");
        BANK_FULL_NAMES.put("PNB", "Punjab National Bank");
        BANK_FULL_NAMES.put("KOTAK", "Kotak Mahindra Bank");
        BANK_FULL_NAMES.put("YES", "Yes Bank");
        BANK_FULL_NAMES.put("INDUSIND", "IndusInd Bank");
        BANK_FULL_NAMES.put("BOB", "Bank of Baroda");
        BANK_FULL_NAMES.put("CANARA", "Canara Bank");
        BANK_FULL_NAMES.put("UNION", "Union Bank of India");
        BANK_FULL_NAMES.put("IDBI", "IDBI Bank");
        BANK_FULL_NAMES.put("FEDERAL", "Federal Bank");
        BANK_FULL_NAMES.put("RBL", "RBL Bank");
        BANK_FULL_NAMES.put("BANDHAN", "Bandhan Bank");
        BANK_FULL_NAMES.put("IDFC", "IDFC First Bank");
        BANK_FULL_NAMES.put("AU_SFB", "AU Small Finance Bank");
        BANK_FULL_NAMES.put("INDIAN", "Indian Bank");
        BANK_FULL_NAMES.put("BOI", "Bank of India");
        BANK_FULL_NAMES.put("CBI", "Central Bank of India");
        BANK_FULL_NAMES.put("UCO", "UCO Bank");
        BANK_FULL_NAMES.put("PSB", "Punjab & Sind Bank");
        BANK_FULL_NAMES.put("IOB", "Indian Overseas Bank");
        BANK_FULL_NAMES.put("KARNATAKA", "Karnataka Bank");
        BANK_FULL_NAMES.put("SIB", "South Indian Bank");
    }

    private static final Map<String, String> IFSC_PREFIX_MAP = new LinkedHashMap<>();

    static {
        IFSC_PREFIX_MAP.put("SBIN", "SBI");
        IFSC_PREFIX_MAP.put("HDFC", "HDFC");
        IFSC_PREFIX_MAP.put("ICIC", "ICICI");
        IFSC_PREFIX_MAP.put("UTIB", "AXIS");
        IFSC_PREFIX_MAP.put("PUNB", "PNB");
        IFSC_PREFIX_MAP.put("KKBK", "KOTAK");
        IFSC_PREFIX_MAP.put("YESB", "YES");
        IFSC_PREFIX_MAP.put("INDB", "INDUSIND");
        IFSC_PREFIX_MAP.put("BARB", "BOB");
        IFSC_PREFIX_MAP.put("CNRB", "CANARA");
        IFSC_PREFIX_MAP.put("UBIN", "UNION");
        IFSC_PREFIX_MAP.put("IBKL", "IDBI");
        IFSC_PREFIX_MAP.put("FDRL", "FEDERAL");
        IFSC_PREFIX_MAP.put("RATN", "RBL");
        IFSC_PREFIX_MAP.put("BDBL", "BANDHAN");
        IFSC_PREFIX_MAP.put("IDFB", "IDFC");
        IFSC_PREFIX_MAP.put("AUBL", "AU_SFB");
        IFSC_PREFIX_MAP.put("IDIB", "INDIAN");
        IFSC_PREFIX_MAP.put("BKID", "BOI");
        IFSC_PREFIX_MAP.put("CBIN", "CBI");
        IFSC_PREFIX_MAP.put("UCBA", "UCO");
        IFSC_PREFIX_MAP.put("PSIB", "PSB");
        IFSC_PREFIX_MAP.put("IOBA", "IOB");
        IFSC_PREFIX_MAP.put("KARB", "KARNATAKA");
        IFSC_PREFIX_MAP.put("SIBL", "SIB");
    }

    public DetectedBank detect(String textContent) {
        if (textContent == null || textContent.isBlank()) {
            return DetectedBank.builder().bankCode("UNKNOWN").bankName("Unknown Bank").build();
        }

        String upper = textContent.toUpperCase();

        // Try IFSC code detection first
        java.util.regex.Matcher ifscMatcher = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}").matcher(upper);
        if (ifscMatcher.find()) {
            String ifsc = ifscMatcher.group();
            String prefix = ifsc.substring(0, 4);
            String bankCode = IFSC_PREFIX_MAP.get(prefix);
            if (bankCode != null) {
                return DetectedBank.builder()
                        .bankCode(bankCode)
                        .bankName(BANK_FULL_NAMES.getOrDefault(bankCode, bankCode))
                        .ifscCode(ifsc)
                        .build();
            }
        }

        // Try keyword detection
        for (Map.Entry<String, List<String>> entry : BANK_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (upper.contains(keyword)) {
                    return DetectedBank.builder()
                            .bankCode(entry.getKey())
                            .bankName(BANK_FULL_NAMES.getOrDefault(entry.getKey(), entry.getKey()))
                            .build();
                }
            }
        }

        return DetectedBank.builder().bankCode("UNKNOWN").bankName("Unknown Bank").build();
    }

    public String getBankName(String bankCode) {
        return BANK_FULL_NAMES.getOrDefault(bankCode, bankCode);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetectedBank {
        private String bankCode;
        private String bankName;
        private String ifscCode;
    }
}
