ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS dependency_vintage_percent NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS anchor_relationship_vintage_months INTEGER;
