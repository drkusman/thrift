-- Product Loan is an open loan (from 100 and above, no upper cap) - not a bracket below Emergency
-- Loan's floor as V4 assumed.
UPDATE loan_types SET min_amount = 100, max_amount = NULL WHERE code = 'PDL';
