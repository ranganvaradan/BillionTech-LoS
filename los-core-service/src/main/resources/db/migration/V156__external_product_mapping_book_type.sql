-- Own Book vs Colending: a new required, non-wildcard dimension in the external_product_mapping
-- resolution key, captured at Sanction. Nullable/wildcard was rejected — a Postgres exclusion
-- constraint's equality terms never match NULL to anything (including another NULL), so a
-- wildcard row would silently fail to conflict-check against a book-type-specific row, defeating
-- the overlap protection added in V153. Existing rows backfill to OWN_BOOK (matches current
-- implicit behavior — nothing today does colending routing). New rows must specify book_type
-- explicitly at the application layer (see ExternalProductMappingAdminService.createMapping) —
-- the DEFAULT below exists only to backfill legacy rows.

ALTER TABLE external_product_mapping
    ADD COLUMN IF NOT EXISTS book_type VARCHAR(20) NOT NULL DEFAULT 'OWN_BOOK';

ALTER TABLE external_product_mapping
    DROP CONSTRAINT IF EXISTS uq_external_product_mapping_business;
ALTER TABLE external_product_mapping
    ADD CONSTRAINT uq_external_product_mapping_business
        UNIQUE (los_product_code, external_system, book_type, version);

ALTER TABLE external_product_mapping
    DROP CONSTRAINT IF EXISTS excl_external_product_mapping_active_no_overlap;
ALTER TABLE external_product_mapping
    ADD CONSTRAINT excl_external_product_mapping_active_no_overlap
    EXCLUDE USING gist (
        los_product_code WITH =,
        external_system WITH =,
        book_type WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
    WHERE (status = 'ACTIVE');

-- Nullable: set only at sanction time, once book type is known.
ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS book_type VARCHAR(20);

ALTER TABLE sanction_records
    ADD COLUMN IF NOT EXISTS book_type VARCHAR(20);

COMMENT ON COLUMN external_product_mapping.book_type IS
    'Own Book vs Colending routing classification — required, no wildcard, part of the resolution key.';
COMMENT ON COLUMN loan_applications.book_type IS
    'Own Book vs Colending, captured at sanction time; drives external_product_mapping resolution.';
COMMENT ON COLUMN sanction_records.book_type IS
    'Own Book vs Colending selection captured at sanction, for audit.';
