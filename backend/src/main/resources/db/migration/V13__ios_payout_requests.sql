-- A member's request to be paid out their unpaid interest-on-savings (IOS1 credited, not yet reversed
-- by an IOS2 debit). The requested_amount is always server-computed at request time, never typed by
-- the member or the admin - see IosPayoutService.apply().
CREATE TABLE ios_payout_requests (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    requested_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_by BIGINT REFERENCES members(id),
    decided_at TIMESTAMP
);
