ALTER TABLE loan_types ADD COLUMN max_concurrent_active INT;

UPDATE loan_types SET max_concurrent_active = 2 WHERE code = 'EML';
