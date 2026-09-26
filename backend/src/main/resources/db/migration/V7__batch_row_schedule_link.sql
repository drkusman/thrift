-- Tracks which schedule installment (if any) a LOAN_REPAYMENT row applied its amount to, so an admin
-- deleting a bad upload can reverse that exact amount off that exact installment rather than guessing.
ALTER TABLE monthly_contribution_batch_rows ADD COLUMN schedule_id BIGINT REFERENCES loan_repayment_schedules(id);
