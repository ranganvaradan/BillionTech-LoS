package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.List;

import static com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition.Capability;
import static com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition.DERIVED;
import static com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition.MANUAL;
import static com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition.RAW;

/**
 * GACAT-SOURCE-CATALOGUE-RECOVERY-1 / GACAT-PERSISTENCE-1.
 * <p>
 * Migration/bootstrap inventory only — Flyway V113 imports this once into DB.
 * Production runtime authority is the database via {@link CanonicalParameterRegistry#shared()}.
 * Do not use as a silent production fallback.
 * Inclusion ≠ production-ready.
 */
public final class GacatCatalogueSeed {

    private GacatCatalogueSeed() {}

    public static List<CanonicalParameterDefinition> all() {
        List<CanonicalParameterDefinition> p = new ArrayList<>();
        bureauRetailRaw(p);
        bureauRetailDerived(p);
        bureauCommercialRaw(p);
        bureauCommercialDerived(p);
        bankRaw(p);
        bankDerived(p);
        accountAggregator(p);
        gst(p);
        itrFinancials(p);
        kyc(p);
        applicationProduct(p);
        computed(p);
        return p;
    }

    /** Current seed inventory size. Tests should assert against this rather than a stale literal. */
    public static int inventorySize() {
        return all().size();
    }

    private static final String BR = "Bureau Retail";
    private static final String BC = "Bureau Commercial";
    private static final String BS = "Bank Statement";
    private static final String AA = "Account Aggregator";
    private static final String GST = "GST";
    private static final String FS = "Financial Statements";
    private static final String KYC = "KYC";
    private static final String APP = "Application";
    private static final String PROG = "Program / Product";
    private static final String MAN = "Manual Input";
    private static final String COMP = "Computed / Derived";

    private static void bureauRetailRaw(List<CanonicalParameterDefinition> p) {
        // Subject / score (Equifax InquiryResponse)
        raw(p, "bureau.score", "Bureau score", BR, "SCORE", "SCALAR",
                "InquiryResponse/Score/Value",
                List.of("cibil score", "credit bureau score", "bureau score", "credit score", "cibil"),
                "BureauMetricService / CreditControl bureauScore",
                "BUREAU_SCORE", "BUREAU_SCORE", true, true, false, true, true);
        raw(p, "bureau.score.name", "Bureau score product name", BR, "TEXT", "SCALAR",
                "InquiryResponse/Score/Name", List.of("score name", "ERS"),
                "CiBureauReport.scoreType / ci_bureau_report_summary.score_name", null, null,
                true, true, false, true, true);
        raw(p, "bureau.report.date", "Bureau report date", BR, "DATE", "SCALAR",
                "InquiryResponseHeader/Date", List.of("report date"),
                "CiBureauReport.reportDate", null, null,
                true, true, false, true, true);
        raw(p, "bureau.report.time", "Bureau report time", BR, "TEXT", "SCALAR",
                "InquiryResponseHeader/Time", List.of("report time"),
                "ci_bureau_report_summary.report_time", null, null,
                true, true, false, true, true);
        raw(p, "bureau.hit_code", "Equifax hit code", BR, "CODE", "SCALAR",
                "InquiryResponseHeader/HitCode", List.of("hit code"),
                "ci_bureau_report_summary.hit_code", null, null, true, true, false, true, true);
        raw(p, "bureau.success_code", "Equifax success code", BR, "CODE", "SCALAR",
                "InquiryResponseHeader/SuccessCode", List.of("success code"),
                "ci_bureau_report_summary.success_code", null, null, true, true, false, true, true);
        raw(p, "bureau.report_order_no", "Bureau report order number", BR, "TEXT", "SCALAR",
                "InquiryResponseHeader/ReportOrderNO", List.of("report order no"),
                "ci_bureau_report_summary.report_order_no", null, null, true, true, false, true, true);
        raw(p, "bureau.reason_code", "Bureau reason code", BR, "CODE", "PER_SCORE",
                "Score/ReasonCode (when present; not Equifax ScoringElements)", List.of("reason code", "score reason"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.scoring_element.code", "Bureau scoring element code", BR, "CODE", "PER_SCORE",
                "ScoringElements/ScoringElement/Code", List.of("scoring element code"),
                "ci_bureau_scoring_element.code", null, null, true, true, false, true, true);
        raw(p, "bureau.scoring_element.description", "Bureau scoring element description", BR, "TEXT", "PER_SCORE",
                "ScoringElements/ScoringElement/Description", List.of("scoring element"),
                "ci_bureau_scoring_element.description", null, null, true, true, false, true, true);

        // Tradeline identity & profile — Equifax Account + CiBureauTradeline
        raw(p, "bureau.tradeline.account_type", "Account type", BR, "CODE", "PER_TRADELINE",
                "Account/AccountType|AccountTypeCode", List.of("account type", "product type"),
                "EquifaxBureauAccountExtractor → CiBureauTradeline.accountTypeRaw", null, null,
                true, true, false, true, true);
        raw(p, "bureau.tradeline.ownership", "Ownership type", BR, "CODE", "PER_TRADELINE",
                "Account/OwnershipType", List.of("ownership"),
                "CiBureauTradeline.ownershipType", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.lender", "Lender / member name", BR, "TEXT", "PER_TRADELINE",
                "Account/MemberName|Institution", List.of("lender", "member name"),
                "CiBureauTradeline.lenderName", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.account_open_date", "Account open date", BR, "DATE", "PER_TRADELINE",
                "Account/DateOpened", List.of("account open date", "tradeline open date"),
                "CiBureauTradeline.openedDate", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.account_close_date", "Account close date", BR, "DATE", "PER_TRADELINE",
                "Account/DateClosed", List.of("close date"),
                "CiBureauTradeline.closedDate", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.date_reported", "Date reported", BR, "DATE", "PER_TRADELINE",
                "Account/DateReported|ReportedDate", List.of("last reported"),
                "CiBureauTradeline.lastReportedDate", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.sanction_amount", "Sanction / high credit", BR, "INR", "PER_TRADELINE",
                "Account/SanctionAmount|HighCredit", List.of("sanction amount", "high credit"),
                "CiBureauTradeline.sanctionedAmount / highCredit", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.current_balance", "Current balance", BR, "INR", "PER_TRADELINE",
                "Account/Balance", List.of("current balance", "outstanding"),
                "CiBureauTradeline.currentBalance", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.overdue_amount", "Overdue / past due amount", BR, "INR", "PER_TRADELINE",
                "Account/PastDueAmount", List.of("past due", "overdue amount"),
                "CiBureauTradeline.overdueAmount", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.credit_limit", "Credit limit", BR, "INR", "PER_TRADELINE",
                "Account/HighCredit (revolving when present)", List.of("credit limit", "card limit"),
                "CiBureauTradeline.highCredit", null, null, true, true, false, true, false);
        raw(p, "bureau.tradeline.emi", "EMI / installment amount", BR, "INR", "PER_TRADELINE",
                "Account/InstallmentAmount|EMI", List.of("emi", "installment"),
                "CiBureauTradeline.emiAmount", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.tenure_months", "Repayment tenure (months)", BR, "MONTHS", "PER_TRADELINE",
                "Account/RepaymentTenure", List.of("tenure"),
                "CiBureauTradeline.tenureMonths", null, null, true, true, false, true, false);
        raw(p, "bureau.tradeline.interest_rate", "Interest rate", BR, "PERCENT", "PER_TRADELINE",
                "Account/InterestRate", List.of("interest rate"),
                "CiBureauTradeline.interestRate", null, null, true, true, false, true, false);
        raw(p, "bureau.tradeline.account_status", "Account status", BR, "CODE", "PER_TRADELINE",
                "Account/AccountStatus|Open", List.of("account status", "open flag"),
                "CiBureauTradeline.accountStatus", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.write_off_amount", "Write-off amount", BR, "INR", "PER_TRADELINE",
                "Account/WrittenOffAmount",
                List.of("write off", "write-off", "write offs", "write-offs", "written off", "written-off"),
                "CiBureauTradeline.writtenOffAmount", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.settlement_amount", "Settlement amount", BR, "INR", "PER_TRADELINE",
                "Account/SettlementAmount", List.of("settlement"),
                "CiBureauTradeline.settlementAmount", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.suit_filed", "Suit filed indicator", BR, "FLAG", "PER_TRADELINE",
                "Account/SuitFiledStatus", List.of("suit filed"),
                "CiBureauTradeline.suitFiled", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.wilful_default", "Wilful default indicator", BR, "FLAG", "PER_TRADELINE",
                "Normalized from status / provider flags when present", List.of("wilful default"),
                "CiBureauTradeline.wilfulDefault", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.asset_classification", "Asset classification", BR, "CODE", "PER_TRADELINE",
                "Account/AssetClassification", List.of("asset classification", "sma"),
                "CiBureauTradeline.assetClassification", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.collateral_type", "Collateral / security type", BR, "CODE", "PER_TRADELINE",
                "Account/CollateralType", List.of("collateral", "security"),
                "CiBureauTradeline.collateralType", null, null, true, true, false, true, false);
        raw(p, "bureau.tradeline.collateral_value", "Collateral value", BR, "INR", "PER_TRADELINE",
                "Account/CollateralValue", List.of("collateral value"),
                "CiBureauTradeline.collateralValue", null, null, true, true, false, true, false);
        raw(p, "bureau.tradeline.payment_history", "Payment history", BR, "HISTORY", "PER_TRADELINE_PER_MONTH",
                "Account/History48Months/Month (PaymentStatus, DaysPastDue) | PaymentHistory",
                List.of("payment history", "tradeline history", "history48"),
                "CiBureauPaymentHistory / Equifax History48Months", null, null,
                true, true, false, true, true);
        raw(p, "bureau.tradeline.payment_status_month", "Payment status (month)", BR, "CODE", "PER_MONTH",
                "History48Months/Month/PaymentStatus", List.of("payment status"),
                "CiBureauPaymentHistory", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.dpd_month", "Days past due (month)", BR, "DAYS", "PER_MONTH",
                "History48Months/Month/DaysPastDue", List.of("monthly dpd", "days past due month"),
                "CiBureauPaymentHistory.dpd", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.secured_flag", "Secured / unsecured flag", BR, "FLAG", "PER_TRADELINE",
                "Derived from taxonomy mapping of AccountType", List.of("secured", "unsecured"),
                "CiBureauTradeline.secured (taxonomy + collateral evidence override)", null, null,
                true, true, false, true, true);

        raw(p, "bureau.tradeline.last_payment_amount", "Last payment amount", BR, "INR", "PER_TRADELINE",
                "Account/LastPayment", List.of("last payment"),
                "CiBureauTradeline.lastPaymentAmount", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.last_payment_date", "Last payment date", BR, "DATE", "PER_TRADELINE",
                "Account/LastPaymentDate", List.of("last payment date"),
                "CiBureauTradeline.lastPaymentDate", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.term_frequency", "Term frequency", BR, "CODE", "PER_TRADELINE",
                "Account/TermFrequency", List.of("term frequency"),
                "CiBureauTradeline.termFrequency", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.dispute_code", "Dispute code", BR, "CODE", "PER_TRADELINE",
                "Account/DisputeCode", List.of("dispute code"),
                "CiBureauTradeline.disputeCode", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.closure_reason", "Account closure reason", BR, "TEXT", "PER_TRADELINE",
                "Account/Reason", List.of("closure reason"),
                "CiBureauTradeline.closureReason", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.suit_filed_month", "Suit filed status (month)", BR, "CODE", "PER_MONTH",
                "History48Months/Month/SuitFiledStatus", List.of("monthly suit filed"),
                "CiBureauPaymentHistory.suitFiledStatus", null, null, true, true, false, true, true);
        raw(p, "bureau.tradeline.asset_classification_month", "Asset classification (month)", BR, "CODE", "PER_MONTH",
                "History48Months/Month/AssetClassificationStatus", List.of("monthly sma"),
                "CiBureauPaymentHistory.assetClassificationStatus", null, null, true, true, false, true, true);

        // Equifax native AccountSummary / OtherKeyInd / EnquirySummary / RecentActivities
        raw(p, "bureau.summary.account_count", "Provider account count", BR, "COUNT", "SCALAR",
                "AccountSummary/NoOfAccounts", List.of("no of accounts"),
                "ci_bureau_report_summary.account_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.active_account_count", "Provider active account count", BR, "COUNT", "SCALAR",
                "AccountSummary/NoOfActiveAccounts", List.of("active accounts"),
                "ci_bureau_report_summary.active_account_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.writeoff_count", "Provider write-off count", BR, "COUNT", "SCALAR",
                "AccountSummary/NoOfWriteOffs", List.of("writeoff count"),
                "ci_bureau_report_summary.writeoff_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_past_due", "Provider total past due", BR, "INR", "SCALAR",
                "AccountSummary/TotalPastDue", List.of("total past due"),
                "ci_bureau_report_summary.total_past_due", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.most_severe_status_24m", "Most severe status within 24 months", BR, "CODE", "SCALAR",
                "AccountSummary/MostSevereStatusWithIn24Months", List.of("most severe status"),
                "ci_bureau_report_summary.most_severe_status_24m", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_balance", "Provider total balance", BR, "INR", "SCALAR",
                "AccountSummary/TotalBalanceAmount", List.of("total balance"),
                "ci_bureau_report_summary.total_balance", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_sanction", "Provider total sanction", BR, "INR", "SCALAR",
                "AccountSummary/TotalSanctionAmount", List.of("total sanction"),
                "ci_bureau_report_summary.total_sanction", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_credit_limit", "Provider total credit limit", BR, "INR", "SCALAR",
                "AccountSummary/TotalCreditLimit", List.of("total credit limit"),
                "ci_bureau_report_summary.total_credit_limit", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_monthly_payment", "Provider total monthly payment", BR, "INR", "SCALAR",
                "AccountSummary/TotalMonthlyPaymentAmount", List.of("total monthly payment"),
                "ci_bureau_report_summary.total_monthly_payment", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.highest_sanction", "Provider highest sanction", BR, "INR", "SCALAR",
                "AccountSummary/SingleHighestSanctionAmount", List.of("highest sanction"),
                "ci_bureau_report_summary.highest_sanction", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.highest_balance", "Provider highest balance", BR, "INR", "SCALAR",
                "AccountSummary/SingleHighestBalance", List.of("highest balance"),
                "ci_bureau_report_summary.highest_balance", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.average_open_balance", "Provider average open balance", BR, "INR", "SCALAR",
                "AccountSummary/AverageOpenBalance", List.of("average open balance"),
                "ci_bureau_report_summary.average_open_balance", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.age_of_oldest_trade_months", "Provider age of oldest trade (months)", BR, "MONTHS", "SCALAR",
                "OtherKeyInd/AgeOfOldestTrade", List.of("age of oldest trade"),
                "ci_bureau_report_summary.age_of_oldest_trade_months (RAW Equifax; not bureau.oldest_tradeline_vintage_months)",
                null, null, true, true, false, true, true);
        raw(p, "bureau.summary.open_trade_count", "Provider open trade count", BR, "COUNT", "SCALAR",
                "OtherKeyInd/NumberOfOpenTrades", List.of("open trades"),
                "ci_bureau_report_summary.open_trade_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.past_due_account_count", "Provider past-due account count", BR, "COUNT", "SCALAR",
                "AccountSummary/NoOfPastDueAccounts", List.of("past due accounts"),
                "ci_bureau_report_summary.past_due_account_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.zero_balance_account_count", "Provider zero-balance account count", BR, "COUNT", "SCALAR",
                "AccountSummary/NoOfZeroBalanceAccounts", List.of("zero balance accounts"),
                "ci_bureau_report_summary.zero_balance_account_count", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.highest_credit", "Provider single highest credit", BR, "INR", "SCALAR",
                "AccountSummary/SingleHighestCredit", List.of("single highest credit"),
                "ci_bureau_report_summary.highest_credit", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.total_high_credit", "Provider total high credit", BR, "INR", "SCALAR",
                "AccountSummary/TotalHighCredit", List.of("total high credit"),
                "ci_bureau_report_summary.total_high_credit", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.recent_account_narrative", "Provider recent account narrative", BR, "TEXT", "SCALAR",
                "AccountSummary/RecentAccount", List.of("recent account"),
                "ci_bureau_report_summary.recent_account_narrative (RAW narrative; not a derived open-date calc)",
                null, null, true, true, false, true, true);
        raw(p, "bureau.summary.oldest_account_narrative", "Provider oldest account narrative", BR, "TEXT", "SCALAR",
                "AccountSummary/OldestAccount", List.of("oldest account"),
                "ci_bureau_report_summary.oldest_account_narrative (RAW narrative; distinct from AgeOfOldestTrade and derived vintage)",
                null, null, true, true, false, true, true);
        raw(p, "bureau.summary.all_lines_ever_written", "Provider all lines ever written off", BR, "INR", "SCALAR",
                "OtherKeyInd/AllLinesEVERWritten", List.of("all lines ever written"),
                "ci_bureau_report_summary.all_lines_ever_written", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.all_lines_ever_written_9m", "Provider all lines written off (9 months)", BR, "INR", "SCALAR",
                "OtherKeyInd/AllLinesEVERWrittenIn9Months", List.of("all lines written 9 months"),
                "ci_bureau_report_summary.all_lines_ever_written_9m", null, null, true, true, false, true, true);
        raw(p, "bureau.summary.all_lines_ever_written_6m", "Provider all lines written off (6 months)", BR, "INR", "SCALAR",
                "OtherKeyInd/AllLinesEVERWrittenIn6Months", List.of("all lines written 6 months"),
                "ci_bureau_report_summary.all_lines_ever_written_6m", null, null, true, true, false, true, true);

        raw(p, "bureau.enquiry.summary.total", "Provider enquiry total", BR, "COUNT", "SCALAR",
                "EnquirySummary/Total", List.of("enquiry total"),
                "ci_bureau_report_summary.enquiry_total", null, null, true, true, false, true, true);
        raw(p, "bureau.enquiry.summary.past_30d", "Provider enquiries past 30 days", BR, "COUNT", "SCALAR",
                "EnquirySummary/Past30Days", List.of("enquiries 30 days"),
                "ci_bureau_report_summary.enquiry_past_30d", null, null, true, true, false, true, true);
        raw(p, "bureau.enquiry.summary.past_12m", "Provider enquiries past 12 months", BR, "COUNT", "SCALAR",
                "EnquirySummary/Past12Months", List.of("enquiries 12 months"),
                "ci_bureau_report_summary.enquiry_past_12m", null, null, true, true, false, true, true);
        raw(p, "bureau.enquiry.summary.past_24m", "Provider enquiries past 24 months", BR, "COUNT", "SCALAR",
                "EnquirySummary/Past24Months", List.of("enquiries 24 months"),
                "ci_bureau_report_summary.enquiry_past_24m", null, null, true, true, false, true, true);
        raw(p, "bureau.enquiry.summary.recent_date", "Provider most recent enquiry date", BR, "DATE", "SCALAR",
                "EnquirySummary/Recent", List.of("recent enquiry date"),
                "ci_bureau_report_summary.enquiry_recent_date", null, null, true, true, false, true, true);
        raw(p, "bureau.enquiry.summary.purpose", "Provider enquiry summary purpose filter", BR, "CODE", "SCALAR",
                "EnquirySummary/Purpose", List.of("enquiry summary purpose"),
                "ci_bureau_report_summary.enquiry_summary_purpose (summary filter; distinct from per-enquiry bureau.inquiry.purpose)",
                null, null, true, true, false, true, true);

        raw(p, "bureau.recent.accounts_opened_90d", "Accounts opened (Equifax recent activity)", BR, "COUNT", "SCALAR",
                "RecentActivities/AccountsOpened", List.of("accounts opened 90d"),
                "ci_bureau_report_summary.recent_accounts_opened_90d", null, null, true, true, false, true, true);
        raw(p, "bureau.recent.accounts_updated_90d", "Accounts updated (Equifax recent activity)", BR, "COUNT", "SCALAR",
                "RecentActivities/AccountsUpdated", List.of("accounts updated 90d"),
                "ci_bureau_report_summary.recent_accounts_updated_90d", null, null, true, true, false, true, true);
        raw(p, "bureau.recent.accounts_delinquent_90d", "Accounts delinquent (Equifax recent activity)", BR, "COUNT", "SCALAR",
                "RecentActivities/AccountsDeliquent", List.of("accounts delinquent 90d"),
                "ci_bureau_report_summary.recent_accounts_delinquent_90d", null, null, true, true, false, true, true);
        raw(p, "bureau.recent.inquiries_90d", "Inquiries in Equifax recent activity window", BR, "COUNT", "SCALAR",
                "RecentActivities/TotalInquiries", List.of("recent inquiries"),
                "ci_bureau_report_summary.recent_inquiries_90d (RAW Equifax; not bureau.recent_inquiries_90d DERIVED)",
                null, null, true, true, false, true, true);

        // Enquiries
        raw(p, "bureau.inquiry", "Bureau enquiry event", BR, "EVENT", "PER_ENQUIRY",
                "Enquiries|Enquiry|Inquiry", List.of("enquiry event", "inquiry event"),
                "EquifaxBureauAccountExtractor → CiBureauInquiry", null, null, true, true, false, true, true);
        raw(p, "bureau.inquiry.date", "Enquiry date", BR, "DATE", "PER_ENQUIRY",
                "Enquiries/Date", List.of("enquiry date"),
                "CiBureauInquiry.inquiryDate", null, null, true, true, false, true, true);
        raw(p, "bureau.inquiry.purpose", "Enquiry purpose", BR, "CODE", "PER_ENQUIRY",
                "Enquiries/RequestPurpose|Purpose", List.of("enquiry purpose"),
                "CiBureauInquiry.purpose", null, null, true, true, false, true, true);
        raw(p, "bureau.inquiry.amount", "Enquiry amount", BR, "INR", "PER_ENQUIRY",
                "Enquiries/Amount", List.of("enquiry amount"),
                "CiBureauInquiry.amount", null, null, true, true, false, true, true);
        raw(p, "bureau.inquiry.member", "Enquiring member", BR, "TEXT", "PER_ENQUIRY",
                "Enquiries/Institution|MemberName", List.of("enquiring member"),
                "CiBureauInquiry.memberName", null, null, true, true, false, true, true);
        raw(p, "bureau.inquiry.time", "Enquiry time", BR, "TEXT", "PER_ENQUIRY",
                "Enquiries/Time", List.of("enquiry time"),
                "CiBureauInquiry.inquiryTime", null, null, true, true, false, true, true);
    }

    /** Bureau Retail derived inventory size (honesty + calculation-definition closure). */
    public static final int BUREAU_RETAIL_DERIVED_TARGET_COUNT = 37;

    private static void bureauRetailDerived(List<CanonicalParameterDefinition> p) {
        String maxDpd6mHow = """
                Highest days-past-due across tradeline monthly DPD in a trailing 6 calendar-month window.
                Raw inputs: bureau.tradeline.dpd_month (Equifax History48Months → YearMonth).
                Period representation: YearMonth (provider month key stored as first-of-month LocalDate).
                asOf: bureau report / evaluation date; as-of month is included.
                Window: inclusive [YearMonth(asOf) − 5, YearMonth(asOf)] (six months).
                Future periods after as-of month are excluded. Exact lower-bound month is included.
                Filters: no product-type exclusion (CC/closed/settled included when history exists).
                Null/malformed periods skipped; null DPD skipped (not zero). Aggregation: MAX.
                Missing data: no DPD month rows → DATA_INSUFFICIENT (not zero).
                Calculator: BureauMetricService.evaluateMaxDpd (shared studio + live).
                """.trim().replace('\n', ' ');

        derived(p, "bureau.max_dpd_6m", "Maximum DPD (6 months)", BR, "DAYS", "TRAILING_6M",
                maxDpd6mHow,
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService.evaluateMaxDpd",
                List.of("max dpd", "days past due", "dpd last 6 months", "maximum dpd 6 months", "maximum dpd"),
                null, null,
                cap("BUREAU_RETAIL", true, true, true, true, false, "SCALAR",
                        "History48Months/Month/DaysPastDue",
                        "DATA_INSUFFICIENT when no monthly DPD on any tradeline",
                        "Tradelines with monthly DPD; YearMonth trailing window shared with live",
                        "Month DPD numeric value as normalized YearMonth period",
                        "MAX"),
                true, true, true, true, false);

        bureauBuiltIn(p, "bureau.max_dpd_12m", "Maximum DPD (12 months)", "DAYS", "TRAILING_12M",
                "Highest DPD across monthly DPD in trailing 12 months. Aggregation: MAX. Calculator: BureauMetricService.evaluateMaxDpd.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService.evaluateMaxDpd",
                List.of("max dpd 12m", "dpd 12 months"));
        bureauBuiltIn(p, "bureau.max_dpd_24m", "Maximum DPD (24 months)", "DAYS", "TRAILING_24M",
                "Highest DPD across monthly DPD in trailing 24 months. Aggregation: MAX. Calculator: BureauMetricService.evaluateMaxDpd.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService.evaluateMaxDpd",
                List.of("max dpd 24m"));

        bureauBuiltIn(p, "bureau.inquiries.current_month", "Bureau enquiries (current month)", "COUNT", "CURRENT_MONTH",
                "Count of bureau enquiries whose date falls in the current evaluation month",
                List.of("bureau.inquiry.date"),
                "BureauMetricService.evaluateInquiriesCurrentMonth",
                List.of("enquiries", "inquiries", "bureau enquiry", "current month enquiries"));
        bureauBuiltIn(p, "bureau.inquiries.last_3m", "Bureau enquiries (last 3 calendar months)", "COUNT", "TRAILING_3M",
                "Count of bureau enquiries in the trailing 3 calendar months (distinct from current month and from 90 days)",
                List.of("bureau.inquiry.date"),
                "BureauMetricService.evaluateInquiriesLast3Months",
                List.of("enquiries last 3 months", "inquiries 3 months"));
        bureauBuiltIn(p, "bureau.recent_inquiries_90d", "Bureau enquiries (90 days)", "COUNT", "TRAILING_90D",
                "Count of individual enquiries in trailing 90 days; summary-only reports may be data-insufficient",
                List.of("bureau.inquiry.date"),
                "BureauMetricService.computeInquiries90d",
                List.of("enquiries 90d", "inquiries 90 days", "last 90 days"));

        derived(p, "bureau.live_unsecured_loan_count", "Live unsecured loans", BR, "COUNT", "PIT",
                "Count of LIVE tradelines that are secured=false, category≠UNKNOWN, non-duplicate (BUREAU_LIVE_ACCOUNT_DEFINITION_V1)",
                List.of("bureau.tradeline.account_status", "bureau.tradeline.secured_flag", "bureau.tradeline.current_balance"),
                "BureauMetricService.computeLiveUnsecured",
                List.of("live unsecured", "unsecured loan count"),
                "LIVE_UNSECURED_LOAN_COUNT", null,
                studioImpl(), true, true, true, true, false);

        bureauBuiltIn(p, "bureau.total_live_exposure", "Total live exposure", "INR", "PIT",
                "Sum of current balance on LIVE non-duplicate tradelines",
                List.of("bureau.tradeline.current_balance", "bureau.tradeline.account_status"),
                "BureauMetricService",
                List.of("live exposure", "total exposure"));
        bureauBuiltIn(p, "bureau.secured_live_exposure", "Secured live exposure", "INR", "PIT",
                "Sum of balances on LIVE secured tradelines",
                List.of("bureau.tradeline.current_balance", "bureau.tradeline.secured_flag", "bureau.tradeline.account_status"),
                "BureauMetricService", List.of("secured exposure"));
        bureauBuiltIn(p, "bureau.unsecured_live_exposure", "Unsecured live exposure", "INR", "PIT",
                "Sum of balances on LIVE unsecured tradelines",
                List.of("bureau.tradeline.current_balance", "bureau.tradeline.secured_flag", "bureau.tradeline.account_status"),
                "BureauMetricService", List.of("unsecured exposure"));
        bureauBuiltIn(p, "bureau.total_monthly_obligation", "Total monthly obligation (bureau EMI)", "INR", "PIT",
                "Sum of provider EMI on qualifying tradelines; never invents EMI %; PARTIAL if some missing",
                List.of("bureau.tradeline.emi"),
                "BureauMetricService", List.of("monthly obligation", "bureau emi total"));
        bureauBuiltIn(p, "bureau.settled_account_count", "Settled account count", "COUNT", "PIT",
                "Count of tradelines with settled status",
                List.of("bureau.tradeline.account_status"),
                "BureauMetricService", List.of("settled accounts"));
        bureauBuiltIn(p, "bureau.written_off_account_count", "Written-off account count", "COUNT", "PIT",
                "Count of tradelines with written-off status",
                List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"),
                "BureauMetricService",
                List.of("write off count", "write-off count", "written off accounts", "written-off accounts",
                        "loan write offs", "loan write-offs"));

        derived(p, "bureau.accounts.writeoff_non_cc", "Non-credit-card write-off count", BR, "COUNT", "PIT",
                "Count of written-off tradelines excluding credit cards (BureauMetricService.computeWriteoffCounts)",
                List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"),
                "BureauMetricService.computeWriteoffCounts",
                List.of("non cc write off", "write-off except credit card", "loan write-offs except credit cards"),
                "WRITEOFF_NON_CC", null,
                studioImpl(), true, true, true, true, false);
        bureauBuiltIn(p, "bureau.accounts.cc_writeoff", "Credit-card write-off count", "COUNT", "PIT",
                "Count of written-off credit-card tradelines (BureauMetricService.computeWriteoffCounts)",
                List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"),
                "BureauMetricService.computeWriteoffCounts",
                List.of("credit card write off", "cc write-off"));

        bureauBuiltIn(p, "bureau.cc_overdue_amount", "Credit-card overdue amount", "INR", "PIT",
                "MAX of credit-card overdue amounts (not SUM). Null overdue treated as zero. Duplicates excluded.",
                List.of("bureau.tradeline.overdue_amount"),
                "BureauMetricService.evaluateCcOverdueAmount",
                List.of("credit card overdue", "cc overdue"));
        bureauBuiltIn(p, "bureau.overdue.age_months", "Overdue age (months)", "MONTHS", "PIT",
                "MAX age in months of non-CC overdue using overdue EVENT month from monthly DPD history",
                List.of("bureau.tradeline.overdue_amount", "bureau.tradeline.dpd_month"),
                "BureauMetricService",
                List.of("overdue age", "overdue older than"));
        bureauBuiltIn(p, "bureau.overdue.amount", "Overdue amount", "INR", "PIT",
                "SUM of non-CC overdue amounts where overdue > 0 (explanatory; not BRE exception)",
                List.of("bureau.tradeline.overdue_amount"),
                "BureauMetricService",
                List.of("overdue amount", "overdue below"));
        bureauBuiltIn(p, "bureau.credit_after_overdue.exists", "New credit after overdue", "BOOLEAN", "PIT",
                "Whether a new credit facility was opened after the global overdue event",
                List.of("bureau.tradeline.account_open_date", "bureau.tradeline.overdue_amount"),
                "BureauMetricService",
                List.of("new credit after overdue", "credit after overdue"));

        derived(p, "bureau.credit_after_overdue.clean_history_months", "Clean history months (post-overdue)",
                BR, "MONTHS", "PIT",
                "Max consecutive CLEAN months on loans opened after the overdue event — explanatory helper, not account-safe for BRE. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.dpd_month", "bureau.tradeline.account_open_date", "bureau.tradeline.overdue_amount"),
                "BureauMetricService",
                List.of("clean history", "clean credit history", "clean string", "6 months clean"),
                null, "REPAYMENT_HISTORY",
                studioImpl(), true, true, true, true, false);

        bureauBuiltIn(p, "bureau.dpd_30_plus_count_6m", "Count of 30+ DPD months (6m)", "COUNT", "TRAILING_6M",
                "Count distinct account-months with DPD≥30 in trailing 6 months. Null DPD skipped. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService", List.of("30 plus dpd", "30+ dpd"));
        bureauBuiltIn(p, "bureau.dpd_60_plus_count_6m", "Count of 60+ DPD months (6m)", "COUNT", "TRAILING_6M",
                "Count distinct account-months with DPD≥60 in trailing 6 months. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService", List.of("60 plus dpd"));
        bureauBuiltIn(p, "bureau.dpd_90_plus_count_6m", "Count of 90+ DPD months (6m)", "COUNT", "TRAILING_6M",
                "Count distinct account-months with DPD≥90 in trailing 6 months. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService", List.of("90 plus dpd"));
        bureauBuiltIn(p, "bureau.months_since_last_delinquency", "Months since last delinquency", "MONTHS", "PIT",
                "Months since most recent month with DPD>0. Calculator: BureauMetricService.evaluateMonthsSinceLastDelinquency.",
                List.of("bureau.tradeline.dpd_month"),
                "BureauMetricService.evaluateMonthsSinceLastDelinquency",
                List.of("months since delinquency"));
        bureauBuiltIn(p, "bureau.oldest_tradeline_vintage_months", "Oldest tradeline vintage (months)", "MONTHS", "PIT",
                "Months since earliest account open date. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.account_open_date"),
                "BureauMetricService", List.of("oldest account", "vintage"));
        bureauBuiltIn(p, "bureau.average_account_age_months", "Average account age (months)", "MONTHS", "PIT",
                "Mean age of tradelines from open date. Calculator: BureauMetricService.",
                List.of("bureau.tradeline.account_open_date"),
                "BureauMetricService", List.of("average account age"));
        bureauBuiltIn(p, "bureau.cc_utilisation", "Credit-card utilisation", "RATIO", "PIT",
                "SUM(current balance) / SUM(credit limit) on open LIVE credit cards. DATA_INSUFFICIENT if any eligible CC is missing limit or SUM(limit)=0.",
                List.of("bureau.tradeline.current_balance", "bureau.tradeline.credit_limit", "bureau.tradeline.account_status"),
                "BureauMetricService.evaluateCcUtilisation",
                List.of("card utilisation", "cc utilization"));

        derived(p, "bureau.thin_file_indicator", "Thin-file / NTC indicator", BR, "BOOLEAN", "PIT",
                "NTC / thin-file indicator — BUSINESS_DEFINITION_REQUIRED (distinct from bureau.status_ntc). [DEFINED_NOT_IMPLEMENTED]",
                List.of("bureau.score"),
                "DEFINED_NOT_IMPLEMENTED",
                List.of("ntc", "thin file"),
                null, null,
                Capability.of("BUREAU_RETAIL", true, true, true, false, false, "SCALAR", null,
                        "BUSINESS_DEFINITION_REQUIRED — do not treat as live; use bureau.status_ntc for implemented NTC",
                        null, null, null),
                true, true, true, false, false);

        derived(p, "bureau.status_ntc", "Bureau NTC / thin-file status", BR, "BOOLEAN", "PIT",
                "True when bureau report is NTC / thin-file (BureauMetricService.computeStatusNtc / evaluateStatusNtc); "
                        + "Live scorecard maps NTC_FLAG (do not invent false when missing; do not infer NTC from -1 alone)",
                List.of("bureau.score"),
                "BureauMetricService.computeStatusNtc",
                List.of("ntc", "status ntc", "thin file", "new to credit"),
                null, "NTC_FLAG",
                studioImpl(), true, true, true, true, false);

        bureauBuiltIn(p, "bureau.non_cc_overdue_exception_violation_count",
                "Non-CC overdue exception violation count", "COUNT", "PIT",
                "Count of non-CC overdue accounts that fail the 1∧2∧3∧4 exception (age>12m, later loan, 6 clean months, overdue<1500). Calculator: BureauMetricService.",
                List.of("bureau.tradeline.overdue_amount", "bureau.tradeline.account_open_date", "bureau.tradeline.dpd_month"),
                "BureauMetricService",
                List.of("non cc overdue exception", "overdue exception violation"));
        bureauBuiltIn(p, "bureau.suit_filed_account_count", "Suit-filed account count", "COUNT", "PIT",
                "Count of non-duplicate tradelines with suit-filed flag or affirmative suit status in monthly history. Star/blank is not affirmative.",
                List.of("bureau.tradeline.suit_filed"),
                "BureauMetricService",
                List.of("suit filed count", "suit filed accounts"));
        bureauBuiltIn(p, "bureau.pan_distinct_count", "Distinct PAN count", "COUNT", "PIT",
                "Distinct normalised PAN count from report extraction. Missing extraction → DATA_INSUFFICIENT. Empty list → 0. PAN strings are never persisted.",
                List.of(),
                "BureauMetricService.evaluatePanDistinctCount",
                List.of("pan count", "distinct pan"));

        bureauSourceNotProven(p, "bureau.restructured_account_count", "Restructured account count",
                "Count of tradelines with restructured status — SOURCE_NOT_PROVEN on Equifax sample vocabulary (Standard/STD/SPM only).",
                List.of("restructured accounts"));
        bureauSourceNotProven(p, "bureau.account_sold_count", "Account-sold count",
                "Count of tradelines with account-sold status — SOURCE_NOT_PROVEN on Equifax sample vocabulary.",
                List.of("account sold", "sold accounts"));
        bureauSourceNotProven(p, "bureau.dbt_account_count", "DBT account count",
                "Count of tradelines with DBT status — SOURCE_NOT_PROVEN on Equifax sample vocabulary.",
                List.of("dbt accounts"));
        bureauSourceNotProven(p, "bureau.pwos_account_count", "PWOS account count",
                "Count of tradelines with PWOS status — SOURCE_NOT_PROVEN on Equifax sample vocabulary.",
                List.of("pwos accounts"));
        bureauSourceNotProven(p, "bureau.lss_account_count", "LSS account count",
                "Count of tradelines with LSS status — SOURCE_NOT_PROVEN on Equifax sample vocabulary.",
                List.of("lss accounts"));
    }

    private static void bureauCommercialRaw(List<CanonicalParameterDefinition> p) {
        // SurePass commercial fixture CommercialBureauResponseDetails — separate schema
        raw(p, "bureau.commercial.business_name", "Commercial business name", BC, "TEXT", "SCALAR",
                "CommercialPersonalInfo/BusinessName", List.of("commercial business name"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.legal_constitution", "Business legal constitution", BC, "CODE", "SCALAR",
                "CommercialPersonalInfo/BusinessLegalConstitution", List.of("legal constitution"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.business_category", "Business category", BC, "CODE", "SCALAR",
                "CommercialPersonalInfo/BusinessCategory", List.of("business category"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.industry_type", "Industry type", BC, "CODE", "SCALAR",
                "CommercialPersonalInfo/IndustryType", List.of("industry type"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.date_of_incorporation", "Date of incorporation", BC, "DATE", "SCALAR",
                "CommercialPersonalInfo/DateOfIncorporation", List.of("incorporation date"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.sales_figure", "Reported sales figure", BC, "INR", "SCALAR",
                "CommercialPersonalInfo/SalesFigure", List.of("sales figure"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.score", "Commercial bureau score", BC, "SCORE", "SCALAR",
                "credit_score[].credit_score (e.g. ERS)", List.of("commercial score", "ers score"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.facility.account_number", "Facility account reference", BC, "TEXT", "PER_FACILITY",
                "CreditFacilityDetails/AccountNumber", List.of("facility account"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.facility.type", "Facility / account type", BC, "CODE", "PER_FACILITY",
                "CreditFacilityDetails/AccountType", List.of("facility type", "working capital"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.facility.sanction_amount", "Facility sanction amount", BC, "INR", "PER_FACILITY",
                "CreditFacilityDetails/SanctionedAmount", List.of("facility sanction"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.facility.current_balance", "Facility outstanding", BC, "INR", "PER_FACILITY",
                "CreditFacilityDetails/CurrentBalance", List.of("facility outstanding"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.facility.asset_classification", "Facility asset classification", BC, "CODE", "PER_FACILITY",
                "CreditFacilityDetails/AssetClassification", List.of("facility asset class"), null, null, null,
                true, true, false, false, false);
        raw(p, "bureau.commercial.relationship.name", "Related party name", BC, "TEXT", "PER_RELATIONSHIP",
                "RelationshipDetails/Name", List.of("director name"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.relationship.type", "Relationship type", BC, "CODE", "PER_RELATIONSHIP",
                "RelationshipDetails/Relationship", List.of("director", "guarantor relationship"), null, null, null,
                true, false, false, false, false);
        // Documented as not fully implemented in taxonomy — source-available from richer commercial reports when connected
        raw(p, "bureau.commercial.guarantee_exposure", "Guarantee exposure", BC, "INR", "SCALAR",
                "Guarantee / contingent sections when present in commercial report",
                List.of("guarantee exposure"), null, null, null,
                true, false, false, false, false);
        raw(p, "bureau.commercial.enquiry", "Commercial enquiry event", BC, "EVENT", "PER_ENQUIRY",
                "Commercial enquiry section when present", List.of("commercial enquiry"), null, null, null,
                true, false, false, false, false);
    }

    private static void bureauCommercialDerived(List<CanonicalParameterDefinition> p) {
        derivedDefined(p, "bureau.commercial.total_facility_exposure", "Total commercial facility exposure", BC,
                "Sum of facility outstanding across CreditFacilityDetails",
                List.of("bureau.commercial.facility.current_balance"), List.of("commercial exposure"));
        derivedDefined(p, "bureau.commercial.total_sanction", "Total commercial sanction", BC,
                "Sum of facility sanction amounts",
                List.of("bureau.commercial.facility.sanction_amount"), List.of("commercial sanction"));
        derivedDefined(p, "bureau.commercial.utilisation", "Commercial utilisation", BC,
                "Total outstanding ÷ total sanction when sanction > 0",
                List.of("bureau.commercial.facility.current_balance", "bureau.commercial.facility.sanction_amount"),
                List.of("commercial utilisation"));
    }

    private static void bankRaw(List<CanonicalParameterDefinition> p) {
        raw(p, "bank.transaction.date", "Transaction date", BS, "DATE", "PER_TRANSACTION",
                "BSA/AA transaction date", List.of("txn date", "transaction date"), null, null, null,
                true, true, false, true, true);
        raw(p, "bank.transaction.value_date", "Value date", BS, "DATE", "PER_TRANSACTION",
                "Value date when supplied by BSA/AA", List.of("value date"), null, null, null,
                true, true, false, false, false);
        raw(p, "bank.transaction.amount", "Transaction amount", BS, "INR", "PER_TRANSACTION",
                "BSA/AA amount", List.of("txn amount", "transaction amount"), null, null, null,
                true, true, false, true, true);
        raw(p, "bank.transaction.credit_debit", "Credit/debit indicator", BS, "FLAG", "PER_TRANSACTION",
                "Credit vs debit flag", List.of("credit debit", "dr cr"), null, null, null,
                true, true, false, true, true);
        raw(p, "bank.account.closing_balance", "Closing balance", BS, "INR", "PER_DAY",
                "End-of-day / closing balance", List.of("closing balance", "eod balance"), null, null, null,
                true, true, false, true, true);
        raw(p, "bank.transaction.narration", "Narration", BS, "TEXT", "PER_TRANSACTION",
                "Transaction narration / description", List.of("narration", "description"), null, null, null,
                true, true, false, true, true);
        raw(p, "bank.transaction.mode", "Payment mode", BS, "CODE", "PER_TRANSACTION",
                "UPI/NEFT/IMPS/RTGS/cheque when classified", List.of("payment mode", "upi", "neft"), null, null, null,
                true, true, false, true, false);
        raw(p, "bank.transaction.counterparty", "Counterparty", BS, "TEXT", "PER_TRANSACTION",
                "Counterparty / beneficiary when present", List.of("counterparty"), null, null, null,
                true, true, false, false, false);
        raw(p, "bank.transaction.classification", "Transaction classification", BS, "CODE", "PER_TRANSACTION",
                "BANK_TXN_CLASSIFIER_V1 category", List.of("txn classification", "transaction category"),
                "Bank transaction classifier", null, null, true, true, false, true, true);
        raw(p, "bank.account.od_limit", "OD / CC sanctioned limit", BS, "INR", "SCALAR",
                "sanctionedLimit / drawingPower / overdraftLimit when present",
                List.of("od limit", "overdraft limit"), null, null, null,
                true, true, false, true, false);
    }

    private static void bankDerived(List<CanonicalParameterDefinition> p) {
        derived(p, "banking.avg_daily_balance_3m", "Average daily balance", BS, "INR", "TRAILING_3M",
                "Eligible end-of-day balances → daily balance → trailing 3-month average (EOD carry-forward)",
                List.of("bank.account.closing_balance", "bank.transaction"),
                "BankingMetricService / BankAverageDailyBalanceCalculator",
                List.of("adb", "average daily balance", "avg daily balance"),
                null, "AVERAGE_BANK_BALANCE",
                prodBank(), true, true, true, true, true);
        derived(p, "banking.monthly_credits_3m", "Monthly credits", BS, "INR", "TRAILING_3M",
                "Sum of qualifying credit (inflow) transactions on the Bank Statement over the trailing 3 months",
                List.of("bank.transaction"),
                "PolicyBankingMetricService",
                List.of("monthly credits", "credit sum"), null, null,
                bankStudio(), true, true, true, true, false);
        derived(p, "banking.cheque_return_count_3m", "Cheque return count", BS, "COUNT", "TRAILING_3M",
                "Count of cheque/ECS returns in trailing 3 months",
                List.of("bank.transaction", "CHEQUE_RETURN"),
                "PolicyBankingMetricService",
                List.of("cheque return count", "bounce count"), null, null,
                bankStudio(), true, true, true, true, false);
        derived(p, "banking.emi_bounce_count_3m", "EMI bounce count", BS, "COUNT", "TRAILING_3M",
                "Count of EMI repayment events with matched return/bounce events in trailing 3 months "
                        + "(existing EMI + bounce/return classifiers; DATA_INSUFFICIENT when coverage missing)",
                List.of("bank.transaction", "EMI", "NACH_RETURN", "CHEQUE_RETURN"),
                "BankingMetricService.countEmiBounce / EmiBounceCountCalculator.V1",
                List.of("emi bounce", "emi bounce count", "bounced emi", "emi return count"), null, null,
                // Gate-3: runtime calculator + snapshot path banking.bounce.emi_count_3m — production-ready binding.
                // Product Config still requires AA/BSA workflow acquisition.
                prodBank(), true, true, true, true, true);
        derived(p, "banking.settlement.count_monthly_avg_3m", "Average monthly settlements", BS, "COUNT", "TRAILING_3M",
                "Number of qualifying QR settlement credits during the trailing 3 months ÷ 3",
                List.of("bank.transaction", "QR_SETTLEMENT"),
                "banking.qr_settlement.average_monthly_count_3m",
                List.of("monthly settlements", "settlement count", "number of settlements", "average monthly settlements"),
                null, null, bankStudio(), true, true, true, true, false);
        derived(p, "banking.settlement.avg_daily_3m", "Average daily settlements", BS, "INR", "TRAILING_3M",
                "Sum of qualifying QR settlement credit amounts during trailing 3 months ÷ 90",
                List.of("bank.transaction", "QR_SETTLEMENT"),
                "banking.qr_settlement.average_daily_3m",
                List.of("daily settlements", "average daily settlement"), null, null,
                bankStudio(), true, true, true, true, false);
        derived(p, "banking.transaction_count.average_monthly_3m", "Average monthly transactions", BS, "COUNT", "TRAILING_3M",
                "Total qualifying transactions in trailing 3 months ÷ 3",
                List.of("bank.transaction"),
                "PolicyBankingMetricService.transactionCount3m",
                List.of("monthly transactions", "average monthly transaction"), null, null,
                bankStudio(), true, true, true, true, false);
        derived(p, "banking.inward_return.ratio_3m", "Inward return ratio", BS, "PERCENT", "TRAILING_3M",
                "Inward cheque/ECS/ENACH returns as a percentage of transactions",
                List.of("bank.transaction", "CHEQUE_RETURN", "NACH_RETURN"),
                "PolicyBankingMetricService.inwardChequeReturn",
                List.of("inward return", "inward cheque return", "ecs return"),
                "CHEQUE_BOUNCES_3M", null, bankStudio(), true, true, true, true, false);
        derived(p, "banking.adjusted_business_credits_12m", "Adjusted business credits (12m)", BS, "INR", "TRAILING_12M",
                "Credits excluding loan disbursement, self-transfer, capital infusion, interest, refunds (ADJUSTED_BANKING_TURNOVER_V1)",
                List.of("bank.transaction.classification"),
                "BankingMetricService adjusted turnover",
                List.of("adjusted credits", "business credits"), null, null,
                prodBank(), true, true, true, true, true);
        derived(p, "banking.monthly_obligation", "Detected monthly EMI obligation", BS, "INR", "PIT",
                "Recurring EMI/NACH lender debits with min occurrences; no % estimates",
                List.of("bank.transaction"),
                "BANK_EMI_DETECTION_V1",
                List.of("bank emi", "monthly emi obligation"), null, null,
                prodBank(), true, true, true, true, true);
        derivedDefined(p, "banking.negative_balance_days_3m", "Negative balance days (3m)", BS,
                "Count of days with negative EOD balance in trailing 3m",
                List.of("bank.account.closing_balance"), List.of("negative balance days"));
        derivedDefined(p, "banking.cash_intensity_3m", "Cash intensity (3m)", BS,
                "Cash deposit/withdrawal share of turnover when classifier marks cash",
                List.of("bank.transaction.classification"), List.of("cash intensity"));
        derivedDefined(p, "banking.od_utilisation", "OD / CC utilisation (banking)", BS,
                "Utilisation only when OD/CC limit present; else data-insufficient",
                List.of("bank.account.od_limit", "bank.account.closing_balance"), List.of("od utilisation"));
    }

    private static void accountAggregator(List<CanonicalParameterDefinition> p) {
        raw(p, "aa.consent.status", "AA consent status", AA, "CODE", "SCALAR",
                "Account Aggregator consent lifecycle status", List.of("aa consent", "consent status"),
                "AccountAggregatorService", null, null, true, true, false, true, true);
        raw(p, "aa.consent.fi_types", "AA FI types", AA, "CODE", "LIST",
                "Requested/linked FI types on consent", List.of("fi types"), null, null, null,
                true, true, false, true, false);
        raw(p, "aa.account.metadata", "AA account metadata", AA, "OBJECT", "PER_ACCOUNT",
                "Linked account metadata from FIP via AA", List.of("aa account"), null, null, null,
                true, true, false, true, false);
        // AA is transport — banking facts come from FI; do not invent new AA facts
        raw(p, "aa.transport.note", "AA is consent/transport (not a fact source)", AA, "FLAG", "SCALAR",
                "Facts originate from underlying FIP (bank/GST/etc.), not AA itself",
                List.of("account aggregator"), null, null, null,
                true, true, false, true, true);
    }

    private static void gst(List<CanonicalParameterDefinition> p) {
        raw(p, "gst.registration.gstin", "GSTIN", GST, "TEXT", "SCALAR",
                "GST registration GSTIN", List.of("gstin"), "CiGstRegistration", null, null,
                true, true, false, true, true);
        raw(p, "gst.registration.legal_name", "GST legal name", GST, "TEXT", "SCALAR",
                "Legal name on GST registration", List.of("gst legal name"), null, null, null,
                true, true, false, true, true);
        raw(p, "gst.registration.status", "GST registration status", GST, "CODE", "SCALAR",
                "Registration status", List.of("gst status"), null, null, null,
                true, true, false, true, true);
        raw(p, "gst.return.filing_status", "GST return filing status", GST, "CODE", "PER_PERIOD",
                "GSTR period filing status", List.of("filing status"), "CiGstReturnPeriod", null, null,
                true, true, false, true, true);
        raw(p, "gst.return.taxable_value", "GST taxable value", GST, "INR", "PER_PERIOD",
                "Period taxable value from GSTR financials", List.of("taxable value"),
                "CiGstPeriodFinancials", null, null, true, true, false, true, true);
        raw(p, "gst.return.invoice_value", "GST invoice value", GST, "INR", "PER_PERIOD",
                "Invoice / turnover components when present", List.of("invoice value"), null, null, null,
                true, true, false, true, false);

        derived(p, "gst.turnover.trailing_12m", "GST turnover (12 months)", GST, "INR", "TRAILING_12M",
                "Trailing 12-month GST turnover from filed period financials",
                List.of("gst.return.taxable_value"),
                "GstMetricService.computeTrailingTurnover",
                List.of("gst turnover", "annual gst turnover"),
                "ANNUAL_GST_TURNOVER", "GST_INCOME",
                prodGst(), true, true, true, true, true);
        derived(p, "gst.turnover.trailing_3m", "GST turnover (3 months)", GST, "INR", "TRAILING_3M",
                "Trailing 3-month GST turnover",
                List.of("gst.return.taxable_value"),
                "GstMetricService", List.of("gst turnover 3m"), null, null,
                prodGst(), true, true, true, true, true);
        derived(p, "gst.turnover.trailing_6m", "GST turnover (6 months)", GST, "INR", "TRAILING_6M",
                "Trailing 6-month GST turnover",
                List.of("gst.return.taxable_value"),
                "GstMetricService", List.of("gst turnover 6m"), null, null,
                prodGst(), true, true, true, true, true);
        derived(p, "gst.filing.timeliness_score", "GST filing timeliness score", GST, "SCORE", "TRAILING_6M",
                "Filing timeliness score from return periods (GstMetricService.computeTimeliness uses trailing 6 months)",
                List.of("gst.return.filing_status"),
                "GstMetricService", List.of("filing timeliness"), null, null,
                prodGstPeriod("Runtime window = trailing 6 months (not 12m)"), true, true, true, true, true);
        derived(p, "gst.gstr1_gstr3b_turnover_variance", "GSTR-1 vs GSTR-3B turnover variance", GST, "PERCENT", "TRAILING",
                "Variance between GSTR-1 and GSTR-3B turnover",
                List.of("gst.return.taxable_value"),
                "GstMetricService", List.of("gstr variance"), null, null,
                prodGst(), true, true, true, true, true);
        derived(p, "gst.return.missing_count_12m", "Missing GST returns (12m)", GST, "COUNT", "TRAILING_12M",
                "Count of missing returns in trailing 12 months",
                List.of("gst.return.filing_status"),
                "GstMetricService", List.of("missing gst returns"), null, null,
                prodGst(), true, true, true, true, true);
        derived(p, "gst.return.late_count_12m", "Late GST returns (12m)", GST, "COUNT", "TRAILING_12M",
                "Count of late filings in trailing 12 months",
                List.of("gst.return.filing_status"),
                "GstMetricService", List.of("late gst returns"), null, null,
                prodGst(), true, true, true, true, true);
    }

    private static void itrFinancials(List<CanonicalParameterDefinition> p) {
        raw(p, "itr.income.total", "ITR total income", FS, "INR", "PER_YEAR",
                "SurePass/Karza ITR total income fields", List.of("itr income", "total income"),
                "TaxMetricService / ITR adapters", null, null, true, true, false, true, false);
        raw(p, "itr.taxable_income", "Taxable income", FS, "INR", "PER_YEAR",
                "ITR taxable income", List.of("taxable income"), null, null, null,
                true, true, false, true, false);
        raw(p, "financial.revenue", "Revenue / turnover (financials)", FS, "INR", "PER_PERIOD",
                "TIS / financial statement revenue when present", List.of("revenue", "turnover financial"),
                "SurePassTisAdapter", null, null, true, true, false, true, false);
        raw(p, "financial.pat", "Profit after tax", FS, "INR", "PER_PERIOD",
                "PAT from financial / TIS amounts", List.of("pat", "profit after tax"), null, null, null,
                true, true, false, true, false);
        raw(p, "financial.interest", "Interest expense", FS, "INR", "PER_PERIOD",
                "Interest from financials when present", List.of("interest expense"), null, null, null,
                true, true, false, false, false);
        raw(p, "financial.debt", "Total debt", FS, "INR", "PER_PERIOD",
                "Debt from financials when present", List.of("total debt"), null, null, null,
                true, true, false, false, false);
        raw(p, "financial.net_worth", "Net worth / TNW", FS, "INR", "PER_PERIOD",
                "Tangible net worth when present", List.of("net worth", "tnw"), null, null, null,
                true, true, false, false, false);

        derivedDefined(p, "financial.dscr", "DSCR", FS,
                "Debt service coverage — capability exists; confirm formula binding before production use",
                List.of("financial.pat", "financial.interest", "financial.debt"), List.of("dscr"));
        derivedDefined(p, "financial.interest_coverage", "Interest coverage", FS,
                "EBIT/Interest style coverage when inputs available",
                List.of("financial.interest"), List.of("interest coverage", "icr"));
        derivedDefined(p, "financial.debt_equity", "Debt / Equity", FS,
                "Debt ÷ equity/net worth when both present",
                List.of("financial.debt", "financial.net_worth"), List.of("debt equity", "d/e"));
        derivedDefined(p, "financial.tol_tnw", "TOL / TNW", FS,
                "Total outside liabilities ÷ tangible net worth",
                List.of("financial.debt", "financial.net_worth"), List.of("tol/tnw", "tol tnw"));
        derivedDefined(p, "financial.pat_positive", "PAT positive", FS,
                "Boolean: PAT > 0",
                List.of("financial.pat"), List.of("pat positive"));
    }

    private static void kyc(List<CanonicalParameterDefinition> p) {
        // Keep aggregate + expand KycFactCatalog paths
        derived(p, "kyc.quality", "KYC quality", KYC, "FLAG", "PIT",
                "Pass/fail from KYC outcome",
                List.of("kyc"),
                "CreditControlService KYC_QUALITY",
                List.of("kyc", "kyc pass", "kyc quality"),
                "KYC_PASS", "KYC_QUALITY",
                Capability.of("KYC", true, true, true, true, true, "SCALAR", null, null, null, null, null),
                true, true, true, true, true);

        kycFact(p, "kyc.pan.present", "PAN present", "BOOLEAN", true, true);
        kycFact(p, "kyc.pan.verified", "PAN verified", "BOOLEAN", true, true);
        // Bound to NormalizedKycFactBuilder (emits when provider/nameMatchHint present).
        // Not auto-certified Production Ready — optional/provider-conditional fact.
        raw(p, "kyc.pan.name_match", "PAN name match", KYC, "BOOLEAN", "SCALAR",
                "NormalizedKycFactBuilder / provider nameMatch hint",
                List.of("pan name match", "name match"),
                "NormalizedKycFactBuilder", null, null,
                true, true, false, true, false);
        kycFact(p, "kyc.pan.verification_status", "PAN verification status", "STRING", true, true);
        kycFact(p, "kyc.ckyc.available", "CKYC available", "BOOLEAN", true, true);
        kycFact(p, "kyc.ckyc.verified", "CKYC verified", "BOOLEAN", true, true);
        kycFact(p, "kyc.aadhaar.verified", "Aadhaar verified", "BOOLEAN", true, true);
        kycFact(p, "kyc.gstin.present", "GSTIN present", "BOOLEAN", true, true);
        kycFact(p, "kyc.gstin.verified", "GSTIN verified", "BOOLEAN", true, true);
        kycFact(p, "kyc.cin.present", "CIN present", "BOOLEAN", true, true);
        kycFact(p, "kyc.cin.verified", "CIN / MCA verified", "BOOLEAN", true, true);
        kycFact(p, "kyc.udyam.verified", "Udyam verified", "BOOLEAN", true, true);
        kycFact(p, "kyc.bank_account.verified", "Bank account verified (penny drop)", "BOOLEAN", true, true);
        kycFact(p, "kyc.vkyc.completed", "Video KYC completed", "BOOLEAN", true, true);
        kycFact(p, "kyc.vkyc.result", "VKYC result", "STRING", true, true);
        kycFact(p, "kyc.pkyc.completed", "Physical KYC completed", "BOOLEAN", true, true);
        kycFact(p, "kyc.overall.outcome", "KYC overall outcome", "STRING", true, true);
        kycFact(p, "kyc.overall.technical_status", "KYC technical status", "STRING", true, true);
        // Explicitly unsupported — sourceAvailable false
        raw(p, "kyc.pep.screened", "PEP screened", KYC, "BOOLEAN", "SCALAR",
                "Not supported in KycFactCatalog", List.of("pep"), null, null, null,
                false, false, false, false, false);
        raw(p, "kyc.sanctions.cleared", "Sanctions cleared", KYC, "BOOLEAN", "SCALAR",
                "Not supported in KycFactCatalog", List.of("sanctions", "aml"), null, null, null,
                false, false, false, false, false);
    }

    private static void applicationProduct(List<CanonicalParameterDefinition> p) {
        manual(p, "application.proposed_edi", "Proposed EDI", APP, "INR",
                List.of("edi", "proposed edi", "equated daily instalment"), null, null);
        manual(p, "application.requested_amount", "Requested loan amount", APP, "INR",
                List.of("loan amount", "requested amount"), "application.loan_amount", null);
        manual(p, "application.tenure_months", "Requested tenure (months)", APP, "MONTHS",
                List.of("tenure", "loan tenure"), null, null);
        manual(p, "application.business_vintage_months", "Business vintage", APP, "MONTHS",
                List.of("vintage", "business vintage", "years in business"), null, null);
        manual(p, "application.borrower_type", "Borrower type", APP, "CODE",
                List.of("borrower type", "entity type"), null, null);
        manual(p, "application.entity_constitution", "Entity constitution", APP, "CODE",
                List.of("constitution", "private limited"), null, null);
        manual(p, "application.declared_income", "Declared income", APP, "INR",
                List.of("declared income"), null, null);
        manual(p, "application.declared_turnover", "Declared turnover", APP, "INR",
                List.of("declared turnover"), null, null);
        manual(p, "application.existing_emi", "Existing EMI", APP, "INR",
                List.of("existing emi"), null, null);
        manual(p, "application.proposed_emi", "Proposed EMI", APP, "INR",
                List.of("proposed emi"), null, null);
        manual(p, "product.code", "Product code", PROG, "CODE",
                List.of("product", "loan product"), null, null);
        manual(p, "program.sub_program", "Sub-program / program", PROG, "CODE",
                List.of("program", "sub program", "digileap", "reboost", "starter"), null, null);
        manual(p, "product.facility_security_flag", "Facility / security flag", PROG, "FLAG",
                List.of("secured facility", "security flag"), null, null);
    }

    private static void computed(List<CanonicalParameterDefinition> p) {
        derived(p, "obligation.ratio", "Obligation ratio (FOIR)", COMP, "PERCENT", "PIT",
                "Monthly obligations ÷ monthly income × 100",
                List.of("income", "emi"),
                "CreditControlService / PolicyRegistryMetricService.computeFoir",
                List.of("foir", "obligation ratio", "dti"),
                "OBLIGATION_RATIO", "OBLIGATION_RATIO",
                Capability.of("COMPUTED", true, true, true, true, true, "SCALAR", null,
                        "DATA_INSUFFICIENT", null, null, null),
                true, true, true, true, true);
        // DB-canonical (Flyway V123); seed omission was catalogue drift — keep productionReady=false.
        derived(p, "collateral.ltv", "LTV", COMP, "PERCENT", "PIT",
                "Loan amount / eligible collateral value * 100",
                List.of("application.requested_amount", "collateral.value"),
                "Collateral valuation / LTV ratio",
                List.of("ltv", "loan to value", "loan-to-value", "loan to value ratio"),
                "LTV", "LTV",
                Capability.of("COMPUTED", true, true, true, true, false, "SCALAR", null,
                        "DATA_INSUFFICIENT", null, null, null),
                true, true, true, true, false);
    }

    // ---- helpers ----

    private static void kycFact(List<CanonicalParameterDefinition> p, String id, String name, String unit,
                                boolean available, boolean implemented) {
        raw(p, id, name, KYC, unit, "SCALAR", "KycFactCatalog / NormalizedKycFactBuilder",
                List.of(), "KycFactCatalog", null, null,
                available, available, false, implemented, implemented && available);
    }

    private static void raw(
            List<CanonicalParameterDefinition> p, String id, String name, String from, String unit,
            String cardinality, String providerPath, List<String> aliases, String binding,
            String liveRule, String liveScorecard,
            boolean sourceAvailable, boolean normalized, boolean derivationDefined,
            boolean implemented, boolean productionReady
    ) {
        p.add(new CanonicalParameterDefinition(
                id, name, from, RAW, unit, null,
                productionReady ? "AVAILABLE_AUTOMATICALLY"
                        : (implemented ? "AVAILABLE_AUTOMATICALLY" : (sourceAvailable ? "SOURCE_AVAILABLE" : "UNAVAILABLE")),
                null, List.of(), binding, aliases, liveRule, liveScorecard,
                Capability.of(Capability.defaultsFor(RAW, from).schema(), sourceAvailable, normalized,
                        derivationDefined, implemented, productionReady, cardinality, providerPath,
                        null, null, null, null)));
    }

    private static void derived(
            List<CanonicalParameterDefinition> p, String id, String name, String from, String unit, String period,
            String calc, List<String> primitives, String binding, List<String> aliases,
            String liveRule, String liveScorecard, Capability cap,
            boolean sourceAvailable, boolean normalized, boolean derivationDefined,
            boolean implemented, boolean productionReady
    ) {
        Capability c = cap != null ? cap : Capability.of(
                Capability.defaultsFor(DERIVED, from).schema(),
                sourceAvailable, normalized, derivationDefined, implemented, productionReady,
                "SCALAR", null, null, null, null, null);
        p.add(new CanonicalParameterDefinition(
                id, name, from, DERIVED, unit, period,
                productionReady ? "AVAILABLE_AUTOMATICALLY"
                        : (implemented ? "AVAILABLE_AUTOMATICALLY" : "DERIVABLE_FROM_AVAILABLE_DATA"),
                calc, primitives, binding, aliases, liveRule, liveScorecard, c));
    }

    private static void bureauBuiltIn(
            List<CanonicalParameterDefinition> p, String id, String name, String unit, String period,
            String calc, List<String> primitives, String binding, List<String> aliases
    ) {
        derived(p, id, name, BR, unit, period, calc, primitives, binding, aliases, null, null,
                studioImpl(), true, true, true, true, false);
    }

    private static void bureauSourceNotProven(
            List<CanonicalParameterDefinition> p, String id, String name, String calc, List<String> aliases
    ) {
        derived(p, id, name, BR, "COUNT", "PIT", calc,
                List.of("bureau.tradeline.account_status"),
                "SOURCE_NOT_PROVEN", aliases, null, null,
                Capability.of("BUREAU_RETAIL", true, true, true, false, false, "SCALAR", null,
                        "SOURCE_NOT_PROVEN — Equifax sample vocabulary (Standard/STD/SPM) does not prove this status token",
                        null, null, null),
                true, true, true, false, false);
    }

    private static void derivedDefined(
            List<CanonicalParameterDefinition> p, String id, String name, String from,
            String calc, List<String> primitives, List<String> aliases
    ) {
        derived(p, id, name, from, null, null, calc + " [DEFINED_NOT_IMPLEMENTED]",
                primitives, "DEFINED_NOT_IMPLEMENTED", aliases, null, null,
                Capability.of(Capability.defaultsFor(DERIVED, from).schema(),
                        true, true, true, false, false, "SCALAR", null,
                        "Not implemented — do not treat as live", null, null, null),
                true, true, true, false, false);
    }

    private static void manual(
            List<CanonicalParameterDefinition> p, String id, String name, String from, String unit,
            List<String> aliases, String liveRule, String liveScorecard
    ) {
        p.add(new CanonicalParameterDefinition(
                id, name, from, MANUAL, unit, null, "MANUAL_INPUT_AVAILABLE",
                null, List.of("application"), "Application / Manual Input",
                aliases, liveRule, liveScorecard,
                Capability.of(Capability.defaultsFor(MANUAL, from).schema(),
                        true, true, false, true, true, "SCALAR", null, null, null, null, null)));
    }

    private static Capability prodBureau(boolean productionReady) {
        return Capability.of("BUREAU_RETAIL", true, true, true, true, productionReady,
                "SCALAR", null, null, null, null, null);
    }

    private static Capability studioImpl() {
        return Capability.of("BUREAU_RETAIL", true, true, true, true, false,
                "SCALAR", null, "Studio/policy helper — not production UW authority", null, null, null);
    }

    private static Capability bankStudio() {
        return Capability.of("BANK_STATEMENT", true, true, true, true, false,
                "SCALAR", null, "Studio/policy helper — confirm production banking path before go-live", null, null, null);
    }

    private static Capability prodBank() {
        return Capability.of("BANK_STATEMENT", true, true, true, true, true,
                "SCALAR", null, "DATA_INSUFFICIENT", null, null, null);
    }

    private static Capability prodGst() {
        return Capability.of("GST", true, true, true, true, true,
                "SCALAR", null, "DATA_INSUFFICIENT", null, null, null);
    }

    /** GST metric with explicit trailing period honesty (must match GstMetricService window). */
    private static Capability prodGstPeriod(String periodNote) {
        return Capability.of("GST", true, true, true, true, true,
                "SCALAR", null, "DATA_INSUFFICIENT", periodNote, null, null);
    }

    private static Capability cap(
            String schema, boolean sa, boolean n, boolean dd, boolean impl, boolean prod,
            String cardinality, String path, String missing, String filters, String transform, String agg
    ) {
        return Capability.of(schema, sa, n, dd, impl, prod, cardinality, path, missing, filters, transform, agg);
    }
}
