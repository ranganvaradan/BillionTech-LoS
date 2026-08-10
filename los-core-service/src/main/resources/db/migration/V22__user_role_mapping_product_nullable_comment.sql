-- loan_product is already nullable in V21; document wildcard semantics.
COMMENT ON COLUMN user_role_mappings.loan_product IS 'When NULL or unused, matches all loan products. Otherwise exact case-insensitive match.';
