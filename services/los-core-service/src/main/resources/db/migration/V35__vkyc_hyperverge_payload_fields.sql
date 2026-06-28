ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS vkyc_transaction_id varchar(120),
    ADD COLUMN IF NOT EXISTS vkyc_last_event varchar(80),
    ADD COLUMN IF NOT EXISTS vkyc_event_payload text,
    ADD COLUMN IF NOT EXISTS vkyc_result_payload text,
    ADD COLUMN IF NOT EXISTS vkyc_agent_name varchar(200),
    ADD COLUMN IF NOT EXISTS vkyc_agent_updated_on timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_completed_on timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_video_url text,
    ADD COLUMN IF NOT EXISTS vkyc_pan_image_url text,
    ADD COLUMN IF NOT EXISTS vkyc_face_image_url text,
    ADD COLUMN IF NOT EXISTS aml_hit boolean;
