-- Credit Vintage (NEW / EXISTING_CUSTOMER / EXISTING_CUSTOMER_OF_GROUP): a new scoping
-- dimension parallel to the existing borrower_type/loan_product/intake_segment triple on
-- customer_category and workflow_configs, used to route category-governed applications to
-- different Workflow/Policy configurations based on customer relationship history.
--
-- Backward compatibility (critical): the existing wildcard matcher (MatchWildcard.isAny /
-- CustomerCategoryEligibilityService.dimMatch) treats the literal string 'ANY' as a wildcard,
-- but NULL is a hard fail, not a wildcard. Every existing row on both tables is backfilled to
-- 'ANY' so no existing Category/Workflow bind or discovery match is affected. New rows can be
-- scoped to a specific vintage on both sides if an admin wants a real restriction.

ALTER TABLE customer_category
    ADD COLUMN IF NOT EXISTS credit_vintage VARCHAR(40) NOT NULL DEFAULT 'ANY';

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS credit_vintage VARCHAR(40) NOT NULL DEFAULT 'ANY';

-- Nullable, no default: RM must explicitly select it at intake. An unset value correctly
-- fails to match any Category that is actually scoped to a specific vintage, while 'ANY'
-- scoped Categories (i.e. everything today) still match regardless.
ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS credit_vintage VARCHAR(40);

COMMENT ON COLUMN customer_category.credit_vintage IS
    'Credit Vintage scoping dimension: NEW, EXISTING_CUSTOMER, EXISTING_CUSTOMER_OF_GROUP, or ANY (wildcard).';
COMMENT ON COLUMN workflow_configs.credit_vintage IS
    'Credit Vintage scoping dimension, cross-checked against the bound Category at admin bind time.';
COMMENT ON COLUMN loan_applications.credit_vintage IS
    'RM-selected Credit Vintage at intake; participates in Customer Category discovery/eligibility matching.';
