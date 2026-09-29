CREATE TABLE accounts (
    id TEXT PRIMARY KEY,
    identifier_kind TEXT NOT NULL,
    identifier_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_accounts_identifier UNIQUE (identifier_kind, identifier_value)
);

CREATE TABLE account_profiles (
    account_id TEXT NOT NULL REFERENCES accounts (id),
    profile_kind TEXT NOT NULL,
    PRIMARY KEY (account_id, profile_kind)
);

CREATE TABLE otp_codes (
    id TEXT PRIMARY KEY,
    identifier_kind TEXT NOT NULL,
    identifier_value TEXT NOT NULL,
    code_hash TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    consumed BOOLEAN NOT NULL,
    requester_ip TEXT NOT NULL
);

CREATE INDEX idx_otp_codes_identifier_issued_at ON otp_codes (identifier_kind, identifier_value, issued_at DESC);

CREATE INDEX idx_otp_codes_ip_issued_at ON otp_codes (requester_ip, issued_at);
