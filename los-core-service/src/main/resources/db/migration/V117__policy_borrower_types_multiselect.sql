-- PRODUCT-CONFIG-ROUTING-CONVERGENCE-1 — Policy scope borrowerTypes collection
-- Explicit ALL = empty list (and legacy borrower_type NULL). No silent widening.

ALTER TABLE ci_policy_applicability
    ADD COLUMN IF NOT EXISTS borrower_types JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Backfill from legacy scalar without inventing ALL
UPDATE ci_policy_applicability
SET borrower_types = jsonb_build_array(borrower_type)
WHERE borrower_type IS NOT NULL
  AND borrower_type <> ''
  AND (borrower_types IS NULL OR borrower_types = '[]'::jsonb);

COMMENT ON COLUMN ci_policy_applicability.borrower_types IS
    'JSON array of borrower type codes. Empty = ALL. Legacy borrower_type kept for read compatibility.';
COMMENT ON COLUMN ci_policy_applicability.borrower_type IS
    'Legacy scalar borrower type; prefer borrower_types. Null with empty borrower_types = ALL.';
