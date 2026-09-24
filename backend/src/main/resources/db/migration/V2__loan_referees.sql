ALTER TABLE loans
    ADD COLUMN referee_one_id BIGINT REFERENCES members(id),
    ADD COLUMN referee_two_id BIGINT REFERENCES members(id);
