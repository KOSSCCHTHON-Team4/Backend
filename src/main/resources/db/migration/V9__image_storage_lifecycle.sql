-- 만료 영수증과 완료 요청 FK는 보존하고 파일 참조만 해제한다.
-- 이 migration은 파일시스템을 변경하거나 기존 root를 자동 채택하지 않는다.
ALTER TABLE image_uploads
    ALTER COLUMN storage_path DROP NOT NULL;

UPDATE image_uploads
SET storage_path = NULL
WHERE status = 'EXPIRED';

ALTER TABLE image_uploads
    ADD CONSTRAINT ck_upload_storage_reference CHECK (
        (status = 'EXPIRED' AND storage_path IS NULL)
        OR (status IN ('STAGED', 'ATTACHED')
            AND storage_path IS NOT NULL AND btrim(storage_path) <> '')
    );

CREATE TABLE image_storage_binding (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    dataset_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    root_id UUID UNIQUE,
    bound_database_name TEXT CHECK (btrim(bound_database_name) <> ''),
    bound_database_oid BIGINT CHECK (bound_database_oid > 0),
    bound_server_address INET,
    bound_server_port INTEGER CHECK (bound_server_port BETWEEN 1 AND 65535),
    bound_schema_name TEXT CHECK (btrim(bound_schema_name) <> ''),
    bound_schema_oid BIGINT CHECK (bound_schema_oid > 0),
    CONSTRAINT ck_image_storage_binding_state CHECK (
        (root_id IS NULL
            AND bound_database_name IS NULL AND bound_database_oid IS NULL
            AND bound_server_address IS NULL AND bound_server_port IS NULL
            AND bound_schema_name IS NULL AND bound_schema_oid IS NULL)
        OR
        (root_id IS NOT NULL
            AND bound_database_name IS NOT NULL AND bound_database_oid IS NOT NULL
            AND bound_server_address IS NOT NULL AND bound_server_port IS NOT NULL
            AND bound_schema_name IS NOT NULL AND bound_schema_oid IS NOT NULL)
    )
);

-- 새 dataset 식별자만 생성한다. root 활성화는 명시적인 내부 작업으로 수행한다.
INSERT INTO image_storage_binding(singleton) VALUES (TRUE);
