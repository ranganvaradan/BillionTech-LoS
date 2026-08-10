package com.los.core.service.kfs.edi;

import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.service.LmsApplicationConfigResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EdiKfsTemplateContextBuilder {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(IST);
    private static final String REDUCING_BALANCE_BASICS =
            "Interest is computed on daily reducing balance as per Encore LMS schedule.";

    private final EdiKfsScheduleResolver scheduleResolver;
    private final LmsLoanHandoverRepository handoverRepository;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;
    private final EdiKfsProperties ediKfsProperties;

    public EdiKfsTemplateContext build(LoanApplication app, KfsDocument kfs) {
        LocalDate today = LocalDate.now(IST);
        String currentDate = DATE_FMT.format(today.atStartOfDay(IST).toInstant());

        String fullName = ApplicationPartyResolver.resolveDisplayName(app);
        String address = resolveAddress(app);

        BigDecimal principal = kfs.getSanctionedAmount() != null
                ? kfs.getSanctionedAmount()
                : BigDecimal.ZERO;
        BigDecimal loanRounded = principal.setScale(0, RoundingMode.DOWN);
        String limit = loanRounded.toPlainString();

        BigDecimal rate = kfs.getInterestRate() != null ? kfs.getInterestRate() : BigDecimal.ZERO;
        String interest = rate.stripTrailingZeros().toPlainString();

        int tenureDays = kfs.getTenureMonths() != null ? kfs.getTenureMonths() : 0;
        String tenor = String.valueOf(tenureDays);

        String tenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
        LocalDate expiry = "day".equalsIgnoreCase(tenureUnit)
                ? today.plusDays(Math.max(tenureDays, 0))
                : today.plusYears(1);
        String expiryDate = DATE_FMT.format(expiry.atStartOfDay(IST).toInstant());

        String appRefNo = handoverRepository
                .findByApplicationNumber(app.getApplicationNumber())
                .map(h -> h.getEncoreAccountId())
                .filter(id -> id != null && !id.isBlank())
                .orElse(app.getApplicationNumber() != null ? app.getApplicationNumber() : "");

        LocalDate kfsValidity = KfsWorkingDayCalculator.addWorkingDays(
                today, 3, ediKfsProperties.getHolidays());
        String kfsExpDate = DATE_FMT.format(kfsValidity.atStartOfDay(IST).toInstant());

        List<EdiKfsScheduleRow> schedule = scheduleResolver.resolve(app, kfs);
        String firstRepaymentDate = schedule.isEmpty() ? "" : schedule.get(0).demandDate();

        BigDecimal installment = kfs.getEmiAmount();
        if ((installment == null || installment.compareTo(BigDecimal.ZERO) <= 0) && !schedule.isEmpty()) {
            installment = schedule.get(0).installmentAmount();
        }
        if (installment == null) {
            installment = BigDecimal.ZERO;
        }

        int noOfInst = !schedule.isEmpty() ? schedule.size() : tenureDays;
        BigDecimal totalInterest = EdiKfsScheduleResolver.resolveTotalInterest(
                schedule, principal, installment, tenureDays);
        BigDecimal totalAmount = loanRounded.add(totalInterest).setScale(0, RoundingMode.DOWN);

        BigDecimal apr = kfs.getApr() != null ? kfs.getApr() : BigDecimal.ZERO;
        String annualPercentage = apr.setScale(2, RoundingMode.HALF_UP).toPlainString();
        BigDecimal penal = apr.add(new BigDecimal("12")).setScale(2, RoundingMode.DOWN);

        int amountWords = loanRounded.intValue();
        String loanAmtText = IndianAmountInWords.convert(amountWords);

        String repaymentFrequency = capitalizeTenureUnit(tenureUnit);

        return new EdiKfsTemplateContext(
                currentDate,
                fullName,
                address,
                "Rs." + limit,
                "Rs." + loanRounded.toPlainString(),
                loanRounded.toPlainString(),
                loanAmtText,
                interest,
                tenor,
                expiryDate,
                appRefNo,
                kfsExpDate,
                firstRepaymentDate,
                "Daily",
                repaymentFrequency,
                "Rs." + totalInterest.toPlainString(),
                "Rs." + loanRounded.toPlainString(),
                "Rs." + totalAmount.toPlainString(),
                annualPercentage,
                REDUCING_BALANCE_BASICS,
                String.valueOf(noOfInst),
                "Rs." + installment.toPlainString(),
                penal.toPlainString(),
                schedule);
    }

    private static String resolveAddress(LoanApplication app) {
        String line = ApplicationPartyResolver.resolveAddressLine(app);
        Map<String, Object> pi = app.getPersonalInfo();
        String city = pi != null ? str(pi.get("city")) : "";
        String state = pi != null ? str(pi.get("state")) : "";
        String pin = ApplicationPartyResolver.resolvePincode(app);
        StringBuilder sb = new StringBuilder();
        if (!line.isBlank()) {
            sb.append(line);
        }
        if (!city.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(city);
        }
        if (!state.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(state);
        }
        if (!pin.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(pin);
        }
        String built = sb.toString().trim();
        return built.isEmpty() ? "—" : built;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String capitalizeTenureUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "Month";
        }
        String t = unit.trim().toLowerCase();
        return switch (t) {
            case "day" -> "Day";
            case "week" -> "Week";
            case "month" -> "Month";
            default -> unit.trim();
        };
    }
}
