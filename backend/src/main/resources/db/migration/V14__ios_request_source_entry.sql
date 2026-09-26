-- A request now claims one specific unpaid IOS1 credit rather than a blended lump sum - lets a member
-- apply for, say, 2024's interest separately from 2025's, instead of one combined amount.
ALTER TABLE ios_payout_requests ADD COLUMN source_ledger_entry_id BIGINT REFERENCES ledger_entries(id);
