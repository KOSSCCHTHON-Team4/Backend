-- Immutable selection-policy history; this migration deliberately seeds no operating configuration.
CREATE TABLE selection_config_versions (
    revision BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_version TEXT NOT NULL UNIQUE CHECK (btrim(config_version) <> ''),
    effective_at TIMESTAMPTZ NOT NULL,
    nearby_radius_meters INTEGER NOT NULL CHECK (nearby_radius_meters > 0),
    rule_version TEXT NOT NULL CHECK (btrim(rule_version) <> '')
);

CREATE INDEX idx_selection_config_effective_revision
    ON selection_config_versions(effective_at DESC, revision DESC);
