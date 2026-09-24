ALTER TABLE loans RENAME COLUMN referee_one_id TO guarantor_one_id;
ALTER TABLE loans RENAME COLUMN referee_two_id TO guarantor_two_id;

ALTER TABLE loans
    ADD COLUMN guarantor_one_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN guarantor_two_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Loans that are already past the pending/approval stage (disbursed, running, pulsed, completed, or
-- legacy loans with no guarantors at all) predate this requirement entirely - treat them as settled
-- rather than leaving a misleading "awaiting guarantor" flag on loans nobody can act on any more.
UPDATE loans
SET guarantor_one_status = 'ACCEPTED', guarantor_two_status = 'ACCEPTED'
WHERE status <> 'PENDING';
