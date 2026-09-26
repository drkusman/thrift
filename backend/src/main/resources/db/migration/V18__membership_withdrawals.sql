-- A membership withdrawal pays a member out and closes their account for good - see
-- MembershipWithdrawalService. membership_withdrawals is the audit record (mirrors loan_liquidations),
-- membership_withdrawal_requests is the member's own self-service request awaiting admin approval
-- (mirrors loan_liquidation_requests).
CREATE TABLE membership_withdrawals (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    total_savings BIGINT NOT NULL,
    total_loan BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    cot BIGINT NOT NULL,
    withdrawable_amount BIGINT NOT NULL,
    cot_ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    payout_ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    performed_by BIGINT NOT NULL REFERENCES members(id),
    performed_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE membership_withdrawal_requests (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_by BIGINT REFERENCES members(id),
    decided_at TIMESTAMP,
    decision_note TEXT,
    membership_withdrawal_id BIGINT REFERENCES membership_withdrawals(id)
);
