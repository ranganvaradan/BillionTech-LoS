-- Close a TOCTOU race in ExternalProductMappingAdminService: the app-level overlap check
-- (findActiveOverlapping) is a SELECT followed by a separate SAVE, so two concurrent admin
-- requests activating overlapping-window mappings for the same (los_product_code,
-- external_system) can both pass validation and commit. Add a database-level exclusion
-- constraint as the authoritative backstop — semantics mirror the existing app-level check
-- (effective_from <= other.effective_to AND effective_to >= other.effective_from, i.e. an
-- inclusive-inclusive date range overlap), restricted to ACTIVE rows only.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE external_product_mapping
    ADD CONSTRAINT excl_external_product_mapping_active_no_overlap
    EXCLUDE USING gist (
        los_product_code WITH =,
        external_system WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
    WHERE (status = 'ACTIVE');
