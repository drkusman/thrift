-- Keeps the original uploaded workbook so an admin can re-download exactly what was submitted for a
-- period, not just its parsed summary.
ALTER TABLE monthly_contribution_batches ADD COLUMN file_bytes BYTEA;
