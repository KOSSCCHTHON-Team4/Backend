package team4.emotionmap.contracts.media;

import java.io.InputStream;
import java.util.Optional;

/**
 * BE1 제공 · BE2 소비. 로컬 파일 primitive(SHARED_CONTRACTS §3). DB 의 {@code image_uploads}/{@code memories}
 * 행 관리는 여기 없다 — 파일과 DB 는 함께 롤백되지 않으므로 순서·정리는 호출 use case 계약(§4.4 7항)이다.
 *
 * <p>실패 분류: 저장 실패 → {@code IMAGE_STORAGE_UNAVAILABLE}(503), 읽기 IO 장애 → {@code IMAGE_FILE_UNAVAILABLE}(503),
 * 키 형식 오류/파일 없음 → empty 또는 {@code RESOURCE_NOT_FOUND}(호출자가 권한 검사 후 404 로 통일).
 */
public interface LocalImageStore {

    /** 검증된 이미지를 새 무작위 키로 저장한다. */
    StoredImageMeta store(SanitizedImage image);

    /** 키가 안전하고 파일이 있으면 메타데이터. 경로 이탈 키({@code ..}, 구분자)는 항상 empty. */
    Optional<StoredImageMeta> describe(String storageKey);

    /** 바이너리 스트림. 호출자가 닫는다. 파일이 있으나 IO 장애면 IMAGE_FILE_UNAVAILABLE. */
    InputStream open(String storageKey);

    /**
     * 좋아요 사본용 독립 복사. <b>새 무작위 키·별도 파일</b>을 만들고 원본 키를 파생하거나 참조하지 않는다.
     * 사진 없는 원문이면 호출하지 않는다.
     */
    StoredImageMeta duplicateIndependent(String sourceStorageKey);

    /**
     * 제한된 삭제. 호출자가 <b>DB 에서 어떤 행도 이 키를 참조하지 않음을 확인한 뒤</b>에만 부른다.
     * 커밋 여부가 불명한 직후 파일을 추정으로 지우지 않는다. 없는 파일은 false.
     */
    boolean deleteUnreferenced(String storageKey);
}
