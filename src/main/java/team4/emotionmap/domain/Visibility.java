package team4.emotionmap.domain;

/**
 * Memory 공개 범위. DB 에는 name() 문자열로 저장한다.
 *   LETTER  : 편지로 배달 대상이 될 수 있는 기억
 *   PRIVATE : 비공개 (작성자만)
 */
public enum Visibility {
    LETTER,
    PRIVATE
}
