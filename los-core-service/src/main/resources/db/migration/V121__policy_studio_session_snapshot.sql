-- POLICY-STUDIO-SESSION-PERSISTENCE-P0
-- ci_policy_document alone cannot hold underwriting rules / expression AST
-- (only source_text + metadata). Normalized ci_policy_rule_candidate.expression
-- can hold AST but requires clause_id NOT NULL and does not cover the full
-- PolicyStudioSession aggregate (readiness, lifecycle metadata, CM rules without
-- a durable clause row, parameter bindings on the session).
-- This snapshot table is the durable authority for full session rehydrate by documentId.

CREATE TABLE IF NOT EXISTS ci_policy_studio_session_snapshot (
    policy_document_id  UUID PRIMARY KEY REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash        VARCHAR(128),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_studio_session_tenant
    ON ci_policy_studio_session_snapshot (tenant_id, updated_at DESC);

COMMENT ON TABLE ci_policy_studio_session_snapshot IS
    'Full Policy Studio session payload (rules/AST/bindings) surviving JVM restart';
