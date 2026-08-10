-- Program-level LMS (Encore) configuration for invoice discounting and PLP sync

ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS lms_entry_in VARCHAR(3) NOT NULL DEFAULT 'NO',
    ADD COLUMN IF NOT EXISTS encore_product_code VARCHAR(50);

COMMENT ON COLUMN program_masters.lms_entry_in IS 'YES = post to Encore LMS; NO = internal account id only (bl-core lms_entry_in parity)';
COMMENT ON COLUMN program_masters.encore_product_code IS 'Encore product code when lms_entry_in=YES (invoice discounting programs)';
