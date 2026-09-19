-- V2's fixed_score check is unnamed; preserve it while widening its column rather than dropping a guessed name.
ALTER TABLE daily_selections
    ALTER COLUMN fixed_score TYPE DOUBLE PRECISION USING fixed_score::DOUBLE PRECISION,
    ALTER COLUMN rule_version DROP DEFAULT;

ALTER TABLE daily_selections
    ADD CONSTRAINT ck_daily_fixed_score_finite_range CHECK (
        fixed_score IS NULL OR (fixed_score >= 0.0::DOUBLE PRECISION AND fixed_score <= 4.0::DOUBLE PRECISION)
    ),
    ADD CONSTRAINT ck_daily_fixed_score_positive_zero CHECK (
        fixed_score IS NULL OR fixed_score <> 0.0::DOUBLE PRECISION
        OR float8send(fixed_score) = decode('0000000000000000', 'hex')
    ),
    ADD CONSTRAINT ck_daily_v1_fixed_score_integral CHECK (
        rule_version <> 'atmosphere-v1' OR fixed_score IS NULL
        OR fixed_score IN (0.0::DOUBLE PRECISION, 1.0::DOUBLE PRECISION, 2.0::DOUBLE PRECISION,
                           3.0::DOUBLE PRECISION, 4.0::DOUBLE PRECISION)
    );

CREATE TABLE selection_cutoff_fence (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    sealed_through TIMESTAMPTZ
);
INSERT INTO selection_cutoff_fence(id) VALUES (1);

CREATE FUNCTION selection_config_versions_reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'selection configuration history is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_selection_config_versions_append_only
BEFORE UPDATE OR DELETE ON selection_config_versions
FOR EACH ROW EXECUTE FUNCTION selection_config_versions_reject_mutation();
