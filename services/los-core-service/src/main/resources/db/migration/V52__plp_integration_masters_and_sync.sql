-- PLP integration: anchor/program/sub-program masters and loan application sync columns

CREATE TABLE anchor_masters (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        VARCHAR(50) NOT NULL UNIQUE,
    name                        VARCHAR(200) NOT NULL,
    pan                         VARCHAR(10),
    gstin                       VARCHAR(15),
    email                       VARCHAR(255),
    mobile                      VARCHAR(20),
    address                     VARCHAR(500),
    source_anchor_application_id UUID,
    plp_anchor_id               UUID,
    plp_anchor_sync_status      VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    plp_anchor_sync_error       VARCHAR(500),
    plp_anchor_synced_at        TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_anchor_masters_plp_sync_status ON anchor_masters (plp_anchor_sync_status);

CREATE TABLE program_masters (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_code                VARCHAR(50) NOT NULL UNIQUE,
    program_name                VARCHAR(200) NOT NULL,
    product_type                VARCHAR(50) NOT NULL,
    program_limit               NUMERIC(15, 2),
    max_borrower_limit          NUMERIC(15, 2),
    workflow_config_id          UUID REFERENCES workflow_configs(id),
    plp_lender_id               UUID NOT NULL,
    plp_program_id              UUID,
    plp_program_sync_status     VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    plp_program_sync_error      VARCHAR(500),
    plp_program_synced_at       TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_program_masters_plp_sync_status ON program_masters (plp_program_sync_status);

CREATE TABLE sub_program_masters (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sub_program_code                VARCHAR(50) NOT NULL UNIQUE,
    name                            VARCHAR(200) NOT NULL,
    program_id                      UUID NOT NULL REFERENCES program_masters(id),
    anchor_id                       UUID NOT NULL REFERENCES anchor_masters(id),
    flow_type                       VARCHAR(50),
    anchor_role                     VARCHAR(50),
    borrower_role                   VARCHAR(50),
    sub_program_limit               NUMERIC(15, 2),
    plp_sub_program_id              UUID,
    plp_sub_program_sync_status     VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    plp_sub_program_sync_error      VARCHAR(500),
    plp_sub_program_synced_at       TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sub_program_program_anchor_code UNIQUE (program_id, anchor_id, sub_program_code)
);

CREATE INDEX idx_sub_program_masters_plp_sync_status ON sub_program_masters (plp_sub_program_sync_status);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS sub_program_id UUID REFERENCES sub_program_masters(id),
    ADD COLUMN IF NOT EXISTS plp_borrower_id UUID,
    ADD COLUMN IF NOT EXISTS plp_sub_program_borrower_id UUID,
    ADD COLUMN IF NOT EXISTS plp_borrower_program_mapping_id UUID,
    ADD COLUMN IF NOT EXISTS plp_program_sync_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN IF NOT EXISTS plp_program_sync_error VARCHAR(500),
    ADD COLUMN IF NOT EXISTS plp_program_synced_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_loan_applications_plp_sync_status ON loan_applications (plp_program_sync_status);
CREATE INDEX IF NOT EXISTS idx_loan_applications_sub_program ON loan_applications (sub_program_id);

COMMENT ON COLUMN loan_applications.plp_program_sync_status IS 'Overall PLP sanction sync status per handoff §3.4';
