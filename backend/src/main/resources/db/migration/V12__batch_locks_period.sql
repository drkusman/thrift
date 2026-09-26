-- Only a batch containing a SAVINGS-waterfall or LOAN_REPAYMENT row genuinely can't be re-run for the
-- same period (it would double-credit the whole month's payroll deduction) - IOS1/IOS2/Cash Deposit/
-- Refund of Over Deduction are ad-hoc, per-member events that legitimately recur within a period that
-- already has its main contribution posted. Existing batches predate this distinction, so they default
-- to true (still locking their period) rather than silently losing that protection.
ALTER TABLE monthly_contribution_batches ADD COLUMN locks_period BOOLEAN NOT NULL DEFAULT true;
