-- markPaid() now posts the offsetting IOS2 ledger debit itself, atomically with the status change,
-- instead of relying on a separate Monthly Upload to do it later - a request could otherwise sit marked
-- PAID with no matching debit ever posted, which let the same credit be re-applied for. This column
-- records which ledger entry that was, for the same audit-trail reason MembershipWithdrawal keeps its
-- own ledger entry ids.
ALTER TABLE ios_payout_requests ADD COLUMN paid_ledger_entry_id BIGINT REFERENCES ledger_entries(id);
