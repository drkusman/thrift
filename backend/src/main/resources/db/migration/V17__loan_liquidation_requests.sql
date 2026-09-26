-- A member's self-service request to liquidate their own loan, subject to admin approval - approving one
-- runs the exact same LoanLiquidationService.liquidate() an admin-initiated liquidation does (re-validating
-- balance/savings fresh at approval time), and links back to the resulting audit record via
-- loan_liquidation_id so the whole trail - request, decision, postings - can be traced from either end.
CREATE TABLE loan_liquidation_requests (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    requested_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_by BIGINT REFERENCES members(id),
    decided_at TIMESTAMP,
    decision_note TEXT,
    loan_liquidation_id BIGINT REFERENCES loan_liquidations(id)
);
