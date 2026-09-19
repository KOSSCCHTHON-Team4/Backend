-- ERD 1.0 + API 0.2 email/password credential override.
-- V1 is immutable. This cutover supports EMPTY legacy tables only: missing
-- atmosphere/preferences/delivery history cannot be invented for old records.
-- Flyway runs this PostgreSQL migration transactionally; lock before checking.
LOCK TABLE app_user, place, memory, reaction, letter_delivery, report IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_user)
       OR EXISTS (SELECT 1 FROM place)
       OR EXISTS (SELECT 1 FROM memory)
       OR EXISTS (SELECT 1 FROM reaction)
       OR EXISTS (SELECT 1 FROM letter_delivery)
       OR EXISTS (SELECT 1 FROM report) THEN
        RAISE EXCEPTION 'ERD cutover requires empty legacy tables. Data was not deleted; use a separate empty development database or an explicitly reviewed data migration.';
    END IF;
END $$;

DROP TABLE report, reaction, letter_delivery, memory, place, app_user;

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname TEXT,
    access_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (access_status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    app_role TEXT NOT NULL DEFAULT 'USER' CHECK (app_role IN ('USER', 'OPERATOR')),
    mailbox_lat DOUBLE PRECISION,
    mailbox_lng DOUBLE PRECISION,
    mailbox_enabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_user_mailbox CHECK (
        (mailbox_lat IS NULL AND mailbox_lng IS NULL AND mailbox_enabled_at IS NULL)
        OR (mailbox_lat IS NOT NULL AND mailbox_lng IS NOT NULL AND mailbox_enabled_at IS NOT NULL
            AND mailbox_lat BETWEEN -90 AND 90 AND mailbox_lng BETWEEN -180 AND 180))
);

CREATE TABLE email_password_credentials (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE RESTRICT,
    email TEXT NOT NULL CHECK (btrim(email) <> ''),
    email_lookup_key TEXT NOT NULL UNIQUE CHECK (btrim(email_lookup_key) <> ''),
    password_hash TEXT NOT NULL CHECK (btrim(password_hash) <> ''),
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE user_preference_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    revision BIGINT NOT NULL CHECK (revision > 0),
    effective_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    crowd_level SMALLINT NOT NULL CHECK (crowd_level IN (-1, 1)),
    spatial_feel SMALLINT NOT NULL CHECK (spatial_feel IN (-1, 1)),
    company_fit SMALLINT NOT NULL CHECK (company_fit IN (-1, 1)),
    stay_style SMALLINT NOT NULL CHECK (stay_style IN (-1, 1)),
    description TEXT CHECK (description IS NULL OR btrim(description) <> ''),
    axis_definition_version SMALLINT NOT NULL DEFAULT 1 CHECK (axis_definition_version = 1),
    UNIQUE (user_id, revision),
    UNIQUE (id, user_id)
);
CREATE INDEX idx_preference_cutoff ON user_preference_versions(user_id, effective_at DESC, revision DESC);

CREATE TABLE places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label TEXT,
    lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_places_location ON places(lat, lng);

CREATE TABLE place_categories (
    id SMALLINT PRIMARY KEY CHECK (id BETWEEN 1 AND 8),
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    definition TEXT NOT NULL,
    sort_order SMALLINT NOT NULL UNIQUE CHECK (sort_order BETWEEN 1 AND 8),
    taxonomy_version SMALLINT NOT NULL DEFAULT 1 CHECK (taxonomy_version = 1)
);

CREATE TABLE memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE RESTRICT,
    distribution_type TEXT NOT NULL CHECK (distribution_type IN ('LETTER', 'PRIVATE')),
    origin_kind TEXT NOT NULL CHECK (origin_kind IN ('DIRECT', 'LETTER_COPY')),
    data_origin TEXT NOT NULL CHECK (data_origin IN ('PARTICIPANT', 'TEAM_TEST', 'SYNTHETIC')),
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    place_label_snapshot TEXT,
    place_lat DOUBLE PRECISION NOT NULL CHECK (place_lat BETWEEN -90 AND 90),
    place_lng DOUBLE PRECISION NOT NULL CHECK (place_lng BETWEEN -180 AND 180),
    crowd_level SMALLINT NOT NULL CHECK (crowd_level IN (-1, 1)),
    spatial_feel SMALLINT NOT NULL CHECK (spatial_feel IN (-1, 1)),
    company_fit SMALLINT NOT NULL CHECK (company_fit IN (-1, 1)),
    stay_style SMALLINT NOT NULL CHECK (stay_style IN (-1, 1)),
    crowd_source TEXT NOT NULL CHECK (crowd_source IN ('AI', 'USER')),
    spatial_source TEXT NOT NULL CHECK (spatial_source IN ('AI', 'USER')),
    company_source TEXT NOT NULL CHECK (company_source IN ('AI', 'USER')),
    stay_source TEXT NOT NULL CHECK (stay_source IN ('AI', 'USER')),
    axis_definition_version SMALLINT NOT NULL DEFAULT 1 CHECK (axis_definition_version = 1),
    atmosphere_analysis_status TEXT NOT NULL CHECK (atmosphere_analysis_status IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'NOT_RUN')),
    category_analysis_status TEXT NOT NULL CHECK (category_analysis_status IN ('CLASSIFIED', 'UNCLASSIFIED', 'FAILED', 'NOT_RUN')),
    analysis_model TEXT,
    analysis_prompt_version TEXT,
    image_path TEXT UNIQUE,
    image_media_type TEXT,
    image_size_bytes BIGINT,
    content_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (content_status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    moderation_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REVIEW_REQUIRED', 'REJECTED', 'ERROR', 'NOT_REQUIRED')),
    available_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_memory_copy_private CHECK (origin_kind <> 'LETTER_COPY' OR distribution_type = 'PRIVATE'),
    CONSTRAINT ck_memory_image CHECK (
        (image_path IS NULL AND image_media_type IS NULL AND image_size_bytes IS NULL)
        OR (image_path IS NOT NULL AND btrim(image_path) <> ''
            AND image_media_type IS NOT NULL AND image_media_type IN ('image/jpeg', 'image/png')
            AND image_size_bytes IS NOT NULL AND image_size_bytes > 0)),
    CONSTRAINT ck_memory_deleted CHECK ((content_status = 'DELETED') = (deleted_at IS NOT NULL)),
    CONSTRAINT ck_letter_moderation CHECK (distribution_type <> 'LETTER' OR moderation_status <> 'NOT_REQUIRED'),
    CONSTRAINT ck_memory_available CHECK (
        (distribution_type = 'PRIVATE' AND available_at IS NULL)
        OR (distribution_type = 'LETTER' AND (available_at IS NULL OR available_at >= created_at)))
);
CREATE INDEX idx_memories_owner ON memories(owner_id, created_at DESC, id DESC);
CREATE INDEX idx_memories_private ON memories(owner_id, created_at DESC, id DESC)
    WHERE distribution_type = 'PRIVATE' AND content_status = 'ACTIVE';
CREATE INDEX idx_memories_place ON memories(place_id, created_at DESC, id DESC);
CREATE INDEX idx_memories_candidates ON memories(available_at, id)
    WHERE distribution_type = 'LETTER' AND content_status = 'ACTIVE' AND moderation_status = 'APPROVED';

CREATE TABLE memory_categories (
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE RESTRICT,
    category_id SMALLINT NOT NULL REFERENCES place_categories(id) ON DELETE RESTRICT,
    slot_no SMALLINT NOT NULL CHECK (slot_no BETWEEN 1 AND 3),
    assignment_source TEXT NOT NULL CHECK (assignment_source IN ('AI', 'USER')),
    label_snapshot TEXT NOT NULL,
    taxonomy_version SMALLINT NOT NULL DEFAULT 1 CHECK (taxonomy_version = 1),
    PRIMARY KEY (memory_id, category_id),
    UNIQUE (memory_id, slot_no)
);
CREATE INDEX idx_memory_categories_category ON memory_categories(category_id, memory_id);

CREATE TABLE daily_selections (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    service_date DATE NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    preference_version_id UUID NOT NULL,
    radius_m INTEGER NOT NULL CHECK (radius_m > 0),
    rule_version TEXT NOT NULL DEFAULT 'atmosphere-v1',
    random_seed UUID NOT NULL DEFAULT gen_random_uuid(),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'NO_CANDIDATE', 'RETRYABLE_ERROR', 'EXPIRED_ERROR', 'SKIPPED_ACCESS')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_error_code TEXT,
    candidate_count INTEGER CHECK (candidate_count >= 0),
    top_tie_count INTEGER CHECK (top_tie_count >= 0),
    fixed_score SMALLINT CHECK (fixed_score BETWEEN 0 AND 4),
    tie_break_method TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (user_id, service_date),
    FOREIGN KEY (preference_version_id, user_id) REFERENCES user_preference_versions(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_daily_cutoff CHECK (cutoff_at = (service_date + TIME '09:00') AT TIME ZONE 'Asia/Seoul'),
    CONSTRAINT ck_daily_claim CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT ck_daily_completed CHECK (
        (status IN ('DELIVERED', 'NO_CANDIDATE', 'EXPIRED_ERROR', 'SKIPPED_ACCESS')) = (completed_at IS NOT NULL)),
    CONSTRAINT ck_daily_counts CHECK (top_tie_count IS NULL OR candidate_count IS NULL OR top_tie_count <= candidate_count)
);
CREATE INDEX idx_daily_preference ON daily_selections(preference_version_id, user_id);
CREATE INDEX idx_daily_recovery ON daily_selections(service_date, status, lease_expires_at);

CREATE TABLE letter_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receiver_id UUID NOT NULL,
    service_date DATE NOT NULL,
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE RESTRICT,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    read_at TIMESTAMPTZ,
    liked_at TIMESTAMPTZ,
    FOREIGN KEY (receiver_id, service_date) REFERENCES daily_selections(user_id, service_date) ON DELETE RESTRICT,
    UNIQUE (receiver_id, service_date),
    UNIQUE (receiver_id, memory_id),
    CONSTRAINT ck_delivery_date CHECK ((delivered_at AT TIME ZONE 'Asia/Seoul')::DATE = service_date
        AND delivered_at >= (service_date + TIME '09:00') AT TIME ZONE 'Asia/Seoul'),
    CONSTRAINT ck_delivery_read CHECK (read_at IS NULL OR read_at >= delivered_at),
    CONSTRAINT ck_delivery_like CHECK (liked_at IS NULL OR liked_at >= delivered_at)
);
CREATE INDEX idx_deliveries_inbox ON letter_deliveries(receiver_id, delivered_at DESC, id DESC);
CREATE INDEX idx_deliveries_memory ON letter_deliveries(memory_id);
CREATE INDEX idx_deliveries_likes ON letter_deliveries(memory_id) WHERE liked_at IS NOT NULL;

CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL_CONTENT', 'VIOLENCE_OR_DANGEROUS_CONTENT', 'PERSONAL_INFORMATION', 'COPYRIGHT', 'OTHER')),
    details TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    handled_by UUID REFERENCES app_users(id) ON DELETE RESTRICT,
    handling_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    handled_at TIMESTAMPTZ,
    CONSTRAINT ck_report_resolution CHECK (
        (status IN ('RESOLVED', 'DISMISSED') AND handled_by IS NOT NULL AND handled_at IS NOT NULL)
        OR (status IN ('OPEN', 'IN_REVIEW') AND handled_at IS NULL))
);
CREATE INDEX idx_reports_queue ON reports(status, created_at, id);
CREATE INDEX idx_reports_reporter ON reports(reporter_id);
CREATE INDEX idx_reports_memory ON reports(memory_id);
CREATE INDEX idx_reports_handler ON reports(handled_by);

-- API upload ownership/lifecycle supplement; no original-to-copy relation.
CREATE TABLE image_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    storage_path TEXT NOT NULL UNIQUE,
    media_type TEXT NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    status TEXT NOT NULL DEFAULT 'STAGED' CHECK (status IN ('STAGED', 'ATTACHED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    attached_memory_id UUID UNIQUE REFERENCES memories(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_upload_attachment CHECK ((status = 'ATTACHED') = (attached_memory_id IS NOT NULL))
);
CREATE INDEX idx_upload_owner ON image_uploads(owner_id);
CREATE INDEX idx_upload_expiry ON image_uploads(expires_at) WHERE status = 'STAGED';
