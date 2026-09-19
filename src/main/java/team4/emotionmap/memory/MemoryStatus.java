package team4.emotionmap.memory;

/**
 * Memory 상태. DB 에는 name() 문자열로 저장한다.
 *   ACTIVE  : 정상 노출
 *   HIDDEN  : 신고 등으로 숨김 처리
 *   DELETED : 삭제 표시
 */
public enum MemoryStatus {
    ACTIVE,
    HIDDEN,
    DELETED
}
