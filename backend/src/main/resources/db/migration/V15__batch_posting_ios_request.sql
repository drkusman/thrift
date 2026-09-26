-- When an uploaded IOS2 row auto-resolves a pending IosPayoutRequest (see IosPayoutService), this
-- tracks which one - so deleting the batch can revert that request back to PENDING instead of leaving
-- it stuck as PAID for a debit that no longer exists.
ALTER TABLE monthly_contribution_batch_row_postings ADD COLUMN ios_payout_request_id BIGINT REFERENCES ios_payout_requests(id);
