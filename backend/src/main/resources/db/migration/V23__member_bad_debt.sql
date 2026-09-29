ALTER TABLE members ADD COLUMN bad_debt BOOLEAN NOT NULL DEFAULT FALSE;

-- Tracks a member who withdrew from the cooperative with a negative savings balance (every loan is
-- already required to be liquidated to zero before a withdrawal is allowed, so a negative balance here
-- means the SAVINGS ledger itself nets negative, not an unpaid loan). original_amount is a snapshot of
-- what they owed at withdrawal time, purely for record-keeping - the actual amount still owed is always
-- read live from the ledger (see BadDebtService), since repayments post as ordinary ledger entries.
CREATE TABLE bad_debts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    membership_withdrawal_id BIGINT REFERENCES membership_withdrawals(id),
    original_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OUTSTANDING',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    cleared_at TIMESTAMP
);
CREATE INDEX idx_bad_debts_member_id ON bad_debts(member_id);
CREATE INDEX idx_bad_debts_status ON bad_debts(status);
