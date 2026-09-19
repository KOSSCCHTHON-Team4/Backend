-- Persistent coordination records; callers supply all timestamps and lease durations.
-- email_key_hash is lowercase SHA-256 hex of UTF-8('emotionmap:login-attempt:v1:' + strip+Locale.ROOT-normalized email).
CREATE TABLE login_attempt_limits (
    email_key_hash TEXT PRIMARY KEY CHECK (email_key_hash ~ '^[0-9a-f]{64}$'),
    window_started_at TIMESTAMPTZ,
    failure_count INTEGER NOT NULL CHECK (failure_count >= 0),
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_login_attempt_window CHECK (
        (failure_count = 0 AND window_started_at IS NULL AND locked_until IS NULL)
        OR (failure_count > 0 AND window_started_at IS NOT NULL)
    )
);

CREATE TABLE api_idempotency_records (
    actor_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    request_method TEXT NOT NULL CHECK (request_method = 'POST'),
    request_path TEXT NOT NULL CHECK (request_path IN ('/v1/memories', '/v1/images', '/v1/reports')),
    idempotency_key UUID NOT NULL,
    fingerprint_version INTEGER NOT NULL CHECK (fingerprint_version > 0),
    request_fingerprint BYTEA NOT NULL CHECK (octet_length(request_fingerprint) = 32),
    status TEXT NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED')),
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL CHECK (attempt_count > 0),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    memory_id UUID REFERENCES memories(id) ON DELETE RESTRICT,
    image_upload_id UUID REFERENCES image_uploads(id) ON DELETE RESTRICT,
    report_id UUID REFERENCES reports(id) ON DELETE RESTRICT,
    PRIMARY KEY (actor_id, request_method, request_path, idempotency_key),
    CONSTRAINT ck_idempotency_state CHECK (
        (status = 'PROCESSING'
            AND claim_token IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND completed_at IS NULL
            AND memory_id IS NULL
            AND image_upload_id IS NULL
            AND report_id IS NULL)
        OR
        (status = 'COMPLETED'
            AND claim_token IS NULL
            AND lease_expires_at IS NULL
            AND completed_at IS NOT NULL
            AND (
                (request_path = '/v1/memories' AND memory_id IS NOT NULL AND image_upload_id IS NULL AND report_id IS NULL)
                OR (request_path = '/v1/images' AND memory_id IS NULL AND image_upload_id IS NOT NULL AND report_id IS NULL)
                OR (request_path = '/v1/reports' AND memory_id IS NULL AND image_upload_id IS NULL AND report_id IS NOT NULL)
            ))
    )
);

-- Completed records are retained; a future explicit retention policy may add deletion separately.
CREATE INDEX idx_idempotency_processing_lease
    ON api_idempotency_records(lease_expires_at)
    WHERE status = 'PROCESSING';
