-- Immutable audit trail for staff-initiated borrower application deletions.

CREATE TABLE IF NOT EXISTS application_deletion_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL,
    application_number  VARCHAR(30) NOT NULL,
    loan_product        VARCHAR(50) NOT NULL,
    intake_segment      VARCHAR(20) NOT NULL,
    application_status  VARCHAR(30) NOT NULL,
    borrower_user_id    UUID,
    borrower_email      VARCHAR(200),
    plp_borrower_id     UUID,
    deleted_by_user_id  UUID,
    deleted_by_email    VARCHAR(200),
    deleted_by_role     VARCHAR(50),
    reason              TEXT,
    plp_cleanup_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    plp_cleanup_summary TEXT,
    snapshot_json       JSONB,
    deleted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_application_deletion_logs_deleted_at
    ON application_deletion_logs (deleted_at DESC);

CREATE INDEX IF NOT EXISTS idx_application_deletion_logs_application_number
    ON application_deletion_logs (application_number);
