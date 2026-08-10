-- Demo underwriting policies (match borrower_type + loan_product + optional ranges; rules_json drives decision).
INSERT INTO underwriting_rule_sets
(id, name, borrower_type, loan_product, min_amount, max_amount, geography, min_tenure_months, max_tenure_months, priority, active, rules_json, created_at)
VALUES
(
  'c1000000-0000-0000-0000-000000000001',
  'Individual Personal — standard',
  'INDIVIDUAL',
  'Personal',
  NULL, NULL, NULL, NULL, NULL,
  100, TRUE,
  '{"minBureauScore":650,"maxLoanAmount":2500000,"requireKycSuccess":true,"decision":"APPROVE","reasons":["Meets retail personal policy"]}'::jsonb,
  now()
),
(
  'c1000000-0000-0000-0000-000000000002',
  'Individual Personal Loan — standard',
  'INDIVIDUAL',
  'PERSONAL_LOAN',
  NULL, NULL, NULL, NULL, NULL,
  100, TRUE,
  '{"minBureauScore":640,"maxLoanAmount":3000000,"requireKycSuccess":true,"decision":"MANUAL_REVIEW","reasons":["Amount or profile needs credit manager review"]}'::jsonb,
  now()
),
(
  'c1000000-0000-0000-0000-000000000003',
  'Individual LAP — secured',
  'INDIVIDUAL',
  'LAP',
  NULL, NULL, NULL, 12, 240,
  90, TRUE,
  '{"minBureauScore":620,"maxLoanAmount":15000000,"requireKycSuccess":true,"decision":"APPROVE","reasons":["LAP collateral-backed policy"]}'::jsonb,
  now()
),
(
  'c1000000-0000-0000-0000-000000000004',
  'Proprietor Term — small business',
  'PROPRIETOR',
  'TERM_LOAN',
  NULL, NULL, NULL, NULL, NULL,
  80, TRUE,
  '{"minBureauScore":660,"maxLoanAmount":5000000,"requireKycSuccess":true,"decision":"MANUAL_REVIEW","reasons":["Proprietor term — credit manager review"]}'::jsonb,
  now()
);
