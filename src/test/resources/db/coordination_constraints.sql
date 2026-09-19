-- Run against a dedicated database after Flyway V1..V7.
-- psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f src/test/resources/db/coordination_constraints.sql
-- Every fixture is synthetic and the transaction is rolled back.
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
 ('a0000000-0000-4000-8000-000000000001','ACTIVE'),
 ('a0000000-0000-4000-8000-000000000002','ACTIVE');
INSERT INTO places(id,label,lat,lng) VALUES
 ('b0000000-0000-4000-8000-000000000001','coordination fixture',37.5,127.0);
INSERT INTO memories(id,owner_id,place_id,distribution_type,origin_kind,data_origin,content,place_lat,place_lng,
 crowd_level,spatial_feel,company_fit,stay_style,crowd_source,spatial_source,company_source,stay_source,
 atmosphere_analysis_status,category_analysis_status,moderation_status,created_at)
VALUES ('c0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000001','LETTER','DIRECT','SYNTHETIC','fixture',37.5,127.0,-1,1,-1,1,'USER','USER','USER','USER','NOT_RUN','NOT_RUN','PENDING','2026-09-18T00:00:00Z');
INSERT INTO image_uploads(id,owner_id,storage_path,media_type,size_bytes,width,height,expires_at)
VALUES ('d0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000001','coordination/fixture.png','image/png',10,1,1,'2027-01-01T00:00:00Z');
INSERT INTO reports(id,reporter_id,memory_id,reason)
VALUES ('e0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002','c0000000-0000-4000-8000-000000000001','OTHER');

-- Login-attempt hash, count/window, and valid zero/positive states.
INSERT INTO login_attempt_limits(email_key_hash,failure_count,window_started_at,updated_at)
VALUES ('0000000000000000000000000000000000000000000000000000000000000000',0,NULL,'2026-09-18T00:00:00Z'),
       ('1111111111111111111111111111111111111111111111111111111111111111',2,'2026-09-18T00:00:00Z','2026-09-18T00:00:00Z');
SELECT pg_temp.assert_rejected($q$INSERT INTO login_attempt_limits(email_key_hash,failure_count,updated_at) VALUES ('BAD',0,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO login_attempt_limits(email_key_hash,failure_count,updated_at) VALUES ('2222222222222222222222222222222222222222222222222222222222222222',-1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO login_attempt_limits(email_key_hash,failure_count,window_started_at,updated_at) VALUES ('3333333333333333333333333333333333333333333333333333333333333333',0,now(),now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO login_attempt_limits(email_key_hash,failure_count,locked_until,updated_at) VALUES ('4444444444444444444444444444444444444444444444444444444444444444',0,now(),now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO login_attempt_limits(email_key_hash,failure_count,updated_at) VALUES ('5555555555555555555555555555555555555555555555555555555555555555',1,now())$q$,'23514');

-- Valid processing/completed records for all supported paths and key scopes.
INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at)
VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000001',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000001','2026-09-18T00:10:00Z',1,'2026-09-18T00:00:00Z'),
       ('a0000000-0000-4000-8000-000000000002','POST','/v1/memories','f0000000-0000-4000-8000-000000000001',1,decode(repeat('ab',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000002','2026-09-18T00:10:00Z',1,'2026-09-18T00:00:00Z'),
       ('a0000000-0000-4000-8000-000000000001','POST','/v1/images','f0000000-0000-4000-8000-000000000001',1,decode(repeat('ac',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000004','2026-09-18T00:10:00Z',1,'2026-09-18T00:00:00Z');
INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,memory_id)
VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000002',1,decode(repeat('bb',32),'hex'),'COMPLETED',1,'2026-09-18T00:00:00Z','2026-09-18T00:11:00Z','c0000000-0000-4000-8000-000000000001');
INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,image_upload_id)
VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/images','f0000000-0000-4000-8000-000000000003',1,decode(repeat('cc',32),'hex'),'COMPLETED',1,'2026-09-18T00:00:00Z','2026-09-18T00:11:00Z','d0000000-0000-4000-8000-000000000001');
INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,report_id)
VALUES ('a0000000-0000-4000-8000-000000000002','POST','/v1/reports','f0000000-0000-4000-8000-000000000004',1,decode(repeat('dd',32),'hex'),'COMPLETED',1,'2026-09-18T00:00:00Z','2026-09-18T00:11:00Z','e0000000-0000-4000-8000-000000000001');
-- Method/path/fingerprint/version/attempt and state-shape negatives.
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','GET','/v1/memories','f0000000-0000-4000-8000-000000000010',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000010',now(),1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/unknown','f0000000-0000-4000-8000-000000000011',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000011',now(),1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories/%','f0000000-0000-4000-8000-000000000012',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000012',now(),1,now())$q$,'23514');

SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000012',0,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000012',now(),1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000013',1,decode(repeat('aa',31),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000013',now(),1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000014',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000014',now(),0,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at,memory_id) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000015',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000015',now(),1,now(),'c0000000-0000-4000-8000-000000000001')$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000020',1,decode(repeat('aa',32),'hex'),'PROCESSING',NULL,now(),1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000021',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000021',NULL,1,now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE api_idempotency_records SET claim_token = 'f1000000-0000-4000-8000-000000000022' WHERE actor_id = 'a0000000-0000-4000-8000-000000000001' AND request_method = 'POST' AND request_path = '/v1/memories' AND idempotency_key = 'f0000000-0000-4000-8000-000000000002'$q$,'23514');
SELECT pg_temp.assert_rejected($q$UPDATE api_idempotency_records SET lease_expires_at = '2026-09-18T00:12:00Z' WHERE actor_id = 'a0000000-0000-4000-8000-000000000001' AND request_method = 'POST' AND request_path = '/v1/images' AND idempotency_key = 'f0000000-0000-4000-8000-000000000003'$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000016',1,decode(repeat('aa',32),'hex'),'COMPLETED',1,now(),now())$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,memory_id,image_upload_id) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000017',1,decode(repeat('aa',32),'hex'),'COMPLETED',1,now(),now(),'c0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000001')$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,image_upload_id) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000023',1,decode(repeat('aa',32),'hex'),'COMPLETED',1,now(),now(),'d0000000-0000-4000-8000-000000000001')$q$,'23514');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,attempt_count,created_at,completed_at,memory_id) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000018',1,decode(repeat('aa',32),'hex'),'COMPLETED',1,now(),now(),'c0000000-0000-4000-8000-000000000099')$q$,'23503');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000099','POST','/v1/memories','f0000000-0000-4000-8000-000000000019',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000019',now(),1,now())$q$,'23503');
SELECT pg_temp.assert_rejected($q$INSERT INTO api_idempotency_records(actor_id,request_method,request_path,idempotency_key,fingerprint_version,request_fingerprint,status,claim_token,lease_expires_at,attempt_count,created_at) VALUES ('a0000000-0000-4000-8000-000000000001','POST','/v1/memories','f0000000-0000-4000-8000-000000000001',1,decode(repeat('aa',32),'hex'),'PROCESSING','f1000000-0000-4000-8000-000000000020',now(),1,now())$q$,'23505');

-- Selection config identity, uniqueness, positive radius, and cutoff precedence.
DO $$ DECLARE p TEXT := 'coord-reg-' || txid_current()::TEXT; BEGIN
    INSERT INTO selection_config_versions(config_version,effective_at,nearby_radius_meters,rule_version)
    VALUES (p || '-1','2099-01-01T00:00:00Z',1000,'rule-v1'),
           (p || '-2','2099-01-02T00:00:00Z',2000,'rule-v2');
END $$;
SELECT pg_temp.assert_rejected($q$INSERT INTO selection_config_versions(config_version,effective_at,nearby_radius_meters,rule_version) SELECT config_version,'2099-01-03T00:00:00Z',3000,'rule-v3' FROM selection_config_versions WHERE config_version LIKE 'coord-reg-' || txid_current()::TEXT || '-1'$q$,'23505');
SELECT pg_temp.assert_rejected($q$INSERT INTO selection_config_versions(config_version,effective_at,nearby_radius_meters,rule_version) VALUES ('coord-radius-' || txid_current()::TEXT,'2099-01-03T00:00:00Z',0,'rule-v3')$q$,'23514');
DO $$ BEGIN
    IF (SELECT config_version FROM selection_config_versions WHERE config_version LIKE 'coord-reg-' || txid_current()::TEXT || '%' AND effective_at <= '2099-01-02T12:00:00Z' ORDER BY effective_at DESC, revision DESC LIMIT 1) <> 'coord-reg-' || txid_current()::TEXT || '-2' THEN
        RAISE EXCEPTION 'historical cutoff did not select latest effective configuration';
    END IF;
END $$;

-- V7 canonical definitions; IDs, codes, labels, order and version are checked too.
DO $$ DECLARE mismatches INTEGER; BEGIN
    WITH expected(id,code,label,definition,sort_order,taxonomy_version) AS (VALUES
      (1,'CAFE','카페','음료·카페 이용이 중심인 공간',1,1),(2,'RESTAURANT','음식점','식사 제공·식사 이용이 중심인 공간',2,1),(3,'BAR','술집','술을 마시는 이용이 중심인 공간',3,1),(4,'PARK_WALK','공원·산책','공원·산책로 등 걷거나 쉬는 야외 공간',4,1),(5,'CULTURE','문화','전시·공연·박물관 등 문화 경험을 위한 공간',5,1),(6,'STUDY_WORK','공부·작업 공간','공부·작업 용도가 본문에서 드러나는 공간',6,1),(7,'SHOPPING','쇼핑','상품을 둘러보거나 구매하는 매장·시장 등',7,1),(8,'OTHER','기타','장소 유형은 알 수 있으나 다른 일곱 유형에 해당하지 않는 경우',8,1))
    SELECT count(*) INTO mismatches FROM ((SELECT id,code,label,definition,sort_order,taxonomy_version FROM place_categories WHERE id BETWEEN 1 AND 8) EXCEPT (SELECT * FROM expected) UNION ALL (SELECT * FROM expected EXCEPT SELECT id,code,label,definition,sort_order,taxonomy_version FROM place_categories WHERE id BETWEEN 1 AND 8)) d;
    IF mismatches <> 0 THEN RAISE EXCEPTION 'V7 category canonical mismatch: % rows', mismatches; END IF;
END $$;

ROLLBACK;
\echo 'PASS: coordination constraints, valid states, config cutoff, and V7 canonical taxonomy verified; all fixtures rolled back.'
