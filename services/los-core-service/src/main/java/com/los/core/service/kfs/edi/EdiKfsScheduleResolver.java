package com.los.core.service.kfs.edi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.encore.client.api.EncoreLmsApi;
import com.los.encore.client.support.EncoreRepaymentScheduleParser;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.legacy.BlCoreEncoreLmsAdapter;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.service.LmsApplicationConfigResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves repayment schedule rows for EDI KFS:
 * live Encore account → cached handover JSON → KFS stored JSON → pre-open JSON on KFS → live pre-open API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdiKfsScheduleResolver {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final LmsLoanHandoverRepository handoverRepository;
    private final EncoreLmsApi encoreLmsApi;
    private final BlCoreEncoreLmsAdapter blCoreEncoreLmsAdapter;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;
    private final ObjectMapper objectMapper;

    public List<EdiKfsScheduleRow> resolve(LoanApplication app, KfsDocument kfs) {
        List<Map<String, Object>> raw = resolveRawScheduleMaps(app, kfs);
        List<EdiKfsScheduleRow> rows = mapRows(raw);
        log.info("EDI KFS resolved {} repayment rows for app {}",
                rows.size(), app != null ? app.getApplicationNumber() : "?");
        return rows;
    }

    private List<Map<String, Object>> resolveRawScheduleMaps(LoanApplication app, KfsDocument kfs) {
        String appNo = app != null ? app.getApplicationNumber() : null;
        Optional<LmsLoanHandover> handoverOpt = appNo != null
                ? handoverRepository.findByApplicationNumber(appNo)
                : Optional.empty();

        String encoreAccountId = resolveEncoreAccountId(app, handoverOpt.orElse(null));

        if (encoreAccountId != null && !encoreAccountId.isBlank() && encoreLmsApi.isActive()) {
            try {
                List<Map<String, Object>> live = encoreLmsApi.findRepaymentSchedule(encoreAccountId);
                if (live != null && !live.isEmpty()) {
                    log.info("EDI KFS schedule from live Encore ({} rows) for app {}", live.size(), appNo);
                    return live;
                }
            } catch (Exception e) {
                log.warn("Live Encore repayment schedule unavailable for {}: {}", appNo, e.getMessage());
            }
        }

        if (handoverOpt.isPresent()) {
            List<Map<String, Object>> cached = fromHandoverCache(handoverOpt.get());
            if (!cached.isEmpty()) {
                log.info("EDI KFS schedule from cached handover JSON ({} rows) for app {}", cached.size(), appNo);
                return cached;
            }
        }

        List<Map<String, Object>> fromKfsTerms = fromKfsStoredSchedule(kfs);
        if (!fromKfsTerms.isEmpty()) {
            log.info("EDI KFS schedule from KFS additionalTerms ({} rows) for app {}", fromKfsTerms.size(), appNo);
            return fromKfsTerms;
        }

        List<Map<String, Object>> preOpen = fromPreOpenJson(kfs);
        if (!preOpen.isEmpty()) {
            log.info("EDI KFS schedule from encorePreOpenSummaryJson ({} rows) for app {}", preOpen.size(), appNo);
            return preOpen;
        }

        if (app != null && encoreLmsApi.isActive()) {
            List<Map<String, Object>> livePreOpen = fromLivePreOpenSummary(app, kfs, encoreAccountId);
            if (!livePreOpen.isEmpty()) {
                log.info("EDI KFS schedule from live findPreOpenSummary ({} rows) for app {}",
                        livePreOpen.size(), appNo);
                return livePreOpen;
            }
        }

        List<Map<String, Object>> computed = fromComputedSchedule(app, kfs);
        if (!computed.isEmpty()) {
            log.info("EDI KFS schedule from computed daily reducing balance ({} rows) for app {}",
                    computed.size(), appNo);
            return computed;
        }

        log.warn("EDI KFS could not resolve repayment schedule for app {} (encoreAccountId={})",
                appNo, encoreAccountId);
        return List.of();
    }

    private List<Map<String, Object>> fromComputedSchedule(LoanApplication app, KfsDocument kfs) {
        if (app == null || kfs == null) {
            return List.of();
        }
        if (!"day".equalsIgnoreCase(lmsApplicationConfigResolver.resolveTenureUnit(app))) {
            return List.of();
        }
        BigDecimal principal = kfs.getSanctionedAmount() != null
                ? kfs.getSanctionedAmount()
                : app.getSanctionedAmount();
        BigDecimal rate = kfs.getInterestRate() != null
                ? kfs.getInterestRate()
                : app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        int tenureDays = kfs.getTenureMonths() != null
                ? kfs.getTenureMonths()
                : app.getTenureMonths() != null ? app.getTenureMonths() : 0;
        if (principal == null || rate == null || tenureDays <= 0) {
            return List.of();
        }
        BigDecimal installment = kfs.getEmiAmount();
        LocalDate start = LocalDate.now(IST).plusDays(1);
        return EdiKfsComputedScheduleBuilder.buildRawSchedule(
                principal, rate, tenureDays, installment, start);
    }

    private static String resolveEncoreAccountId(LoanApplication app, LmsLoanHandover handover) {
        if (handover != null && handover.getEncoreAccountId() != null && !handover.getEncoreAccountId().isBlank()) {
            return handover.getEncoreAccountId();
        }
        if (app == null) {
            return null;
        }
        if (app.getLmsReferenceId() != null && !app.getLmsReferenceId().isBlank()
                && !app.getLmsReferenceId().startsWith("LMS-")) {
            return app.getLmsReferenceId();
        }
        return null;
    }

    private List<Map<String, Object>> fromHandoverCache(LmsLoanHandover handover) {
        return parseScheduleJson(handover.getEncoreRepaymentScheduleJson(), handover.getApplicationNumber());
    }

    private List<Map<String, Object>> fromKfsStoredSchedule(KfsDocument kfs) {
        if (kfs == null || kfs.getAdditionalTerms() == null) {
            return List.of();
        }
        Object raw = kfs.getAdditionalTerms().get("encoreRepaymentScheduleJson");
        if (raw == null) {
            return List.of();
        }
        return parseScheduleJson(raw, kfs.getApplicationId() != null ? kfs.getApplicationId().toString() : "?");
    }

    private List<Map<String, Object>> parseScheduleJson(Object raw, String context) {
        if (raw == null) {
            return List.of();
        }
        try {
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            if (json.isBlank()) {
                return List.of();
            }
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<Map<String, Object>> list = objectMapper.convertValue(node, new TypeReference<>() {});
                return list != null ? list : List.of();
            }
            if (node.isObject()) {
                return EncoreRepaymentScheduleParser.parseFromSummaryRoot(node);
            }
        } catch (Exception e) {
            log.warn("Could not parse Encore repayment schedule JSON for {}: {}", context, e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fromPreOpenJson(KfsDocument kfs) {
        if (kfs.getAdditionalTerms() == null) {
            return List.of();
        }
        Object raw = kfs.getAdditionalTerms().get("encorePreOpenSummaryJson");
        if (raw == null) {
            return List.of();
        }
        try {
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray() && !root.isEmpty()) {
                root = root.get(0);
            }
            return EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);
        } catch (Exception e) {
            log.warn("Could not parse encorePreOpenSummaryJson for EDI schedule: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * bl-core {@code findPreOpenSummary} — works before/without a live Encore account and includes repaymentSchedule.
     */
    private List<Map<String, Object>> fromLivePreOpenSummary(
            LoanApplication app, KfsDocument kfs, String encoreAccountId) {
        try {
            BigDecimal amt = kfs.getSanctionedAmount() != null
                    ? kfs.getSanctionedAmount()
                    : app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
            int tenure = kfs.getTenureMonths() != null ? kfs.getTenureMonths()
                    : app.getTenureMonths() != null ? app.getTenureMonths() : 0;
            if (amt == null || tenure <= 0) {
                return List.of();
            }
            String product = lmsApplicationConfigResolver.resolveEncoreProductCode(app);
            String tenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
            String accountId = encoreAccountId != null && !encoreAccountId.isBlank()
                    ? encoreAccountId
                    : app.getApplicationNumber();
            String body = blCoreEncoreLmsAdapter.buildPreOpenSummaryRequestBody(
                    accountId, amt, LocalDate.now(), product, tenure, tenureUnit,
                    ApplicationPartyResolver.resolvePincode(app));
            String resp = encoreLmsApi.findPreOpenSummaryRaw(body);
            JsonNode root = objectMapper.readTree(resp);
            if (root.isArray() && !root.isEmpty()) {
                root = root.get(0);
            }
            return EncoreRepaymentScheduleParser.parseFromSummaryRoot(root);
        } catch (Exception e) {
            log.warn("Live findPreOpenSummary schedule unavailable for {}: {}",
                    app.getApplicationNumber(), e.getMessage());
            return List.of();
        }
    }

    static List<EdiKfsScheduleRow> mapRows(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<EdiKfsScheduleRow> rows = new ArrayList<>();
        for (Map<String, Object> e : raw) {
            int seq = toInt(e.get("sequenceNum"));
            String date = str(e.get("valueDateStr"));
            if (date.isBlank()) {
                date = str(e.get("valueDate"));
            }

            BigDecimal inst = toBd(e.get("installmentAmount"));
            if (inst.compareTo(BigDecimal.ZERO) == 0) {
                inst = toBd(e.get("amountDue"));
            }

            // bl-core editIndividualSanctionFile uses part1/part2 (normalInterestRate/principalRate) as amounts
            BigDecimal interest = toBd(e.get("interestAmount"));
            if (interest.compareTo(BigDecimal.ZERO) == 0) {
                interest = toBd(e.get("normalInterestRate"));
            }
            BigDecimal principal = toBd(e.get("principalAmount"));
            if (principal.compareTo(BigDecimal.ZERO) == 0) {
                principal = toBd(e.get("principalRate"));
            }
            if (principal.compareTo(BigDecimal.ZERO) == 0 && inst.compareTo(BigDecimal.ZERO) > 0) {
                principal = inst.subtract(interest).max(BigDecimal.ZERO);
            }

            BigDecimal balance = toBd(e.get("balance"));
            if (inst.compareTo(BigDecimal.ZERO) == 0 && interest.add(principal).compareTo(BigDecimal.ZERO) > 0) {
                inst = interest.add(principal);
            }

            rows.add(new EdiKfsScheduleRow(seq, date, inst, interest, principal, balance));
        }
        return rows;
    }

    /**
     * Total interest: sum schedule interest components when present; else flat-rate daily
     * (installment × tenureDays − principal).
     */
    public static BigDecimal resolveTotalInterest(
            List<EdiKfsScheduleRow> schedule,
            BigDecimal principal,
            BigDecimal installment,
            int tenureDays) {
        if (schedule != null && !schedule.isEmpty()) {
            BigDecimal sum = schedule.stream()
                    .map(EdiKfsScheduleRow::normalInterestAmount)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                return sum.setScale(0, RoundingMode.DOWN);
            }
            BigDecimal totalRepay = schedule.stream()
                    .map(EdiKfsScheduleRow::installmentAmount)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalRepay.compareTo(BigDecimal.ZERO) > 0 && principal != null) {
                return totalRepay.subtract(principal).max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
            }
        }
        if (installment != null && tenureDays > 0 && principal != null) {
            return installment
                    .multiply(BigDecimal.valueOf(tenureDays))
                    .subtract(principal)
                    .max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.DOWN);
        }
        return BigDecimal.ZERO;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return 0;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim().replace(",", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
