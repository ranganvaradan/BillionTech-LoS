-- EQUIFAX-SOURCE-CARD-COUNT-RECONCILIATION-1
-- V131 inserted bureau.inquiries.last_3m with capability-schema BUREAU_RETAIL as source_family.
-- Collapse that alias onto the canonical Bureau Retail family. Does not change readiness flags.

UPDATE ci_gacat_canonical_parameter
   SET source_family = 'Bureau Retail',
       updated_at = NOW()
 WHERE source_family IN ('BUREAU_RETAIL', 'bureau_retail', 'Bureau_Retail');
