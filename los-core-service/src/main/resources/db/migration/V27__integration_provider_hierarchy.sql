-- LOS default provider hierarchy (priority: higher = tried first).
-- Deactivates legacy broad KYC / single eSign rows from V12; adds step-scoped KYC + dual eSign chain.

ALTER TABLE aggregator_routing ADD COLUMN IF NOT EXISTS metadata_json JSONB;
ALTER TABLE aggregator_routing ADD COLUMN IF NOT EXISTS esign_step_type VARCHAR(40);

UPDATE aggregator_routing
SET active = false
WHERE id IN (
    'a0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000020'
);

UPDATE aggregator_routing
SET priority = 200,
    allow_fallback = false
WHERE integration_type = 'BUREAU'
  AND provider_name = 'EQUIFAX';

-- --- KYC: PERFIOS (p1) + AUTHBRIDGE (p2) ---
INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, esign_step_type, priority, active, allow_fallback, metadata_json) VALUES
(gen_random_uuid(), 'PERFIOS', 'KYC', 'AADHAAR_OTP', NULL, 200, true, true, '{"purpose":"Aadhaar eKYC via OTP","appliesTo":"All / secondary ID"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'AADHAAR_OTP', NULL, 190, true, true, '{"purpose":"Aadhaar eKYC via OTP","appliesTo":"All / secondary ID"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'PAN_VERIFY', NULL, 200, true, true, '{"purpose":"PAN verification and name match","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'PAN_VERIFY', NULL, 190, true, true, '{"purpose":"PAN verification and name match","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'GSTIN_VERIFY', NULL, 200, true, true, '{"purpose":"GSTIN validation","appliesTo":"Proprietor, Partnership, Company"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'GSTIN_VERIFY', NULL, 190, true, true, '{"purpose":"GSTIN validation","appliesTo":"Proprietor, Partnership, Company"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'VOTER_ID_VERIFY', NULL, 200, true, true, '{"purpose":"Voter ID verification","appliesTo":"Optional secondary ID"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'VOTER_ID_VERIFY', NULL, 190, true, true, '{"purpose":"Voter ID verification","appliesTo":"Optional secondary ID"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'DL_VERIFY', NULL, 200, true, true, '{"purpose":"Driving License verification","appliesTo":"Optional secondary ID"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'DL_VERIFY', NULL, 190, true, true, '{"purpose":"Driving License verification","appliesTo":"Optional secondary ID"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'BANK_PENNY_DROP', NULL, 200, true, true, '{"purpose":"Bank account verification","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'BANK_PENNY_DROP', NULL, 190, true, true, '{"purpose":"Bank account verification","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'UDYAM_VERIFY', NULL, 200, true, true, '{"purpose":"Udyam registration verification","appliesTo":"MSME loans"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'UDYAM_VERIFY', NULL, 190, true, true, '{"purpose":"Udyam registration verification","appliesTo":"MSME loans"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'CIN_MCA21', NULL, 200, true, true, '{"purpose":"Company/director verification","appliesTo":"Company"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'CIN_MCA21', NULL, 190, true, true, '{"purpose":"Company/director verification","appliesTo":"Company"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'AML_SCREENING', NULL, 200, true, true, '{"purpose":"AML check","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'AML_SCREENING', NULL, 190, true, true, '{"purpose":"AML check","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'MOBILE_OTP', NULL, 200, true, true, '{"purpose":"Mobile OTP","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'MOBILE_OTP', NULL, 190, true, true, '{"purpose":"Mobile OTP","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'PERFIOS', 'KYC', 'EMAIL_OTP', NULL, 200, true, true, '{"purpose":"Email OTP","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE', 'KYC', 'EMAIL_OTP', NULL, 190, true, true, '{"purpose":"Email OTP","appliesTo":"All"}'::jsonb);

-- KYC: Hyperverge-only steps
INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, esign_step_type, priority, active, allow_fallback, metadata_json) VALUES
(gen_random_uuid(), 'HYPERVERGE', 'KYC', 'FACE_MATCH', NULL, 200, true, false, '{"purpose":"Selfie vs Aadhaar face match","appliesTo":"Individual, Proprietor"}'::jsonb),
(gen_random_uuid(), 'HYPERVERGE', 'KYC', 'LIVENESS', NULL, 200, true, false, '{"purpose":"Liveness detection","appliesTo":"Individual, Proprietor"}'::jsonb),
(gen_random_uuid(), 'HYPERVERGE', 'KYC', 'VIDEO_KYC', NULL, 200, true, false, '{"purpose":"V-CIP","appliesTo":"On-demand"}'::jsonb);

-- CKYC (single provider, no fallback in chain)
INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, esign_step_type, priority, active, allow_fallback, metadata_json) VALUES
(gen_random_uuid(), 'CKYC_REGISTRY', 'KYC', 'CKYC_DOWNLOAD', NULL, 200, true, false, '{"purpose":"Central KYC record download","appliesTo":"All"}'::jsonb),
(gen_random_uuid(), 'CKYC_REGISTRY', 'KYC', 'CKYC_UPLOAD', NULL, 200, true, false, '{"purpose":"Central KYC record upload","appliesTo":"Post-KYC completion"}'::jsonb);

-- eSign: EMSIGNER p1, AUTHBRIDGE_ESIGN p2 (scope null = all eSign document flows)
INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, esign_step_type, priority, active, allow_fallback, metadata_json) VALUES
(gen_random_uuid(), 'EMSIGNER', 'ESIGN', NULL, NULL, 200, true, true, '{"purpose":"KFS / loan agreement digital signing","appliesTo":"All post-approval"}'::jsonb),
(gen_random_uuid(), 'AUTHBRIDGE_ESIGN', 'ESIGN', NULL, NULL, 190, true, true, '{"purpose":"eSign provider fallback","appliesTo":"All post-approval"}'::jsonb);
