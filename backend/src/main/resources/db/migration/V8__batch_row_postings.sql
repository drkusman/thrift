-- A SAVINGS row can now fan out into several postings (the member's own monthly savings, one or more
-- loan repayments in disbursed-date order, and a Refund of Over Deduction if there's money left over
-- after every running loan is covered) - one row per resulting posting replaces the single
-- ledger_entry_id/schedule_id columns so a batch delete can still reverse every one of them exactly.
CREATE TABLE monthly_contribution_batch_row_postings (
    id BIGSERIAL PRIMARY KEY,
    batch_row_id BIGINT NOT NULL REFERENCES monthly_contribution_batch_rows(id),
    ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    schedule_id BIGINT REFERENCES loan_repayment_schedules(id),
    amount BIGINT NOT NULL
);

INSERT INTO monthly_contribution_batch_row_postings (batch_row_id, ledger_entry_id, schedule_id, amount)
SELECT id, ledger_entry_id, schedule_id, amount FROM monthly_contribution_batch_rows WHERE ledger_entry_id IS NOT NULL;

ALTER TABLE monthly_contribution_batch_rows DROP COLUMN ledger_entry_id;
ALTER TABLE monthly_contribution_batch_rows DROP COLUMN schedule_id;
