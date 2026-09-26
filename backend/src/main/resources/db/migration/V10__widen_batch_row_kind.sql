-- "REFUND OF OVER DEDUCTION" (24 chars) exceeds the original VARCHAR(20) - widen for admin's free-text
-- KIND column, whose nomenclature varies across uploads.
ALTER TABLE monthly_contribution_batch_rows ALTER COLUMN kind TYPE VARCHAR(50);
