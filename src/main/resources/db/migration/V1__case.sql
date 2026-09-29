CREATE TABLE cases (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL,
    state TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    described_at TIMESTAMPTZ
);

CREATE INDEX idx_cases_owner_created_at_id ON cases (owner, created_at, id);

CREATE TABLE case_photos (
    case_id TEXT NOT NULL REFERENCES cases (id),
    slot INTEGER NOT NULL,
    photo_id TEXT NOT NULL,
    PRIMARY KEY (case_id, slot)
);

CREATE TABLE case_intakes (
    owner TEXT NOT NULL,
    intake_key TEXT NOT NULL,
    case_id TEXT NOT NULL REFERENCES cases (id) DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (owner, intake_key)
);
