-- A member withdrawn with a zero-or-negative net balance has nothing to pay out and no COT to charge -
-- Karim's own rule: just close their account, don't post either ledger entry. These columns are now only
-- set when a posting actually happened.
ALTER TABLE membership_withdrawals ALTER COLUMN cot_ledger_entry_id DROP NOT NULL;
ALTER TABLE membership_withdrawals ALTER COLUMN payout_ledger_entry_id DROP NOT NULL;
