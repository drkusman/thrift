ALTER TABLE loan_types ADD COLUMN min_amount BIGINT;
ALTER TABLE loan_types ADD COLUMN max_amount BIGINT;

UPDATE loan_types SET min_amount = 150000, max_amount = 199000 WHERE code = 'EML';
UPDATE loan_types SET min_amount = 200000, max_amount = NULL WHERE code = 'MNL';
UPDATE loan_types SET min_amount = NULL, max_amount = 149999 WHERE code = 'PDL';
