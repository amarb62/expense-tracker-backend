-- Core schema for Personal Finance & Expense Analytics backend.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name             VARCHAR(150) NOT NULL,
    institution      VARCHAR(150),
    account_type     VARCHAR(255) NOT NULL,
    last_four_digits VARCHAR(4),
    currency         VARCHAR(3) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT chk_accounts_account_type CHECK (account_type IN ('BANK_ACCOUNT', 'CREDIT_CARD', 'CASH', 'OTHER'))
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE categories (
    id            UUID PRIMARY KEY,
    parent_id     UUID REFERENCES categories (id) ON DELETE SET NULL,
    name          VARCHAR(100) NOT NULL,
    category_type VARCHAR(255) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_categories_name_parent UNIQUE (name, parent_id),
    CONSTRAINT chk_categories_category_type CHECK (category_type IN ('EXPENSE', 'INCOME', 'TRANSFER'))
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);

CREATE TABLE statements (
    id                    UUID PRIMARY KEY,
    account_id            UUID NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name             VARCHAR(255) NOT NULL,
    storage_key           VARCHAR(500) NOT NULL,
    statement_type        VARCHAR(255) NOT NULL,
    statement_start_date  DATE,
    statement_end_date    DATE,
    status                VARCHAR(255) NOT NULL,
    uploaded_at           TIMESTAMP NOT NULL,
    processed_at          TIMESTAMP,
    error_message         TEXT,
    CONSTRAINT chk_statements_statement_type CHECK (statement_type IN ('BANK_ACCOUNT', 'CREDIT_CARD', 'CASH', 'OTHER')),
    CONSTRAINT chk_statements_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_statements_account_id ON statements (account_id);
CREATE INDEX idx_statements_user_id ON statements (user_id);

CREATE TABLE transactions (
    id                    UUID PRIMARY KEY,
    account_id            UUID NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    statement_id          UUID REFERENCES statements (id) ON DELETE SET NULL,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id           UUID REFERENCES categories (id) ON DELETE SET NULL,
    transaction_date      DATE NOT NULL,
    description           TEXT NOT NULL,
    normalized_merchant   VARCHAR(255),
    amount                NUMERIC(19, 4) NOT NULL,
    transaction_type      VARCHAR(255) NOT NULL,
    source                VARCHAR(255) NOT NULL,
    confidence_score      NUMERIC(5, 4),
    transaction_hash      VARCHAR(128) NOT NULL,
    is_manually_added     BOOLEAN NOT NULL DEFAULT FALSE,
    is_category_modified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    CONSTRAINT uq_transactions_hash UNIQUE (transaction_hash),
    CONSTRAINT chk_transactions_transaction_type CHECK (transaction_type IN
        ('DEBIT', 'CREDIT', 'TRANSFER', 'REFUND', 'PAYMENT', 'FEE', 'INTEREST', 'CASH_WITHDRAWAL')),
    CONSTRAINT chk_transactions_source CHECK (source IN ('PDF', 'MANUAL', 'AI', 'RULE', 'SYSTEM'))
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_statement_id ON transactions (statement_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date);

CREATE TABLE user_category_rules (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    merchant_pattern VARCHAR(255) NOT NULL,
    category_id      UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    priority         INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_category_rules_user_pattern UNIQUE (user_id, merchant_pattern)
);

CREATE INDEX idx_user_category_rules_user_id ON user_category_rules (user_id);

CREATE TABLE ai_categorization (
    id                   UUID PRIMARY KEY,
    transaction_id       UUID NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    predicted_category_id UUID REFERENCES categories (id) ON DELETE SET NULL,
    model_name           VARCHAR(100),
    confidence           NUMERIC(5, 4),
    reasoning            VARCHAR(255),
    status               VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP NOT NULL,
    CONSTRAINT chk_ai_categorization_status CHECK (status IN
        ('AUTO_APPROVED', 'NEEDS_REVIEW', 'USER_APPROVED', 'USER_CORRECTED', 'FAILED'))
);

CREATE INDEX idx_ai_categorization_transaction_id ON ai_categorization (transaction_id);

CREATE TABLE monthly_summary (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    year               INTEGER NOT NULL,
    month              INTEGER NOT NULL,
    total_credited     NUMERIC(19, 4) NOT NULL,
    total_expenses     NUMERIC(19, 4) NOT NULL,
    remaining_amount   NUMERIC(19, 4) NOT NULL,
    expense_percentage NUMERIC(7, 4) NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,
    CONSTRAINT uq_monthly_summary_user_year_month UNIQUE (user_id, year, month)
);

CREATE TABLE category_monthly_summary (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id           UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    year                  INTEGER NOT NULL,
    month                 INTEGER NOT NULL,
    total_amount          NUMERIC(19, 4) NOT NULL,
    percentage_of_expense NUMERIC(7, 4) NOT NULL,
    percentage_of_credit  NUMERIC(7, 4) NOT NULL,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    CONSTRAINT uq_category_monthly_summary_user_year_month_category UNIQUE (user_id, year, month, category_id)
);

CREATE INDEX idx_category_monthly_summary_user_year_month ON category_monthly_summary (user_id, year, month);
