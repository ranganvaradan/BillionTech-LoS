-- EQUIFAX RETAIL RAW CLOSURE — typed persistence for provider summary, scoring elements,
-- enquiry time, tradeline extras, and month-level suit/SMA. Does not rewrite existing values.

CREATE TABLE IF NOT EXISTS ci_bureau_report_summary (
    bureau_report_id                UUID PRIMARY KEY
        REFERENCES ci_bureau_report (id) ON DELETE CASCADE,
    hit_code                        VARCHAR(20),
    success_code                    VARCHAR(20),
    report_order_no                 VARCHAR(80),
    score_name                      VARCHAR(80),
    account_count                   INTEGER,
    active_account_count            INTEGER,
    writeoff_count                  INTEGER,
    total_past_due                  NUMERIC(18, 2),
    most_severe_status_24m          VARCHAR(40),
    total_balance                   NUMERIC(18, 2),
    total_sanction                  NUMERIC(18, 2),
    total_credit_limit              NUMERIC(18, 2),
    total_monthly_payment           NUMERIC(18, 2),
    highest_sanction                NUMERIC(18, 2),
    highest_balance                 NUMERIC(18, 2),
    average_open_balance            NUMERIC(18, 2),
    age_of_oldest_trade_months      INTEGER,
    open_trade_count                INTEGER,
    past_due_account_count          INTEGER,
    zero_balance_account_count      INTEGER,
    highest_credit                  NUMERIC(18, 2),
    total_high_credit               NUMERIC(18, 2),
    enquiry_total                   INTEGER,
    enquiry_past_30d                INTEGER,
    enquiry_past_12m                INTEGER,
    enquiry_past_24m                INTEGER,
    enquiry_recent_date             DATE,
    recent_accounts_opened_90d      INTEGER,
    recent_accounts_updated_90d     INTEGER,
    recent_accounts_delinquent_90d  INTEGER,
    recent_inquiries_90d            INTEGER,
    report_time                     VARCHAR(16),
    enquiry_summary_purpose         VARCHAR(40),
    all_lines_ever_written          NUMERIC(18, 2),
    all_lines_ever_written_9m       NUMERIC(18, 2),
    all_lines_ever_written_6m       NUMERIC(18, 2),
    recent_account_narrative        VARCHAR(400),
    oldest_account_narrative        VARCHAR(400),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ci_bureau_report_summary IS
    'Equifax (and future bureau) RAW provider summary facts. Distinct from LOS derived metrics.';

CREATE TABLE IF NOT EXISTS ci_bureau_scoring_element (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bureau_report_id    UUID NOT NULL REFERENCES ci_bureau_report (id) ON DELETE CASCADE,
    seq_no              INTEGER NOT NULL DEFAULT 0,
    code                VARCHAR(40),
    description         VARCHAR(400),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_scoring_element_report
    ON ci_bureau_scoring_element (bureau_report_id, seq_no);

ALTER TABLE ci_bureau_inquiry
    ADD COLUMN IF NOT EXISTS inquiry_time VARCHAR(16);

ALTER TABLE ci_bureau_tradeline
    ADD COLUMN IF NOT EXISTS last_payment_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS last_payment_date DATE,
    ADD COLUMN IF NOT EXISTS term_frequency VARCHAR(40),
    ADD COLUMN IF NOT EXISTS dispute_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS closure_reason VARCHAR(120);

ALTER TABLE ci_bureau_tradeline
    ALTER COLUMN suit_filed DROP NOT NULL,
    ALTER COLUMN wilful_default DROP NOT NULL;

ALTER TABLE ci_bureau_payment_history
    ADD COLUMN IF NOT EXISTS suit_filed_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS asset_classification_status VARCHAR(40);
