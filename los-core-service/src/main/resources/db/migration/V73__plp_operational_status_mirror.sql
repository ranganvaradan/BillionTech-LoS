ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS plp_operational_status VARCHAR(32);
