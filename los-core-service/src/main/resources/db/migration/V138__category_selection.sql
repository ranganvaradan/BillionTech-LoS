-- Category selection persistence (eligibility/disambiguation/selection only).
-- Does not activate Categories, run Policy/Scorecard, or trigger W4/W6.

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS selected_customer_category_id UUID,
    ADD COLUMN IF NOT EXISTS selected_customer_category_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS selected_customer_category_version INT,
    ADD COLUMN IF NOT EXISTS selected_policy_applicability_id UUID,
    ADD COLUMN IF NOT EXISTS selected_policy_document_id UUID,
    ADD COLUMN IF NOT EXISTS selected_policy_version_label VARCHAR(40),
    ADD COLUMN IF NOT EXISTS category_selection_source VARCHAR(40),
    ADD COLUMN IF NOT EXISTS category_selected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS category_selected_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS category_selection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS category_selection_state VARCHAR(60);

COMMENT ON COLUMN loan_applications.selected_customer_category_id IS
    'Pinned Customer Category Version row id after selection (immutable for app).';
COMMENT ON COLUMN loan_applications.category_selection_source IS
    'AUTO_SINGLE_ELIGIBLE | CUSTOMER_SELECTED | RM_SELECTED | PROGRAMME_SELECTED | CHANNEL_SELECTED | OTHER_CONTROLLED';
COMMENT ON COLUMN loan_applications.category_selection_state IS
    'NO_ELIGIBLE_CATEGORY | AUTO_SINGLE_MATCH | DISAMBIGUATION_REQUIRED | EXPLICIT_PROPOSITION_SELECTION_REQUIRED | CATEGORY_SELECTED';

CREATE TABLE IF NOT EXISTS application_category_disambiguation_answer (
    id                      UUID PRIMARY KEY,
    application_id          UUID         NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    question_id             VARCHAR(80)  NOT NULL,
    answer_value            VARCHAR(120) NOT NULL,
    actor                   VARCHAR(120) NULL,
    actor_role              VARCHAR(40)  NULL,
    candidate_before        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    candidate_after         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_cat_disambig_application_id
    ON application_category_disambiguation_answer (application_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_cat_disambig_app_question
    ON application_category_disambiguation_answer (application_id, question_id);

COMMENT ON TABLE application_category_disambiguation_answer IS
    'Save/resume answers for Category progressive disambiguation — no Policy thresholds stored.';
