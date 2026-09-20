-- Before this migration the public account contract made the mailbox immutable.
-- Preserve that original location in every historical preference snapshot. Refuse
-- inconsistent legacy data instead of inventing a location or a candidate epoch.
LOCK TABLE app_users, user_preference_versions IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_preference_versions p
        JOIN app_users u ON u.id = p.user_id
        WHERE u.mailbox_lat IS NULL
           OR u.mailbox_lng IS NULL
           OR u.mailbox_enabled_at IS NULL
           OR u.mailbox_enabled_at > p.effective_at
    ) THEN
        RAISE EXCEPTION 'preference mailbox history cannot be reconstructed from immutable legacy mailboxes';
    END IF;
END;
$$;

ALTER TABLE user_preference_versions
    ADD COLUMN mailbox_lat DOUBLE PRECISION,
    ADD COLUMN mailbox_lng DOUBLE PRECISION,
    ADD COLUMN mailbox_enabled_at TIMESTAMPTZ;

UPDATE user_preference_versions p
SET mailbox_lat = u.mailbox_lat,
    mailbox_lng = u.mailbox_lng,
    mailbox_enabled_at = u.mailbox_enabled_at
FROM app_users u
WHERE u.id = p.user_id;

ALTER TABLE user_preference_versions
    ALTER COLUMN mailbox_lat SET NOT NULL,
    ALTER COLUMN mailbox_lng SET NOT NULL,
    ALTER COLUMN mailbox_enabled_at SET NOT NULL,
    ADD CONSTRAINT ck_preference_mailbox_coordinates CHECK (
        mailbox_lat BETWEEN -90.0 AND 90.0
        AND mailbox_lng BETWEEN -180.0 AND 180.0
    ),
    ADD CONSTRAINT ck_preference_mailbox_epoch CHECK (mailbox_enabled_at <= effective_at);
