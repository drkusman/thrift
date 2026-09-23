CREATE TABLE banks (
    id          BIGSERIAL PRIMARY KEY,
    bank_code   VARCHAR(10) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    sort_code   VARCHAR(20)
);

CREATE TABLE members (
    id                      BIGSERIAL PRIMARY KEY,
    regno                   VARCHAR(20) NOT NULL UNIQUE,
    full_name               VARCHAR(255) NOT NULL,
    phone                   VARCHAR(50),
    email                   VARCHAR(255),
    sex                     VARCHAR(10),
    dept_code               VARCHAR(50),
    fact_code                VARCHAR(50),
    pay_point               VARCHAR(50),
    bank_id                 BIGINT REFERENCES banks(id),
    account_no              VARCHAR(50),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    monthly_savings_amount  BIGINT NOT NULL DEFAULT 0,
    role                    VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    password_hash           VARCHAR(255) NOT NULL,
    must_change_password    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    last_seen_at            TIMESTAMP
);

CREATE TABLE loan_types (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(10) NOT NULL UNIQUE,
    name                    VARCHAR(255) NOT NULL,
    interest_rate           DOUBLE PRECISION NOT NULL,
    interest_method         VARCHAR(20) NOT NULL,
    max_duration_months     INT NOT NULL
);

CREATE TABLE loans (
    id                      BIGSERIAL PRIMARY KEY,
    loan_code               VARCHAR(50) UNIQUE,
    member_id               BIGINT NOT NULL REFERENCES members(id),
    loan_type_id            BIGINT NOT NULL REFERENCES loan_types(id),
    requested_amount        BIGINT NOT NULL,
    reason                  TEXT,
    duration_months         INT,
    interest_amount         BIGINT,
    disbursed_amount        BIGINT,
    total_repayable         BIGINT,
    monthly_repayment_amount BIGINT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_at              TIMESTAMP NOT NULL DEFAULT now(),
    decided_by              BIGINT REFERENCES members(id),
    decided_at              TIMESTAMP,
    decision_note           TEXT,
    disbursed_at            TIMESTAMP
);

CREATE TABLE loan_repayment_schedules (
    id                  BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT NOT NULL REFERENCES loans(id),
    installment_no      INT NOT NULL,
    due_date            DATE NOT NULL,
    amount_due          BIGINT NOT NULL,
    amount_paid         BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at             TIMESTAMP
);

CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    trans_code      VARCHAR(100) UNIQUE,
    member_id       BIGINT NOT NULL REFERENCES members(id),
    amount          BIGINT NOT NULL,
    date            DATE NOT NULL,
    description     TEXT,
    trans_type      VARCHAR(255),
    trans_cat       VARCHAR(20) NOT NULL,
    dr_cr_status    VARCHAR(2) NOT NULL,
    loan_id         BIGINT REFERENCES loans(id),
    source          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES members(id)
);

CREATE TABLE monthly_contribution_batches (
    id              BIGSERIAL PRIMARY KEY,
    period_month    VARCHAR(10) NOT NULL,
    file_name       VARCHAR(255),
    uploaded_by     BIGINT NOT NULL REFERENCES members(id),
    uploaded_at     TIMESTAMP NOT NULL DEFAULT now(),
    total_rows      INT NOT NULL DEFAULT 0,
    matched_rows    INT NOT NULL DEFAULT 0,
    total_amount    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE monthly_contribution_batch_rows (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        BIGINT NOT NULL REFERENCES monthly_contribution_batches(id),
    regno           VARCHAR(20) NOT NULL,
    amount          BIGINT NOT NULL,
    kind            VARCHAR(20),
    matched         BOOLEAN NOT NULL DEFAULT FALSE,
    ledger_entry_id BIGINT REFERENCES ledger_entries(id),
    error_message   TEXT
);

CREATE TABLE savings_amount_change_requests (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT NOT NULL REFERENCES members(id),
    requested_amount    BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at        TIMESTAMP NOT NULL DEFAULT now(),
    decided_by          BIGINT REFERENCES members(id),
    decided_at          TIMESTAMP
);

CREATE INDEX idx_ledger_entries_member_id ON ledger_entries(member_id);
CREATE INDEX idx_ledger_entries_loan_id ON ledger_entries(loan_id);
CREATE INDEX idx_loans_member_id ON loans(member_id);
CREATE INDEX idx_loan_repayment_schedules_loan_id ON loan_repayment_schedules(loan_id);
CREATE INDEX idx_monthly_contribution_batch_rows_batch_id ON monthly_contribution_batch_rows(batch_id);
CREATE INDEX idx_savings_amount_change_requests_member_id ON savings_amount_change_requests(member_id);
