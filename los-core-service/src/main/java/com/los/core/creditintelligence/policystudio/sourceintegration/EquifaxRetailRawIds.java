package com.los.core.creditintelligence.policystudio.sourceintegration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exact Equifax retail RAW canonical IDs that have a production extractor → persist → producer path.
 * Registration in this set is allowed only when that path exists.
 */
public final class EquifaxRetailRawIds {

    private EquifaxRetailRawIds() {}

    public static final Set<String> ALL = Set.copyOf(allMutable());

    private static LinkedHashSet<String> allMutable() {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        s.add("bureau.score");
        s.add("bureau.score.name");
        s.add("bureau.report.date");
        s.add("bureau.report.time");
        s.add("bureau.hit_code");
        s.add("bureau.success_code");
        s.add("bureau.report_order_no");
        s.add("bureau.scoring_element.code");
        s.add("bureau.scoring_element.description");

        s.add("bureau.summary.account_count");
        s.add("bureau.summary.active_account_count");
        s.add("bureau.summary.writeoff_count");
        s.add("bureau.summary.total_past_due");
        s.add("bureau.summary.most_severe_status_24m");
        s.add("bureau.summary.total_balance");
        s.add("bureau.summary.total_sanction");
        s.add("bureau.summary.total_credit_limit");
        s.add("bureau.summary.total_monthly_payment");
        s.add("bureau.summary.highest_sanction");
        s.add("bureau.summary.highest_balance");
        s.add("bureau.summary.average_open_balance");
        s.add("bureau.summary.age_of_oldest_trade_months");
        s.add("bureau.summary.open_trade_count");
        s.add("bureau.summary.past_due_account_count");
        s.add("bureau.summary.zero_balance_account_count");
        s.add("bureau.summary.highest_credit");
        s.add("bureau.summary.total_high_credit");
        s.add("bureau.summary.recent_account_narrative");
        s.add("bureau.summary.oldest_account_narrative");
        s.add("bureau.summary.all_lines_ever_written");
        s.add("bureau.summary.all_lines_ever_written_9m");
        s.add("bureau.summary.all_lines_ever_written_6m");

        s.add("bureau.enquiry.summary.total");
        s.add("bureau.enquiry.summary.past_30d");
        s.add("bureau.enquiry.summary.past_12m");
        s.add("bureau.enquiry.summary.past_24m");
        s.add("bureau.enquiry.summary.recent_date");
        s.add("bureau.enquiry.summary.purpose");

        s.add("bureau.recent.accounts_opened_90d");
        s.add("bureau.recent.accounts_updated_90d");
        s.add("bureau.recent.accounts_delinquent_90d");
        s.add("bureau.recent.inquiries_90d");

        s.add("bureau.tradelines");
        s.add("bureau.inquiries");
        s.add("bureau.inquiry");
        s.add("bureau.inquiry.date");
        s.add("bureau.inquiry.purpose");
        s.add("bureau.inquiry.amount");
        s.add("bureau.inquiry.member");
        s.add("bureau.inquiry.time");

        s.add("bureau.tradeline.account_type");
        s.add("bureau.tradeline.ownership");
        s.add("bureau.tradeline.lender");
        s.add("bureau.tradeline.account_open_date");
        s.add("bureau.tradeline.account_close_date");
        s.add("bureau.tradeline.date_reported");
        s.add("bureau.tradeline.sanction_amount");
        s.add("bureau.tradeline.current_balance");
        s.add("bureau.tradeline.overdue_amount");
        s.add("bureau.tradeline.credit_limit");
        s.add("bureau.tradeline.emi");
        s.add("bureau.tradeline.tenure_months");
        s.add("bureau.tradeline.interest_rate");
        s.add("bureau.tradeline.account_status");
        s.add("bureau.tradeline.write_off_amount");
        s.add("bureau.tradeline.settlement_amount");
        s.add("bureau.tradeline.suit_filed");
        s.add("bureau.tradeline.wilful_default");
        s.add("bureau.tradeline.asset_classification");
        s.add("bureau.tradeline.collateral_type");
        s.add("bureau.tradeline.collateral_value");
        s.add("bureau.tradeline.payment_history");
        s.add("bureau.tradeline.payment_status_month");
        s.add("bureau.tradeline.dpd_month");
        s.add("bureau.tradeline.secured_flag");
        s.add("bureau.tradeline.last_payment_amount");
        s.add("bureau.tradeline.last_payment_date");
        s.add("bureau.tradeline.term_frequency");
        s.add("bureau.tradeline.dispute_code");
        s.add("bureau.tradeline.closure_reason");
        s.add("bureau.tradeline.suit_filed_month");
        s.add("bureau.tradeline.asset_classification_month");
        return s;
    }
}
