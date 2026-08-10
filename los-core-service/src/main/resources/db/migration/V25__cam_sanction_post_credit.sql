-- Credit Appraisal Memo (CAM) and formal sanction record
CREATE TABLE credit_appraisal_memos (
    id                 UUID         NOT NULL PRIMARY KEY,
    application_id     UUID         NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    cam_json           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    observations       TEXT,
    risk_assessment    TEXT,
    mitigants          TEXT,
    recommended_decision VARCHAR(30),
    cam_reviewed       BOOLEAN      NOT NULL DEFAULT FALSE,
    cam_pdf_path       VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_cam_application ON credit_appraisal_memos (application_id);

CREATE TABLE sanction_records (
    id                 UUID         NOT NULL PRIMARY KEY,
    application_id     UUID         NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    approved_amount    NUMERIC(15, 2) NOT NULL,
    approved_tenure    INTEGER      NOT NULL,
    interest_rate      NUMERIC(5, 2) NOT NULL,
    processing_fee     NUMERIC(15, 2),
    conditions_text    TEXT,
    remarks            TEXT,
    approved_by        VARCHAR(200),
    sanction_pdf_path  VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sanction_app ON sanction_records (application_id);

COMMENT ON TABLE credit_appraisal_memos IS 'Auto-generated + editable Credit Appraisal Memo per application';
COMMENT ON TABLE sanction_records IS 'Sanction / in-principle approval terms after CAM review';
