-- Preserve existing v1 axis values and row identities; only new writes default to continuous v2.
ALTER TABLE user_preference_versions
    DROP CONSTRAINT user_preference_versions_crowd_level_check,
    DROP CONSTRAINT user_preference_versions_spatial_feel_check,
    DROP CONSTRAINT user_preference_versions_company_fit_check,
    DROP CONSTRAINT user_preference_versions_stay_style_check,
    DROP CONSTRAINT user_preference_versions_axis_definition_version_check,
    ALTER COLUMN crowd_level TYPE DOUBLE PRECISION USING crowd_level::DOUBLE PRECISION,
    ALTER COLUMN spatial_feel TYPE DOUBLE PRECISION USING spatial_feel::DOUBLE PRECISION,
    ALTER COLUMN company_fit TYPE DOUBLE PRECISION USING company_fit::DOUBLE PRECISION,
    ALTER COLUMN stay_style TYPE DOUBLE PRECISION USING stay_style::DOUBLE PRECISION,
    ALTER COLUMN axis_definition_version SET DEFAULT 2,
    ADD CONSTRAINT user_preference_versions_crowd_level_check CHECK (crowd_level BETWEEN -1 AND 1),
    ADD CONSTRAINT user_preference_versions_spatial_feel_check CHECK (spatial_feel BETWEEN -1 AND 1),
    ADD CONSTRAINT user_preference_versions_company_fit_check CHECK (company_fit BETWEEN -1 AND 1),
    ADD CONSTRAINT user_preference_versions_stay_style_check CHECK (stay_style BETWEEN -1 AND 1),
    ADD CONSTRAINT user_preference_versions_axis_definition_version_check CHECK (
        axis_definition_version = 2 OR (
            axis_definition_version = 1
            AND crowd_level IN (-1, 1) AND spatial_feel IN (-1, 1)
            AND company_fit IN (-1, 1) AND stay_style IN (-1, 1)
        )
    );

ALTER TABLE memories
    DROP CONSTRAINT memories_crowd_level_check,
    DROP CONSTRAINT memories_spatial_feel_check,
    DROP CONSTRAINT memories_company_fit_check,
    DROP CONSTRAINT memories_stay_style_check,
    DROP CONSTRAINT memories_axis_definition_version_check,
    ALTER COLUMN crowd_level TYPE DOUBLE PRECISION USING crowd_level::DOUBLE PRECISION,
    ALTER COLUMN spatial_feel TYPE DOUBLE PRECISION USING spatial_feel::DOUBLE PRECISION,
    ALTER COLUMN company_fit TYPE DOUBLE PRECISION USING company_fit::DOUBLE PRECISION,
    ALTER COLUMN stay_style TYPE DOUBLE PRECISION USING stay_style::DOUBLE PRECISION,
    ALTER COLUMN axis_definition_version SET DEFAULT 2,
    ADD CONSTRAINT memories_crowd_level_check CHECK (crowd_level BETWEEN -1 AND 1),
    ADD CONSTRAINT memories_spatial_feel_check CHECK (spatial_feel BETWEEN -1 AND 1),
    ADD CONSTRAINT memories_company_fit_check CHECK (company_fit BETWEEN -1 AND 1),
    ADD CONSTRAINT memories_stay_style_check CHECK (stay_style BETWEEN -1 AND 1),
    ADD CONSTRAINT memories_axis_definition_version_check CHECK (
        axis_definition_version = 2 OR (
            axis_definition_version = 1
            AND crowd_level IN (-1, 1) AND spatial_feel IN (-1, 1)
            AND company_fit IN (-1, 1) AND stay_style IN (-1, 1)
        )
    );
