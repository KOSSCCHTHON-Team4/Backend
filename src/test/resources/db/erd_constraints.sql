-- Run only against a dedicated test database AFTER all Flyway migrations.
-- psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/test/resources/db/erd_constraints.sql
-- Synthetic fixtures are rolled back; nothing is inserted into real user data.
BEGIN;
CREATE FUNCTION pg_temp.assert_rejected(statement TEXT, expected_state TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE = expected_state THEN RETURN; END IF;
        RAISE;
    END;
    RAISE EXCEPTION 'Expected SQLSTATE %, but statement succeeded: %', expected_state, statement;
END $$;

INSERT INTO app_users(id, access_status) VALUES
    ('10000000-0000-4000-8000-000000000001', 'ACTIVE'),
    ('10000000-0000-4000-8000-000000000002', 'ACTIVE');
INSERT INTO user_preference_versions(id,user_id,revision,effective_at,crowd_level,spatial_feel,company_fit,stay_style,axis_definition_version) VALUES
    ('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001',1,'2026-09-18T00:00:00Z',-1,1,-1,1,1),
    ('20000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002',1,'2026-09-18T00:00:00Z',1,-1,1,-1,2);
INSERT INTO places(id,label,lat,lng) VALUES ('30000000-0000-4000-8000-000000000001','Synthetic pin',37.5,127.0);
INSERT INTO memories(id,owner_id,place_id,distribution_type,origin_kind,data_origin,content,place_lat,place_lng,
    crowd_level,spatial_feel,company_fit,stay_style,crowd_source,spatial_source,company_source,stay_source,
    axis_definition_version,atmosphere_analysis_status,category_analysis_status,moderation_status,created_at,available_at)
SELECT id::UUID,'10000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000001',
    'LETTER','DIRECT','SYNTHETIC','Synthetic experience',37.5,127.0,-1,1,-1,1,'USER','USER','USER','USER',
    CASE WHEN id = '40000000-0000-4000-8000-000000000001' THEN 1 ELSE 2 END,
    'NOT_RUN','NOT_RUN','APPROVED','2026-09-18T00:00:00Z','2026-09-18T00:01:00Z'
FROM (VALUES ('40000000-0000-4000-8000-000000000001'),('40000000-0000-4000-8000-000000000002')) AS fixtures(id);

-- v1 rows stay endpoint-only; new v2 rows accept any finite binary64 value in range, including zero.
SELECT pg_temp.assert_rejected($q$UPDATE memories SET crowd_level=0 WHERE id='40000000-0000-4000-8000-000000000001'$q$,'23514');
UPDATE memories SET crowd_level=0.25, spatial_feel=0 WHERE id='40000000-0000-4000-8000-000000000002';
SELECT pg_temp.assert_rejected($q$UPDATE memories SET crowd_level=1.0000000000000002 WHERE id='40000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET crowd_level='NaN'::double precision WHERE id='40000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET crowd_level='Infinity'::double precision WHERE id='40000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET crowd_level='-Infinity'::double precision WHERE id='40000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET axis_definition_version=3 WHERE id='40000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE user_preference_versions SET crowd_level=0 WHERE id='20000000-0000-4000-8000-000000000001'$q$,'23514');
UPDATE user_preference_versions SET crowd_level=0.25, spatial_feel=0 WHERE id='20000000-0000-4000-8000-000000000002';
SELECT pg_temp.assert_rejected($q$UPDATE user_preference_versions SET crowd_level='NaN'::double precision WHERE id='20000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE user_preference_versions SET crowd_level='Infinity'::double precision WHERE id='20000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE user_preference_versions SET axis_definition_version=3 WHERE id='20000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET origin_kind='LETTER_COPY' WHERE id='40000000-0000-4000-8000-000000000001'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE memories SET image_path='orphan.png' WHERE id='40000000-0000-4000-8000-000000000001'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE app_users SET mailbox_lat=37.5 WHERE id='10000000-0000-4000-8000-000000000001'$q$,'23514');

INSERT INTO memory_categories(memory_id,category_id,slot_no,assignment_source,label_snapshot)
SELECT '40000000-0000-4000-8000-000000000001',id,id,'USER',label FROM place_categories WHERE id BETWEEN 1 AND 3;
SELECT pg_temp.assert_rejected($q$INSERT INTO memory_categories(memory_id,category_id,slot_no,assignment_source,label_snapshot)
    VALUES ('40000000-0000-4000-8000-000000000001',4,4,'USER','Fourth')$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO memory_categories(memory_id,category_id,slot_no,assignment_source,label_snapshot)
    VALUES ('40000000-0000-4000-8000-000000000001',4,3,'USER','Duplicate slot')$q$,'23505');
SELECT pg_temp.assert_rejected($q$INSERT INTO memory_categories(memory_id,category_id,slot_no,assignment_source,label_snapshot)
    VALUES ('40000000-0000-4000-8000-000000000001',1,2,'USER','Duplicate category')$q$,'23505');

INSERT INTO daily_selections(user_id,service_date,cutoff_at,preference_version_id,radius_m) VALUES
    ('10000000-0000-4000-8000-000000000001','2026-09-19','2026-09-19T00:00:00Z','20000000-0000-4000-8000-000000000001',1000),
    ('10000000-0000-4000-8000-000000000001','2026-09-20','2026-09-20T00:00:00Z','20000000-0000-4000-8000-000000000001',1000);
SELECT pg_temp.assert_rejected($q$UPDATE daily_selections SET preference_version_id='20000000-0000-4000-8000-000000000002'
    WHERE user_id='10000000-0000-4000-8000-000000000001' AND service_date='2026-09-19'$q$,'23503');
SELECT pg_temp.assert_rejected($q$UPDATE daily_selections SET cutoff_at='2026-09-19T01:00:00Z'
    WHERE user_id='10000000-0000-4000-8000-000000000001' AND service_date='2026-09-19'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE daily_selections SET status='PROCESSING'
    WHERE user_id='10000000-0000-4000-8000-000000000001' AND service_date='2026-09-19'$q$,'23514');

INSERT INTO letter_deliveries(receiver_id,service_date,memory_id,delivered_at)
VALUES ('10000000-0000-4000-8000-000000000001','2026-09-19','40000000-0000-4000-8000-000000000001','2026-09-19T00:01:00Z');
SELECT pg_temp.assert_rejected($q$INSERT INTO letter_deliveries(receiver_id,service_date,memory_id,delivered_at)
    VALUES ('10000000-0000-4000-8000-000000000001','2026-09-19','40000000-0000-4000-8000-000000000002','2026-09-19T00:02:00Z')$q$,'23505');
SELECT pg_temp.assert_rejected($q$INSERT INTO letter_deliveries(receiver_id,service_date,memory_id,delivered_at)
    VALUES ('10000000-0000-4000-8000-000000000001','2026-09-20','40000000-0000-4000-8000-000000000001','2026-09-20T00:02:00Z')$q$,'23505');
SELECT pg_temp.assert_rejected($q$INSERT INTO letter_deliveries(receiver_id,service_date,memory_id,delivered_at)
    VALUES ('10000000-0000-4000-8000-000000000001','2026-09-20','40000000-0000-4000-8000-000000000002','2026-09-19T23:59:00Z')$q$,'23514');
SELECT pg_temp.assert_rejected($q$DELETE FROM memories WHERE id='40000000-0000-4000-8000-000000000001'$q$,'23503');
SELECT pg_temp.assert_rejected($q$DELETE FROM user_preference_versions WHERE id='20000000-0000-4000-8000-000000000001'$q$,'23503');

-- Repeated reports are permitted; unlike delivery constraints, these are not UNIQUE.
INSERT INTO reports(reporter_id,memory_id,reason)
SELECT '10000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001','OTHER' FROM generate_series(1,2);
SELECT pg_temp.assert_rejected($q$UPDATE reports SET status='RESOLVED' WHERE reporter_id='10000000-0000-4000-8000-000000000001'$q$,'23514');
ROLLBACK;
\echo 'PASS: ERD constraints reject invalid axes, category overflow, foreign preference ownership, duplicate delivery, invalid dates and destructive deletes.'
