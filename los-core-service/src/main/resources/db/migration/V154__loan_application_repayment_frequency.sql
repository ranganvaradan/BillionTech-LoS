-- Repayment Frequency is a genuinely new, independently-persisted field shown on the intake
-- page and on KFS/sanction documents, alongside Tenure. It is derived from lms_tenure_unit at
-- intake (Day/Week/Month) rather than freely selectable, because the actual EMI cadence is
-- determined by the Encore product/tenure-unit combination — no schedule engine reads an
-- independent frequency value today. Nullable: legacy applications fall back to deriving it
-- from lms_tenure_unit at read time (see EdiKfsTemplateContextBuilder, KfsService, KfsPdfGenerationService).

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS repayment_frequency VARCHAR(20);

COMMENT ON COLUMN loan_applications.repayment_frequency IS
    'Repayment/installment frequency shown on KFS and sanction documents, derived from lms_tenure_unit at intake.';
