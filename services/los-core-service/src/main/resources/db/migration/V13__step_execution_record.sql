-- Lightweight audit of flow step executors (not a workflow engine; no dynamic sequencing)

CREATE TABLE step_execution_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL,
    step_type       VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    input_json      TEXT,
    output_json     TEXT,
    error_code      VARCHAR(200),
    error_message   TEXT,
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_step_exec_application_id ON step_execution_record (application_id);
