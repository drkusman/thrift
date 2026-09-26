-- Audit trail for LoanLiquidationService.liquidate() - links the three ledger entries a liquidation posts
-- together (savings debit, admin fee debit, loan credit) so the full picture traces back from any one.
CREATE TABLE loan_liquidations (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    amount BIGINT NOT NULL,
    admin_fee BIGINT NOT NULL,
    full_liquidation BOOLEAN NOT NULL,
    old_monthly_repayment_amount BIGINT,
    new_monthly_repayment_amount BIGINT,
    savings_ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    fee_ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    loan_ledger_entry_id BIGINT NOT NULL REFERENCES ledger_entries(id),
    performed_by BIGINT NOT NULL REFERENCES members(id),
    performed_at TIMESTAMP NOT NULL DEFAULT now()
);
