-- Policy Studio sessions are session-addressed (new document_id per create/reset).
-- uq_ci_policy_doc_hash blocked legitimate multi-session identical content
-- (banking demo fixture, reused scratch boilerplate). Keep content_hash as a
-- non-unique checksum for future soft-dedup; drop uniqueness only.
ALTER TABLE ci_policy_document DROP CONSTRAINT IF EXISTS uq_ci_policy_doc_hash;

CREATE INDEX IF NOT EXISTS idx_ci_policy_doc_content_hash
    ON ci_policy_document (tenant_id, content_hash);
